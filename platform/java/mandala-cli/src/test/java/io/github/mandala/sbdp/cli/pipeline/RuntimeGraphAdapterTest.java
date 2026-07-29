package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.cli.RepositoryContext;
import io.github.mandala.sbdp.cli.config.MandalaConfig;
import io.github.mandala.sbdp.core.GraphDiffer;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.opentelemetry.RuntimeEvent;
import io.github.mandala.sbdp.opentelemetry.RuntimeLink;
import io.github.mandala.sbdp.opentelemetry.RuntimeSpan;
import io.github.mandala.sbdp.opentelemetry.RuntimeSpanKind;
import io.github.mandala.sbdp.opentelemetry.RuntimeStatus;
import io.github.mandala.sbdp.opentelemetry.RuntimeTrace;
import io.github.mandala.sbdp.opentelemetry.SpanBoundary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeGraphAdapterTest {
    private static final Instant ANALYZED_AT = Instant.parse("2026-07-22T00:00:00Z");

    @TempDir
    Path root;

    @Test
    void repeatedSameNameSpansIgnoreExporterOrderRawIdsAndVolatileRuntimeValues() throws Exception {
        RuntimeGraphAdapter adapter = adapter();
        RefreshContext context = context();
        Path source = root.resolve("mandala/traces/runtime.json");

        DocumentationGraph first = adapter.mapTraces(List.of(firstTrace()), List.of(), source, context);
        DocumentationGraph second = adapter.mapTraces(List.of(secondTrace()), List.of(), source, context);
        DocumentationGraph roundTripped = DocumentationGraphJson.fromJson(DocumentationGraphJson.toJson(second));

        assertEquals(first.nodes().stream().map(node -> node.id()).toList(),
                second.nodes().stream().map(node -> node.id()).toList());
        assertEquals(operationIds(first), operationIds(second),
                "SELECT and UPDATE observations must not swap ids when raw export order changes");
        assertTrue(new GraphDiffer().diff(first, second).isEmpty(),
                "raw runtime ids, timing and concrete URL paths must not create semantic changes");
        assertTrue(new GraphDiffer().diff(first, roundTripped).isEmpty(),
                "raw ids, start/end/duration, event time and service instance values are non-semantic");
    }

    @Test
    void identicalSpanShapesFromDifferentFlowsRemainDistinctTraces() {
        RuntimeGraphAdapter adapter = adapter();
        RuntimeTrace forbidden = flowTrace("trace-forbidden", "forbidden", 403);
        RuntimeTrace notFound = flowTrace("trace-not-found", "not.found", 404);

        DocumentationGraph graph = adapter.mapTraces(List.of(forbidden, notFound), List.of(),
                root.resolve("mandala/traces/runtime.json"), context());

        assertEquals(2, graph.nodes().stream().filter(node -> node.type() == NodeType.TRACE).count());
        assertEquals(Set.of("forbidden", "not.found"), graph.nodes().stream()
                .filter(node -> node.type() == NodeType.TRACE)
                .flatMap(node -> node.metadata().relatedScenarios().stream())
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void graphBoundaryDropsMachineAndProcessMetadataAndLocalAbsolutePaths() throws Exception {
        DocumentationGraph graph = adapter().mapTraces(List.of(firstTrace()), List.of(),
                root.resolve("mandala/traces/runtime.json"), context());
        String json = DocumentationGraphJson.toJson(graph);

        assertFalse(json.contains("host.name"));
        assertFalse(json.contains("developer-mac"));
        assertFalse(json.contains("process.command_args"));
        assertFalse(json.contains("service.instance.id"));
        assertFalse(json.contains("http.request.header.authorization"));
        assertFalse(json.contains("Bearer local-secret"));
        assertFalse(json.contains("/Users/example"));
        assertFalse(json.contains("local status detail"));
        assertTrue(json.contains("sample-backend"), "stable service identity remains available");
        assertTrue(json.contains("[LOCAL_PATH_REDACTED]"), "allowlisted text values redact local paths");
    }

    @Test
    void configuredMaskKeysAreAppliedRecursivelyDuringOtlpImport() throws Exception {
        Path traceFile = root.resolve("mandala/traces/custom.json");
        Files.createDirectories(traceFile.getParent());
        Files.writeString(traceFile, """
                {
                  "resourceSpans": [{
                    "resource": {"attributes": [
                      {"key": "service.name", "value": {"stringValue": "sample-backend"}}
                    ]},
                    "scopeSpans": [{"scope": {"name": "manual"}, "spans": [{
                      "traceId": "0123456789abcdef0123456789abcdef",
                      "spanId": "0123456789abcdef",
                      "name": "GET /api/projects",
                      "kind": "SPAN_KIND_SERVER",
                      "startTimeUnixNano": "1000000000",
                      "endTimeUnixNano": "1000000001",
                      "attributes": [
                        {"key": "http.route", "value": {"stringValue": "/api/projects"}},
                        {"key": "http.request.method", "value": {"stringValue": "GET"}},
                        {"key": "mandala.internal-reference", "value": {"stringValue": "CUSTOM-SECRET-42"}}
                      ]
                    }]}]
                  }]
                }
                """);
        MandalaConfig config = new MandalaConfig();
        config.mandala.project.id = "runtime-test";
        config.mandala.telemetry.traces = List.of("mandala/traces/*.json");
        config.mandala.security.maskKeys = List.of("internal-reference");
        RuntimeGraphAdapter adapter = new RuntimeGraphAdapter(new RepositoryContext(root,
                root.resolve("mandala.yml"), config, "commit-a", ANALYZED_AT));

        DocumentationGraph graph = adapter.analyze(context());

        Object value = graph.nodes().stream().filter(node -> node.type() == NodeType.SPAN)
                .findFirst().orElseThrow().attributes().get("mandala.internal-reference");
        assertEquals("[REDACTED]", value);
        assertFalse(DocumentationGraphJson.toJson(graph).contains("CUSTOM-SECRET-42"));
    }

    @Test
    void importsAndMergesEveryDocumentInACollectorJsonLinesFile() throws Exception {
        Path traceFile = root.resolve("mandala/traces/collector.json");
        Files.createDirectories(traceFile.getParent());
        String traceId = "0123456789abcdef0123456789abcdef";
        Files.writeString(traceFile, String.join("\n",
                otlpDocument(traceId, "0000000000000001", "", "POST /api/projects",
                        attributes(
                                attribute("http.route", "/api/projects"),
                                attribute("http.request.method", "POST"),
                                attribute("mandala.flow.id", "project.create.success"))),
                otlpDocument(traceId, "0000000000000002", "0000000000000001", "ProjectService.create",
                        attributes(attribute("mandala.layer", "application_service"))),
                otlpDocument(traceId, "0000000000000003", "0000000000000002", "INSERT projects",
                        attributes(
                                attribute("db.system.name", "postgresql"),
                                attribute("db.operation.name", "INSERT")))));
        MandalaConfig config = new MandalaConfig();
        config.mandala.project.id = "runtime-test";
        config.mandala.telemetry.traces = List.of("mandala/traces/*.json");
        RuntimeGraphAdapter adapter = new RuntimeGraphAdapter(new RepositoryContext(root,
                root.resolve("mandala.yml"), config, "commit-a", ANALYZED_AT));

        DocumentationGraph graph = adapter.analyze(context());

        assertEquals(1, graph.nodes().stream().filter(node -> node.type() == NodeType.TRACE).count());
        assertEquals(3, graph.nodes().stream().filter(node -> node.type() == NodeType.SPAN).count());
        assertEquals(2, graph.edges().stream().filter(edge -> edge.type() == EdgeType.CALLS).count(),
                "spans split across JSON lines must retain their parent-child path");
    }

    private String otlpDocument(String traceId, String spanId, String parentSpanId, String name,
                                String attributes) {
        String parent = parentSpanId.isBlank() ? "" : ",\"parentSpanId\":\"" + parentSpanId + "\"";
        return "{\"resourceSpans\":[{\"resource\":{\"attributes\":[]},\"scopeSpans\":[{"
                + "\"scope\":{\"name\":\"test\"},\"spans\":[{\"traceId\":\"" + traceId
                + "\",\"spanId\":\"" + spanId + "\"" + parent + ",\"name\":\"" + name
                + "\",\"kind\":\"SPAN_KIND_SERVER\",\"startTimeUnixNano\":\"1000000000\","
                + "\"endTimeUnixNano\":\"1000000001\",\"attributes\":[" + attributes
                + "]}]}]}]}";
    }

    private String attributes(String... values) {
        return String.join(",", values);
    }

    private String attribute(String key, String value) {
        return "{\"key\":\"" + key + "\",\"value\":{\"stringValue\":\"" + value + "\"}}";
    }

    private RuntimeGraphAdapter adapter() {
        MandalaConfig config = new MandalaConfig();
        config.mandala.project.id = "runtime-test";
        return new RuntimeGraphAdapter(new RepositoryContext(root, root.resolve("mandala.yml"), config,
                "commit-a", ANALYZED_AT));
    }

    private RefreshContext context() {
        return new RefreshContext("runtime-test", "commit-a", "config-a", root, ANALYZED_AT, Map.of());
    }

    private RuntimeTrace firstTrace() {
        RuntimeSpan rootSpan = span("trace-a", "root-a", "", "HTTP GET", SpanBoundary.HTTP_SERVER,
                Instant.parse("2026-07-22T01:00:00Z"), Map.of(
                        "http.route", "/api/projects/{id}",
                        "url.path", "/api/projects/7",
                        "http.request.method", "GET",
                        "http.request.header.authorization", "Bearer local-secret",
                        "mandala.flow.id", "flow:project.detail"));
        RuntimeSpan select = span("trace-a", "select-a", "root-a", "database query", SpanBoundary.JDBC,
                Instant.parse("2026-07-22T01:00:01Z"), Map.of(
                        "db.system.name", "postgresql", "db.operation.name", "SELECT",
                        "db.query.text", "select * from projects where id = ?"));
        RuntimeSpan update = span("trace-a", "update-a", "root-a", "database query", SpanBoundary.JDBC,
                Instant.parse("2026-07-22T01:00:02Z"), Map.of(
                        "db.system.name", "postgresql", "db.operation.name", "UPDATE",
                        "db.query.text", "update projects set name = ? where id = ?"));
        return new RuntimeTrace("trace-a", List.of(rootSpan, select, update));
    }

    private RuntimeTrace secondTrace() {
        RuntimeSpan update = span("trace-b", "update-b", "root-b", "database query", SpanBoundary.JDBC,
                Instant.parse("2026-07-23T00:00:00Z"), Map.of(
                        "db.system.name", "postgresql", "db.operation.name", "UPDATE",
                        "db.query.text", "update projects set name = ? where id = ?"));
        RuntimeSpan rootSpan = span("trace-b", "root-b", "", "HTTP GET", SpanBoundary.HTTP_SERVER,
                Instant.parse("2026-07-23T00:00:03Z"), Map.of(
                        "http.route", "/api/projects/{id}",
                        "url.path", "/api/projects/42",
                        "http.request.method", "GET",
                        "http.request.header.authorization", "Bearer another-secret",
                        "mandala.flow.id", "flow:project.detail"));
        RuntimeSpan select = span("trace-b", "select-b", "root-b", "database query", SpanBoundary.JDBC,
                Instant.parse("2026-07-23T00:00:02Z"), Map.of(
                        "db.system.name", "postgresql", "db.operation.name", "SELECT",
                        "db.query.text", "select * from projects where id = ?"));
        return new RuntimeTrace("trace-b", List.of(update, rootSpan, select));
    }

    private RuntimeTrace flowTrace(String traceId, String flowId, int status) {
        RuntimeSpan rootSpan = span(traceId, traceId + "-root", "", "GET /api/projects/{id}",
                SpanBoundary.HTTP_SERVER, ANALYZED_AT, Map.of(
                        "http.route", "/api/projects/{id}",
                        "url.path", "/api/projects/1",
                        "http.request.method", "GET",
                        "http.response.status_code", status,
                        "mandala.endpoint.id", "endpoint:GET:/api/projects/{id}",
                        "mandala.flow.id", flowId));
        RuntimeSpan dao = span(traceId, traceId + "-dao", traceId + "-root", "ProjectDao.selectById",
                SpanBoundary.DOMA_DAO, ANALYZED_AT.plusMillis(1), Map.of(
                        "code.namespace", "example.ProjectDao",
                        "code.function", "selectById"));
        return new RuntimeTrace(traceId, List.of(rootSpan, dao));
    }

    private RuntimeSpan span(String traceId, String spanId, String parentId, String name,
                             SpanBoundary boundary, Instant start, Map<String, Object> attributes) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("service.name", "sample-backend");
        resource.put("service.instance.id", "instance-" + traceId);
        resource.put("host.name", "developer-mac");
        resource.put("process.pid", traceId.equals("trace-a") ? 1234 : 9876);
        resource.put("process.command_args", List.of("java", "-jar", "/Users/example/sample.jar"));
        return new RuntimeSpan(traceId, spanId, parentId, name,
                boundary == SpanBoundary.HTTP_SERVER ? RuntimeSpanKind.SERVER : RuntimeSpanKind.CLIENT,
                boundary, start, start.plusMillis(traceId.equals("trace-a") ? 5 : 900),
                new RuntimeStatus(RuntimeStatus.Code.OK,
                        "local status detail at /Users/example/work/sample.java"),
                attributes, resource, "instrumentation /Users/example/plugin.jar", "1.0.0",
                List.of(new RuntimeEvent("checkpoint", start.plusMillis(1), Map.of(
                        "exception.type", "ExampleException", "exception.message", "/Users/example/private"))),
                List.of(new RuntimeLink("linked-" + traceId, "linked-" + spanId, "volatile-state",
                        Map.of("rpc.service", "project-api", "process.command_args", "/Users/example/run"))));
    }

    private Map<String, String> operationIds(DocumentationGraph graph) {
        Map<String, String> ids = new LinkedHashMap<>();
        graph.nodes().stream().filter(node -> node.type() == NodeType.SPAN)
                .filter(node -> node.attributes().containsKey("db.operation.name"))
                .forEach(node -> ids.put(String.valueOf(node.attributes().get("db.operation.name")),
                        node.id().value()));
        return ids;
    }
}
