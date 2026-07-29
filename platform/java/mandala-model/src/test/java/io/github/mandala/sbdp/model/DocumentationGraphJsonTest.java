package io.github.mandala.sbdp.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationGraphJsonTest {
    private static final Instant ANALYZED_AT = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void roundTripsCompleteGraphUsingStringStableIdsAndIsoDates() throws Exception {
        Evidence evidence = new Evidence(null, EvidenceType.RUNTIME_OBSERVATION, EvidenceScope.TECHNICAL_FACT,
                "trace.json", "POST was observed", SourceLocation.line("traces/runtime.json", 2), "abc123",
                ANALYZED_AT, "otel", Confidence.OBSERVED, Map.of("status", 201));
        ElementMetadata metadata = ElementMetadata.builder().evidence(List.of(evidence))
                .sourceLocations(List.of(SourceLocation.line("src/ProjectController.java", 42)))
                .targetCommit("abc123").analyzedAt(ANALYZED_AT).adapter("spring")
                .confidence(Confidence.OBSERVED).reviewState(ReviewState.AGENT_REVIEWED)
                .relatedTraces(Set.of(StableId.of("trace:project-create")))
                .relatedScenarios(Set.of("project.create.success")).build();
        Node endpoint = Node.builder(StableId.of("endpoint:POST:/api/projects"), NodeType.HTTP_ENDPOINT,
                        "Create project")
                .description("Creates a project")
                .metadata(metadata)
                .attributes(Map.of("method", "POST", "responseStatuses", List.of(201, 400)))
                .build();
        Node table = Node.of("table:public.projects", NodeType.DB_TABLE, "projects");
        Edge edge = Edge.builder(StableId.of("edge:creates:123"), EdgeType.CREATES, endpoint.id(), table.id())
                .metadata(metadata).attributes(Map.of("direct", true)).build();
        DocumentationGraph graph = DocumentationGraph.of("sample", "abc123", ANALYZED_AT,
                List.of(table, endpoint), List.of(edge));

        String json = DocumentationGraphJson.toJson(graph);
        DocumentationGraph restored = DocumentationGraphJson.fromJson(json);

        assertEquals(graph, restored);
        assertTrue(json.contains("\"id\" : \"endpoint:POST:/api/projects\""));
        assertTrue(json.contains("\"analyzedAt\" : \"2026-07-22T00:00:00Z\""));
        assertEquals(endpoint.id(), restored.nodes().getFirst().id());
    }

    @Test
    void canonicalizesNodeAndAttributeOrder() throws Exception {
        Map<String, Object> firstAttributes = new LinkedHashMap<>();
        firstAttributes.put("z", 2);
        firstAttributes.put("a", Map.of("y", 1, "b", 2));
        Map<String, Object> secondAttributes = new LinkedHashMap<>();
        secondAttributes.put("a", Map.of("b", 2, "y", 1));
        secondAttributes.put("z", 2);
        Node a1 = Node.builder(StableId.of("screen:/a"), NodeType.SCREEN, "A").attributes(firstAttributes).build();
        Node a2 = Node.builder(StableId.of("screen:/a"), NodeType.SCREEN, "A").attributes(secondAttributes).build();
        Node b = Node.of("screen:/b", NodeType.SCREEN, "B");

        String first = DocumentationGraphJson.toJson(DocumentationGraph.of("p", "c", ANALYZED_AT,
                List.of(b, a1), List.of()));
        String second = DocumentationGraphJson.toJson(DocumentationGraph.of("p", "c", ANALYZED_AT,
                List.of(a2, b), List.of()));

        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class, () -> a1.attributes().put("x", 1));
        @SuppressWarnings("unchecked") Map<String, Object> nested = (Map<String, Object>) a1.attributes().get("a");
        assertThrows(UnsupportedOperationException.class, () -> nested.put("x", 1));
    }

    @Test
    void rejectsDuplicateIdsAndUnknownJsonFields() {
        Node node = Node.of("screen:/", NodeType.SCREEN, "Home");
        assertThrows(IllegalArgumentException.class, () -> DocumentationGraph.of("p", "", null,
                List.of(node, node), List.of()));
        String json = """
                {"schemaVersion":"1.0","projectId":"p","targetCommit":"","analyzedAt":null,
                 "nodes":[],"edges":[],"unexpected":true}
                """;
        assertThrows(Exception.class, () -> DocumentationGraphJson.fromJson(json));
        String futureSchema = json.replace("\"1.0\"", "\"2.0\"").replace(",\"unexpected\":true", "");
        assertThrows(Exception.class, () -> DocumentationGraphJson.fromJson(futureSchema));
        assertThrows(Exception.class, () -> DocumentationGraphJson.fromJson(
                "{\"projectId\":\"p\",\"targetCommit\":\"\",\"nodes\":[],\"edges\":[]}"));
        assertThrows(Exception.class, () -> DocumentationGraphJson.fromJson(
                "{\"schemaVersion\":\"1.0\",\"projectId\":\"p\",\"projectId\":\"q\","
                        + "\"targetCommit\":\"\",\"nodes\":[],\"edges\":[]}"));
    }

    @Test
    void evidenceIdDoesNotChangeWhenOnlyItsSourceLineMoves() {
        Evidence before = new Evidence(null, EvidenceType.SOURCE_CODE, EvidenceScope.TECHNICAL_FACT,
                "A.java", "method", SourceLocation.line("src/A.java", 10), "", null, "java",
                Confidence.INFERRED, Map.of());
        Evidence after = new Evidence(null, EvidenceType.SOURCE_CODE, EvidenceScope.TECHNICAL_FACT,
                "A.java", "method", SourceLocation.line("src/A.java", 200), "", null, "java",
                Confidence.INFERRED, Map.of());

        assertEquals(before.id(), after.id());
    }

    @Test
    void roundTripsConflictsAndDiffsWithoutSerializingComputedBooleanAccessors() throws Exception {
        Conflict conflict = new Conflict(StableId.of("conflict:endpoint"), ConflictType.SOURCE_DISAGREEMENT,
                StableId.of("endpoint:GET:/projects"), "path", "Sources disagree", List.of(), ANALYZED_AT,
                ConflictStatus.OPEN, "");
        ElementMetadata metadata = ElementMetadata.builder().conflicts(List.of(conflict)).build();
        Node node = Node.builder(StableId.of("endpoint:GET:/projects"), NodeType.HTTP_ENDPOINT, "projects")
                .metadata(metadata).build();
        DocumentationGraph graph = DocumentationGraph.of("p", "c", ANALYZED_AT, List.of(node), List.of());
        String graphJson = DocumentationGraphJson.toJson(graph);

        assertEquals(graph, DocumentationGraphJson.fromJson(graphJson));
        assertFalse(graphJson.contains("\"open\""));

        Diff diff = new Diff("a", "b", ANALYZED_AT, List.of(node), List.of(), List.of(), List.of(),
                List.of(), List.of(), Set.of(node.id()));
        String diffJson = DocumentationGraphJson.mapper().writeValueAsString(diff);
        assertEquals(diff, DocumentationGraphJson.mapper().readValue(diffJson, Diff.class));
        assertFalse(diffJson.contains("\"empty\""));
    }

    @Test
    void evidenceCannotPromoteInferenceToObservedAndRuntimeCaptureIdsAreStable() {
        assertThrows(IllegalArgumentException.class, () -> new Evidence(null, EvidenceType.AGENT_INFERENCE,
                EvidenceScope.TECHNICAL_FACT, "agent", "guess", null, "", null, "agent",
                Confidence.OBSERVED, Map.of()));
        Evidence first = new Evidence(null, EvidenceType.RUNTIME_OBSERVATION, EvidenceScope.TECHNICAL_FACT,
                "otel", "request", null, "c", ANALYZED_AT, "otel", Confidence.OBSERVED,
                Map.of("traceId", "a", "nested", Map.of("start_time", 1, "operation", "create"),
                        "events", List.of(Map.of("timeUnixNano", "100", "name", "checkpoint")),
                        "resource", Map.of("service.instance.id", "instance-a", "process.pid", 123)));
        Evidence second = new Evidence(null, EvidenceType.RUNTIME_OBSERVATION, EvidenceScope.TECHNICAL_FACT,
                "otel", "request", null, "c", ANALYZED_AT.plusSeconds(30), "otel", Confidence.OBSERVED,
                Map.of("traceId", "b", "nested", Map.of("start_time", 999, "operation", "create"),
                        "events", List.of(Map.of("timeUnixNano", "999", "name", "checkpoint")),
                        "resource", Map.of("service.instance.id", "instance-b", "process.pid", 987)));

        assertEquals(first.id(), second.id());
    }

    @Test
    void canonicalizesEveryAcceptedNumericTypeForLosslessJsonRoundTrip() throws Exception {
        Node node = Node.builder(StableId.of("java:Numbers"), NodeType.JAVA_CLASS, "Numbers")
                .attributes(Map.of(
                        "byte", (byte) 1,
                        "short", (short) 2,
                        "integer", 3,
                        "long", 4L,
                        "bigInteger", BigInteger.valueOf(5),
                        "float", 1.5f,
                        "wholeFloat", 1.0f,
                        "double", 2.25d,
                        "wholeDecimal", new BigDecimal("1.000"),
                        "decimal", new BigDecimal("0.123456789012345678901234567890")))
                .build();
        DocumentationGraph graph = DocumentationGraph.of("p", "", null, List.of(node), List.of());

        DocumentationGraph restored = DocumentationGraphJson.fromJson(DocumentationGraphJson.toJson(graph));

        assertEquals(graph, restored);
        assertTrue(node.attributes().get("integer") instanceof Long);
        assertTrue(node.attributes().get("decimal") instanceof BigDecimal);
        assertThrows(IllegalArgumentException.class, () -> Node.builder(StableId.of("java:NaN"),
                NodeType.JAVA_CLASS, "NaN").attributes(Map.of("value", Double.NaN)).build());
        assertThrows(IllegalArgumentException.class, () -> Node.builder(StableId.of("java:Huge"),
                NodeType.JAVA_CLASS, "Huge").attributes(Map.of("value", BigInteger.ONE.shiftLeft(80))).build());
        Map<Object, Object> invalidKeys = new LinkedHashMap<>();
        invalidKeys.put(1, "number");
        invalidKeys.put("1", "string");
        assertThrows(IllegalArgumentException.class, () -> Node.builder(StableId.of("java:Keys"),
                NodeType.JAVA_CLASS, "Keys").attributes(Map.of("nested", invalidKeys)).build());
    }
}
