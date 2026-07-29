package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphValidatorTest {
    @Test
    void rejectsDanglingAndStoredInverseRelations() {
        Node a = Node.of("java:A", NodeType.JAVA_CLASS, "A");
        Node b = Node.of("java:B", NodeType.JAVA_CLASS, "B");
        Edge calls = Edge.of("edge:calls:1", EdgeType.CALLS, a.id(), b.id());
        Edge calledBy = Edge.of("edge:called:1", EdgeType.CALLED_BY, b.id(), a.id());
        Edge dangling = Edge.of("edge:uses:1", EdgeType.USED_BY, StableId.of("java:Missing"), a.id());
        DocumentationGraph graph = DocumentationGraph.of("p", "", null, List.of(a, b),
                List.of(calls, calledBy, dangling));

        ValidationReport report = new GraphValidator().validate(graph);

        assertFalse(report.valid());
        assertEquals(2, report.errors().size());
    }

    @Test
    void rejectsConfidenceThatIsStrongerThanItsEvidence() {
        Node node = Node.builder(StableId.of("java:Guess"), NodeType.JAVA_CLASS, "Guess")
                .metadata(ElementMetadata.builder()
                        .evidence(List.of(Evidence.of(EvidenceType.AGENT_INFERENCE, "agent", "guess")))
                        .confidence(Confidence.OBSERVED).build())
                .build();

        ValidationReport report = new GraphValidator().validate(
                DocumentationGraph.of("p", "c", null, List.of(node), List.of()));

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(issue -> issue.code().equals("INVALID_CONFIDENCE")));
        assertTrue(report.errors().stream().anyMatch(issue -> issue.code().equals("OBSERVED_WITHOUT_OBSERVATION")));
    }
}
