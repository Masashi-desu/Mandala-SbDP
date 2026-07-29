package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceScope;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.model.ReviewState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphMergerTest {
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void runtimeFactWinsWhileAllEvidenceAndConflictAreRetained() {
        StableId id = StableId.of("endpoint:POST:/api/projects");
        Node declared = node(id, "Create project", "Declared description", EvidenceType.SPRING_MAPPING,
                Confidence.DECLARED, Map.of("status", 200, "method", "POST"));
        Node observed = node(id, "Create project", "Observed description", EvidenceType.RUNTIME_OBSERVATION,
                Confidence.OBSERVED, Map.of("status", 201));

        MergeResult result = new GraphMerger().merge(List.of(
                DocumentationGraph.of("p", "c", NOW, List.of(declared), List.of()),
                DocumentationGraph.of("p", "c", NOW, List.of(observed), List.of())));
        Node merged = result.graph().node(id).orElseThrow();

        assertEquals(201L, merged.attributes().get("status"));
        assertEquals("POST", merged.attributes().get("method"));
        assertEquals("Observed description", merged.description());
        assertEquals(2, merged.metadata().evidence().size());
        assertEquals(Confidence.CONFLICTED, merged.metadata().confidence());
        assertTrue(merged.metadata().conflicted());
        assertTrue(result.conflicts().stream().anyMatch(conflict -> conflict.field().equals("attributes.status")));
    }

    @Test
    void identicalFragmentsDoNotCreateConflictsOrDuplicates() {
        StableId id = StableId.of("table:public.projects");
        Node node = node(id, "projects", "Project table", EvidenceType.DATABASE_INTROSPECTION,
                Confidence.DECLARED, Map.of("schema", "public"));
        MergeResult result = new GraphMerger().merge(List.of(
                DocumentationGraph.of("p", "c", NOW, List.of(node), List.of()),
                DocumentationGraph.of("p", "c", NOW, List.of(node), List.of())));

        assertEquals(1, result.graph().nodes().size());
        assertTrue(result.conflicts().isEmpty());
        assertFalse(result.graph().nodes().getFirst().metadata().conflicted());
    }

    @Test
    void producesIdenticalGraphAndConflictIdsRegardlessOfFragmentOrder() throws Exception {
        StableId id = StableId.of("endpoint:POST:/api/projects");
        Node first = node(id, "Create A", "Alpha", EvidenceType.SOURCE_CODE, Confidence.INFERRED,
                Map.of("status", 200));
        Node second = node(id, "Create B", "Beta", EvidenceType.SQL_STATIC_ANALYSIS, Confidence.INFERRED,
                Map.of("status", 201));
        DocumentationGraph a = DocumentationGraph.of("p", "c", NOW, List.of(first), List.of());
        DocumentationGraph b = DocumentationGraph.of("p", "c", NOW, List.of(second), List.of());

        MergeResult forward = new GraphMerger().merge(List.of(a, b));
        MergeResult reverse = new GraphMerger().merge(List.of(b, a));

        assertEquals(DocumentationGraphJson.toJson(forward.graph()), DocumentationGraphJson.toJson(reverse.graph()));
        assertEquals(forward.conflicts().stream().map(conflict -> conflict.id()).toList(),
                reverse.conflicts().stream().map(conflict -> conflict.id()).toList());
    }

    @Test
    void selectsEachAttributeFromItsActualProviderWithoutAuthorityLaundering() throws Exception {
        StableId id = StableId.of("endpoint:POST:/api/projects");
        Node runtime = node(id, "Create", "Runtime", EvidenceType.RUNTIME_OBSERVATION, Confidence.OBSERVED,
                Map.of("runtimeOnly", "A"));
        Node sourceB = node(id, "Create", "Source", EvidenceType.SOURCE_CODE, Confidence.INFERRED,
                Map.of("shared", "B"));
        Node sourceC = node(id, "Create", "Source", EvidenceType.SOURCE_CODE, Confidence.INFERRED,
                Map.of("shared", "C"));
        DocumentationGraph a = DocumentationGraph.of("p", "c", NOW, List.of(runtime), List.of());
        DocumentationGraph b = DocumentationGraph.of("p", "c", NOW, List.of(sourceB), List.of());
        DocumentationGraph c = DocumentationGraph.of("p", "c", NOW, List.of(sourceC), List.of());

        String first = DocumentationGraphJson.toJson(new GraphMerger().merge(List.of(a, b, c)).graph());
        String second = DocumentationGraphJson.toJson(new GraphMerger().merge(List.of(b, c, a)).graph());

        assertEquals(first, second);
        assertEquals("C", new GraphMerger().merge(List.of(a, b, c)).graph().nodes().getFirst()
                .attributes().get("shared"));
    }

    @Test
    void keepsHumanIntentSeparateFromTechnicalProseWithoutFalseConflict() {
        StableId id = StableId.of("java:example.ProjectService#create");
        Node technical = Node.builder(id, NodeType.JAVA_METHOD, "ProjectService#create")
                .description("Persists a project")
                .metadata(ElementMetadata.builder().evidence(List.of(Evidence.of(EvidenceType.JAVADOC,
                        "ProjectService.java", "Persists a project"))).build()).build();
        Node intent = Node.builder(id, NodeType.JAVA_METHOD, "Create a project")
                .description("Starts a customer delivery engagement")
                .metadata(ElementMetadata.builder().evidence(List.of(Evidence.humanReviewed("review.md",
                        "Starts a customer delivery engagement"))).build()).build();

        MergeResult result = new GraphMerger().merge(List.of(
                DocumentationGraph.of("p", "c", NOW, List.of(technical), List.of()),
                DocumentationGraph.of("p", "c", NOW, List.of(intent), List.of())));

        assertTrue(result.conflicts().isEmpty());
        assertEquals("Starts a customer delivery engagement", result.graph().nodes().getFirst().description());
    }

    @Test
    void surfacesApprovedAndRejectedReviewStatesAsNeedsReviewConflict() {
        StableId id = StableId.of("java:A");
        Evidence evidence = Evidence.humanReviewed("review.md", "review");
        Node approved = Node.builder(id, NodeType.JAVA_CLASS, "A").metadata(ElementMetadata.builder()
                .evidence(List.of(evidence)).reviewState(ReviewState.APPROVED).build()).build();
        Node rejected = Node.builder(id, NodeType.JAVA_CLASS, "A").metadata(ElementMetadata.builder()
                .evidence(List.of(evidence)).reviewState(ReviewState.REJECTED).build()).build();

        MergeResult result = new GraphMerger().merge(List.of(
                DocumentationGraph.of("p", "c", NOW, List.of(approved), List.of()),
                DocumentationGraph.of("p", "c", NOW, List.of(rejected), List.of())));

        assertEquals(ReviewState.NEEDS_REVIEW, result.graph().nodes().getFirst().metadata().reviewState());
        assertTrue(result.graph().nodes().getFirst().metadata().conflicted());
    }

    private Node node(StableId id, String name, String description, EvidenceType type,
                      Confidence confidence, Map<String, Object> attributes) {
        Evidence evidence = new Evidence(null, type, EvidenceScope.TECHNICAL_FACT, type.name(), description,
                null, "c", NOW, type.name().toLowerCase(), confidence, Map.of());
        ElementMetadata metadata = ElementMetadata.builder().evidence(List.of(evidence)).confidence(confidence)
                .analyzedAt(NOW).adapter(type.name().toLowerCase()).build();
        return Node.builder(id, NodeType.HTTP_ENDPOINT, name).description(description)
                .metadata(metadata).attributes(attributes).build();
    }
}
