package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomDocumentationReconcilerTest {
    @Test
    void detectsMissingReferencesAndAssertionsThatContradictGeneratedFacts() {
        Node endpoint = Node.builder(StableId.of("endpoint:POST:/projects"), NodeType.HTTP_ENDPOINT, "Create")
                .attributes(Map.of("method", "POST", "status", 201)).build();
        Node custom = Node.builder(StableId.of("custom:create-overview"), NodeType.CUSTOM_HTML_SECTION, "Overview")
                .attributes(Map.of(
                        "references", List.of(endpoint.id().value(), "table:public.missing"),
                        "assertions", Map.of(endpoint.id().value(), Map.of(
                                "type", "HTTP_ENDPOINT", "attributes.method", "GET", "attributes.status", 201))))
                .build();
        DocumentationGraph graph = DocumentationGraph.of("p", "c", Instant.EPOCH,
                List.of(endpoint, custom), List.of());

        CustomDocumentationResult result = new CustomDocumentationReconciler().reconcile(graph, Instant.EPOCH);

        assertEquals(2, result.conflicts().size());
        Node reconciled = result.graph().node(custom.id()).orElseThrow();
        assertEquals(Confidence.CONFLICTED, reconciled.metadata().confidence());
        assertTrue(reconciled.metadata().conflicted());
        assertTrue(result.conflicts().stream().anyMatch(conflict -> conflict.description().contains("missing")));
        assertTrue(result.conflicts().stream().anyMatch(conflict -> conflict.field().contains("attributes.method")));
    }

    @Test
    void acceptsMatchingCustomClaims() {
        Node table = Node.builder(StableId.of("table:public.projects"), NodeType.DB_TABLE, "projects")
                .attributes(Map.of("schema", "public")).build();
        Node custom = Node.builder(StableId.of("custom:table-note"), NodeType.CUSTOM_HTML_SECTION, "Note")
                .attributes(Map.of("assertions", Map.of(table.id().value(),
                        Map.of("type", "DB_TABLE", "attributes.schema", "public")))).build();

        CustomDocumentationResult result = new CustomDocumentationReconciler().reconcile(
                DocumentationGraph.of("p", "", null, List.of(table, custom), List.of()), Instant.EPOCH);

        assertTrue(result.conflicts().isEmpty());
        assertTrue(result.graph().edges().stream().anyMatch(edge -> edge.from().equals(table.id())
                && edge.to().equals(custom.id())));
    }

    @Test
    void removesObsoleteCustomConflictAfterClaimIsCorrected() {
        Node endpoint = Node.builder(StableId.of("endpoint:POST:/projects"), NodeType.HTTP_ENDPOINT, "Create")
                .attributes(Map.of("method", "POST")).build();
        Node wrong = Node.builder(StableId.of("custom:create"), NodeType.CUSTOM_HTML_SECTION, "Create docs")
                .attributes(Map.of("assertions", Map.of(endpoint.id().value(),
                        Map.of("attributes.method", "GET")))).build();
        CustomDocumentationReconciler reconciler = new CustomDocumentationReconciler();
        CustomDocumentationResult first = reconciler.reconcile(DocumentationGraph.of("p", "", null,
                List.of(endpoint, wrong), List.of()), Instant.EPOCH);
        Node corrected = first.graph().node(wrong.id()).orElseThrow().toBuilder()
                .attributes(Map.of("assertions", Map.of(endpoint.id().value(),
                        Map.of("attributes.method", "POST")))).build();

        CustomDocumentationResult second = reconciler.reconcile(DocumentationGraph.of("p", "", null,
                List.of(endpoint, corrected), first.graph().edges()), Instant.EPOCH.plusSeconds(1));

        assertTrue(second.conflicts().isEmpty());
        assertTrue(second.graph().node(corrected.id()).orElseThrow().metadata().conflicts().isEmpty());
        assertEquals(Confidence.UNKNOWN, second.graph().node(corrected.id()).orElseThrow().metadata().confidence());
    }
}
