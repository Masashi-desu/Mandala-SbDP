package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaleDetectorTest {
    @Test
    void detectsChangedAndMissingFingerprints() {
        Node changed = Node.builder(StableId.of("java:A"), NodeType.JAVA_CLASS, "A")
                .attributes(Map.of("sourceFingerprint", "old-a")).build();
        Node missing = Node.builder(StableId.of("java:B"), NodeType.JAVA_CLASS, "B")
                .attributes(Map.of("sourceFingerprint", "old-b")).build();
        DocumentationGraph graph = DocumentationGraph.of("p", "", null, List.of(changed, missing), List.of());

        StaleResult result = new StaleDetector().detectFingerprints(graph,
                Map.of(changed.id(), "new-a"), true, Instant.EPOCH);

        assertEquals(2, result.staleIds().size());
        assertTrue(result.graph().node(changed.id()).orElseThrow().metadata().stale().stale());
        assertEquals(Confidence.STALE, result.graph().node(missing.id()).orElseThrow().metadata().confidence());
    }

    @Test
    void partialFingerprintSnapshotDoesNotMarkUnknownItemsMissing() {
        Node node = Node.builder(StableId.of("java:A"), NodeType.JAVA_CLASS, "A")
                .attributes(Map.of("sourceFingerprint", "old")).build();
        DocumentationGraph graph = DocumentationGraph.of("p", "", null, List.of(node), List.of());

        StaleResult result = new StaleDetector().detectFingerprints(graph, Map.of(), false, Instant.EPOCH);

        assertTrue(result.staleIds().isEmpty());
        assertFalse(result.graph().nodes().getFirst().metadata().stale().stale());
    }

    @Test
    void clearsFingerprintStaleAfterTheSourceWasReanalyzedAtMatchingContent() {
        ElementMetadata stale = ElementMetadata.builder().stale(io.github.mandala.sbdp.model.StaleInfo.stale(
                io.github.mandala.sbdp.model.StaleCause.SOURCE_CHANGED,
                "changed", Instant.EPOCH, "old", "current")).confidence(Confidence.STALE).build();
        Node node = Node.builder(StableId.of("java:A"), NodeType.JAVA_CLASS, "A").metadata(stale)
                .attributes(Map.of("sourceFingerprint", "current")).build();
        DocumentationGraph graph = DocumentationGraph.of("p", "", null, List.of(node), List.of());

        StaleResult result = new StaleDetector().detectFingerprints(graph,
                Map.of(node.id(), "current"), true, Instant.EPOCH);

        assertFalse(result.graph().nodes().getFirst().metadata().stale().stale());
        assertEquals(Confidence.UNKNOWN, result.graph().nodes().getFirst().metadata().confidence());
    }

    @Test
    void marksHumanDescriptionAffectedByImplementationChange() {
        Evidence human = Evidence.humanReviewed("mandala/custom", "Business intent");
        ElementMetadata metadata = ElementMetadata.builder().evidence(List.of(human))
                .confidence(Confidence.HUMAN_REVIEWED).build();
        Node previousNode = Node.builder(StableId.of("java:A"), NodeType.JAVA_CLASS, "A")
                .description("Business intent").metadata(metadata).attributes(Map.of("signature", "old")).build();
        Node currentNode = previousNode.toBuilder().attributes(Map.of("signature", "new")).build();
        DocumentationGraph previous = DocumentationGraph.of("p", "a", null, List.of(previousNode), List.of());
        DocumentationGraph current = DocumentationGraph.of("p", "b", null, List.of(currentNode), List.of());
        Diff diff = new GraphDiffer().diff(previous, current);

        StaleResult result = new StaleDetector().detectAffectedDocumentation(previous, current, diff, Instant.EPOCH);

        assertEquals(Set.of(currentNode.id()), result.staleIds());
        assertEquals(Confidence.STALE, result.graph().nodes().getFirst().metadata().confidence());
    }

    @Test
    void doesNotImmediatelyStaleNewOrEditedDocumentationWithoutImplementationChange() {
        Node implementation = Node.of("java:A", NodeType.JAVA_CLASS, "A");
        DocumentationGraph previous = DocumentationGraph.of("p", "a", null, List.of(implementation), List.of());
        Node custom = Node.builder(StableId.of("custom:a"), NodeType.CUSTOM_HTML_SECTION, "A docs")
                .description("Fresh review")
                .metadata(ElementMetadata.builder().evidence(List.of(Evidence.humanReviewed("a.html", "Fresh review")))
                        .confidence(Confidence.HUMAN_REVIEWED).build())
                .attributes(Map.of("references", List.of(implementation.id().value()))).build();
        DocumentationGraph withCustom = DocumentationGraph.of("p", "b", null,
                List.of(implementation, custom), List.of());
        Diff added = new GraphDiffer().diff(previous, withCustom);

        StaleResult addedResult = new StaleDetector().detectAffectedDocumentation(previous, withCustom,
                added, Instant.EPOCH);

        assertFalse(addedResult.graph().node(custom.id()).orElseThrow().metadata().stale().stale());

        Node edited = custom.toBuilder().description("Fresh reviewed text")
                .metadata(ElementMetadata.builder().evidence(List.of(Evidence.humanReviewed("a.html",
                        "Fresh reviewed text"))).confidence(Confidence.HUMAN_REVIEWED).build()).build();
        DocumentationGraph editedGraph = DocumentationGraph.of("p", "c", null,
                List.of(implementation, edited), List.of());
        StaleResult editedResult = new StaleDetector().detectAffectedDocumentation(withCustom, editedGraph,
                new GraphDiffer().diff(withCustom, editedGraph), Instant.EPOCH.plusSeconds(1));
        assertFalse(editedResult.graph().node(edited.id()).orElseThrow().metadata().stale().stale());
    }

    @Test
    void doesNotStaleJavadocOnANewlyAddedImplementationNode() {
        ElementMetadata metadata = ElementMetadata.builder()
                .evidence(List.of(Evidence.of(EvidenceType.JAVADOC, "New.java", "Current documentation")))
                .confidence(Confidence.INFERRED).build();
        Node added = Node.builder(StableId.of("java:New"), NodeType.JAVA_CLASS, "New")
                .description("Current documentation").metadata(metadata).build();
        DocumentationGraph previous = DocumentationGraph.empty("p");
        DocumentationGraph current = DocumentationGraph.of("p", "c", null, List.of(added), List.of());

        StaleResult result = new StaleDetector().detectAffectedDocumentation(previous, current,
                new GraphDiffer().diff(previous, current), Instant.EPOCH);

        assertFalse(result.graph().nodes().getFirst().metadata().stale().stale());
    }
}
