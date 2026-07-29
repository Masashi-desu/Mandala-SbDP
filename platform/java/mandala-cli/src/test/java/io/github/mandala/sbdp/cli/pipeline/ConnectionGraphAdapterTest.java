package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.cli.RepositoryContext;
import io.github.mandala.sbdp.cli.config.MandalaConfig;
import io.github.mandala.sbdp.core.GraphMerger;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionGraphAdapterTest {
    @TempDir
    Path root;

    @Test
    void leavesSameArityOverloadAmbiguousAndResolvesUniqueArityWithoutFindFirst() throws Exception {
        Path sourceRoot = root.resolve("src/main/java/example");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("Caller.java"), """
                package example;

                record Payload(String value) {}

                class Caller {
                    private final Target target = null;

                    void run() {
                        target.handle(null);
                        target.safe("value");
                    }

                    void run(String ignored) {}
                }

                class Target {
                    void handle(String value) {}
                    void handle(Long value) {}
                    void safe(String value) {}
                    void safe(String value, int flags) {}
                }
                """, StandardCharsets.UTF_8);

        List<Node> known = List.of(
                method("java:example.Caller#run()", "example.Caller", "run"),
                method("java:example.Caller#run(String)", "example.Caller", "run"),
                method("java:example.Target#handle(String)", "example.Target", "handle"),
                method("java:example.Target#handle(Long)", "example.Target", "handle"),
                method("java:example.Target#safe(String)", "example.Target", "safe"),
                method("java:example.Target#safe(String,int)", "example.Target", "safe"),
                Node.builder(StableId.of("client:task-create"), NodeType.HTTP_CLIENT_CALL, "create task")
                        .attributes(Map.of("method", "POST", "path", "/api/projects/{id}/tasks")).build(),
                Node.builder(StableId.of("endpoint:POST:/api/projects/{projectId}/tasks"),
                                NodeType.HTTP_ENDPOINT, "POST task")
                        .attributes(Map.of("method", "POST", "path", "/api/projects/{projectId}/tasks"))
                        .build());
        Path fragments = root.resolve("mandala/cache/fragments");
        Files.createDirectories(fragments);
        DocumentationGraphJson.write(fragments.resolve("source.json"), DocumentationGraph.of(
                "connection-test", "commit-a", Instant.EPOCH, known, List.of()));

        MandalaConfig config = new MandalaConfig();
        config.mandala.project.id = "connection-test";
        config.mandala.source.java.roots = List.of("src/main/java");
        RepositoryContext repository = new RepositoryContext(root, root.resolve("mandala.yml"), config,
                "commit-a", Instant.EPOCH);
        DocumentationGraph result = new ConnectionGraphAdapter(repository).analyze(new RefreshContext(
                "connection-test", "commit-a", "config-a", root, Instant.EPOCH, Map.of()));

        assertTrue(result.edges().stream().anyMatch(edge -> edge.type() == EdgeType.CALLS
                && edge.from().equals(StableId.of("java:example.Caller#run()"))
                && edge.to().equals(StableId.of("java:example.Target#safe(String)"))));
        assertTrue(result.edges().stream().anyMatch(edge -> edge.type() == EdgeType.MATCHES_OPERATION
                && edge.from().equals(StableId.of("client:task-create"))
                && edge.to().equals(StableId.of("endpoint:POST:/api/projects/{projectId}/tasks"))),
                "template variable names are not part of HTTP operation identity");
        assertFalse(result.edges().stream().anyMatch(edge -> edge.type() == EdgeType.CALLS
                && edge.to().value().contains("#handle(")),
                "same-arity overloads must not be connected by collection order");
        assertEquals(List.of(StableId.of("java:example.Caller#run()")),
                result.nodes().stream().map(Node::id).toList(),
                "the warning must be attached to the exact overloaded caller declaration");
        assertTrue(result.nodes().getFirst().metadata().warnings().stream()
                .anyMatch(warning -> warning.contains("Skipped ambiguous method call")
                        && warning.contains("handle(String)") && warning.contains("handle(Long)")));
    }

    @Test
    void promotesMatchedRuntimeSqlCrudEdgesToObservedWithTraceScenarioAndDirectEvidence() throws Exception {
        Instant time = Instant.parse("2026-07-22T00:00:00Z");
        ElementMetadata declared = GraphSupport.metadata(EvidenceType.SQL_STATIC_ANALYSIS,
                "src/sql", "Parsed SQL", "source", "commit-a", time, List.of(), List.of(),
                SourceLocation.of("src/sql"));
        ElementMetadata observed = GraphSupport.metadata(EvidenceType.RUNTIME_OBSERVATION,
                "mandala/traces/runtime.json", "Imported trace", "opentelemetry", "commit-a", time,
                List.of(), List.of("project.create.success"), SourceLocation.of("mandala/traces/runtime.json"));

        Node projectsSql = sql("sql:project-insert",
                "insert into projects (name) values (?)", "projects", declared);
        Node auditSql = sql("sql:audit-insert",
                "insert into audit_logs (action) values (?)", "audit_logs", declared);
        Node projects = table("table:public.projects", "projects", declared);
        Node auditLogs = table("table:public.audit_logs", "audit_logs", declared);
        Edge createsProjects = GraphSupport.edge(EdgeType.CREATES, projectsSql.id(), projects.id(), declared);
        Edge createsAudit = GraphSupport.edge(EdgeType.CREATES, auditSql.id(), auditLogs.id(), declared);
        DocumentationGraph source = DocumentationGraph.of("connection-test", "commit-a", time,
                List.of(projectsSql, auditSql, projects, auditLogs), List.of(createsProjects, createsAudit));

        Node trace = Node.builder(StableId.of("trace:project-create"), NodeType.TRACE, "project create")
                .metadata(observed).attributes(Map.of("flowId", "project.create.success")).build();
        Node projectSpan = Node.builder(StableId.of("span:project-insert"), NodeType.SPAN, "project insert")
                .metadata(observed).attributes(Map.of("db.query.text",
                        "insert into projects ( name ) values ( ? );")).build();
        Node auditSpan = Node.builder(StableId.of("span:audit-insert"), NodeType.SPAN, "audit insert")
                .metadata(observed).attributes(Map.of("db.query.text",
                        "insert into audit_logs ( action ) values ( ? );")).build();
        DocumentationGraph runtime = DocumentationGraph.of("connection-test", "commit-a", time,
                List.of(trace, projectSpan, auditSpan), List.of(
                        GraphSupport.edge(EdgeType.CONTAINS, trace.id(), projectSpan.id(), observed),
                        GraphSupport.edge(EdgeType.CONTAINS, trace.id(), auditSpan.id(), observed)));

        Path fragments = root.resolve("mandala/cache/fragments");
        Files.createDirectories(fragments);
        DocumentationGraphJson.write(fragments.resolve("source.json"), source);
        DocumentationGraphJson.write(fragments.resolve("opentelemetry.json"), runtime);
        MandalaConfig config = new MandalaConfig(); config.mandala.project.id = "connection-test";
        RepositoryContext repository = new RepositoryContext(root, root.resolve("mandala.yml"), config,
                "commit-a", time);
        RefreshContext context = new RefreshContext("connection-test", "commit-a", "config-a", root,
                time, Map.of());

        DocumentationGraph connections = new ConnectionGraphAdapter(repository).analyze(context);
        DocumentationGraph merged = new GraphMerger().merge(List.of(source, runtime, connections)).graph();

        for (StableId edgeId : List.of(createsProjects.id(), createsAudit.id())) {
            Edge edge = merged.edge(edgeId).orElseThrow();
            assertEquals(Confidence.OBSERVED, edge.metadata().confidence());
            assertTrue(edge.metadata().evidence().stream()
                    .anyMatch(evidence -> evidence.type() == EvidenceType.RUNTIME_OBSERVATION));
            assertEquals(java.util.Set.of(trace.id()), edge.metadata().relatedTraces());
            assertEquals(java.util.Set.of("project.create.success"), edge.metadata().relatedScenarios());
            assertEquals(true, edge.attributes().get("observed"));
            assertEquals(true, edge.attributes().get("direct"));
        }
    }

    private Node sql(String id, String normalizedSql, String table, ElementMetadata metadata) {
        return Node.builder(StableId.of(id), NodeType.SQL_STATEMENT, id)
                .metadata(metadata).attributes(Map.of("normalizedSql", normalizedSql,
                        "tables", List.of(Map.of("schema", "", "table", table, "directTarget", true))))
                .build();
    }

    private Node table(String id, String table, ElementMetadata metadata) {
        return Node.builder(StableId.of(id), NodeType.DB_TABLE, table).metadata(metadata)
                .attributes(Map.of("schema", "public", "table", table)).build();
    }

    private Node method(String id, String owner, String member) {
        return Node.builder(StableId.of(id), NodeType.APPLICATION_SERVICE, owner + "#" + member)
                .attributes(Map.of("qualifiedName", owner, "memberName", member)).build();
    }
}
