package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphDifferAndImpactTest {
    @Test
    void ignoresOrderingTimestampsCommitAndSourceLineMovement() {
        ElementMetadata oldMetadata = ElementMetadata.builder()
                .sourceLocations(List.of(SourceLocation.line("A.java", 10))).targetCommit("old")
                .analyzedAt(Instant.parse("2026-01-01T00:00:00Z")).confidence(Confidence.DECLARED).build();
        ElementMetadata newMetadata = ElementMetadata.builder()
                .sourceLocations(List.of(SourceLocation.line("A.java", 20))).targetCommit("new")
                .analyzedAt(Instant.parse("2026-02-01T00:00:00Z")).confidence(Confidence.DECLARED).build();
        Node aOld = Node.builder(io.github.mandala.sbdp.model.StableId.of("java:A"), NodeType.JAVA_CLASS, "A")
                .metadata(oldMetadata).attributes(Map.of("z", 1, "a", 2)).build();
        Node aNew = Node.builder(io.github.mandala.sbdp.model.StableId.of("java:A"), NodeType.JAVA_CLASS, "A")
                .metadata(newMetadata).attributes(Map.of("a", 2, "z", 1)).build();
        Node b = Node.of("java:B", NodeType.JAVA_CLASS, "B");
        DocumentationGraph before = DocumentationGraph.of("p", "old", Instant.EPOCH, List.of(aOld, b), List.of());
        DocumentationGraph after = DocumentationGraph.of("p", "new", Instant.now(), List.of(b, aNew), List.of());

        Diff diff = differ().diff(before, after);

        assertTrue(diff.isEmpty());
    }

    @Test
    void reportsSemanticChangeAndFlowsReachedThroughReverseLookup() {
        Node flow = Node.of("flow:create", NodeType.E2E_FLOW, "Create project");
        Node endpointOld = Node.builder(io.github.mandala.sbdp.model.StableId.of("endpoint:POST:/projects"),
                NodeType.HTTP_ENDPOINT, "Create").attributes(Map.of("status", 200)).build();
        Node endpointNew = endpointOld.toBuilder().attributes(Map.of("status", 201)).build();
        Node table = Node.of("table:public.projects", NodeType.DB_TABLE, "projects");
        Edge a = Edge.of("edge:calls:1", EdgeType.CALLS_HTTP, flow.id(), endpointOld.id());
        Edge b = Edge.of("edge:creates:1", EdgeType.CREATES, endpointOld.id(), table.id());
        DocumentationGraph before = DocumentationGraph.of("p", "a", null,
                List.of(flow, endpointOld, table), List.of(a, b));
        DocumentationGraph after = DocumentationGraph.of("p", "b", null,
                List.of(flow, endpointNew, table), List.of(a, b));

        Diff diff = differ().diff(before, after);
        ImpactAnalysis impact = new ImpactAnalyzer().analyze(after, Set.of(table.id()), 4);

        assertFalse(diff.isEmpty());
        assertEquals(Set.of("attributes"), diff.modifiedNodes().getFirst().changedFields());
        assertTrue(diff.impactedNodes().contains(flow.id()));
        assertEquals(Set.of(flow.id()), impact.impactedFlows());
        assertEquals(List.of(table.id(), endpointOld.id(), flow.id()), impact.paths().get(flow.id()));
    }

    @Test
    void ignoresCaptureTimesRawTraceIdsAndSourceFingerprintsRecursively() {
        Node beforeNode = Node.builder(io.github.mandala.sbdp.model.StableId.of("span:service-create"),
                        NodeType.SPAN, "service create")
                .attributes(Map.of("traceId", "trace-a", "spanId", "span-a", "capturedAt", "2026-01-01",
                        "sourceFingerprint", "line-10", "nested", Map.of("start_time", 1, "operation", "create"),
                        "events", List.of(Map.of("name", "request", "timeUnixNano", "100")),
                        "resource", Map.of("service.instance.id", "instance-a", "process.pid", 123),
                        "links", List.of(Map.of("traceState", "vendor=a", "operation", "create"))))
                .build();
        Node afterNode = beforeNode.toBuilder().attributes(Map.of("traceId", "trace-b", "spanId", "span-b",
                "capturedAt", "2026-02-01", "sourceFingerprint", "line-200",
                "nested", Map.of("start_time", 999, "operation", "create"),
                "events", List.of(Map.of("name", "request", "timeUnixNano", "999")),
                "resource", Map.of("service.instance.id", "instance-b", "process.pid", 987),
                "links", List.of(Map.of("traceState", "vendor=b", "operation", "create")))).build();

        Diff diff = differ().diff(DocumentationGraph.of("p", "a", null, List.of(beforeNode), List.of()),
                DocumentationGraph.of("p", "b", null, List.of(afterNode), List.of()));

        assertTrue(diff.isEmpty());
    }

    @Test
    void keepsBusinessTraceIdSemanticAndIncludesDirectFlowInImpact() {
        Node beforeNode = Node.builder(io.github.mandala.sbdp.model.StableId.of("schema:request"),
                NodeType.REQUEST_SCHEMA, "Request").attributes(Map.of("traceId", "required")).build();
        Node afterNode = beforeNode.toBuilder().attributes(Map.of("traceId", "optional")).build();
        Node flow = Node.of("flow:direct", NodeType.E2E_FLOW, "Direct");

        Diff diff = differ().diff(DocumentationGraph.of("p", "a", null, List.of(beforeNode), List.of()),
                DocumentationGraph.of("p", "b", null, List.of(afterNode), List.of()));
        ImpactAnalysis impact = new ImpactAnalyzer().analyze(
                DocumentationGraph.of("p", "b", null, List.of(flow), List.of()), Set.of(flow.id()), 0);

        assertEquals(Set.of("attributes"), diff.modifiedNodes().getFirst().changedFields());
        assertEquals(Set.of(flow.id()), impact.impactedFlows());
        assertEquals(List.of(flow.id()), impact.paths().get(flow.id()));
    }

    private GraphDiffer differ() {
        return new GraphDiffer(Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC),
                new ImpactAnalyzer());
    }
}
