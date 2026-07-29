package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.StableId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraphValidator {
    private final ConfidenceEvaluator confidenceEvaluator = new ConfidenceEvaluator();

    public ValidationReport validate(DocumentationGraph graph) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<StableId> nodeIds = graph.nodeMap().keySet();
        Map<SemanticEdge, Edge> semanticEdges = new HashMap<>();
        graph.edges().forEach(edge -> {
            if (!nodeIds.contains(edge.from())) {
                issues.add(error("DANGLING_EDGE_SOURCE", edge.id(), "Missing source node " + edge.from()));
            }
            if (!nodeIds.contains(edge.to())) {
                issues.add(error("DANGLING_EDGE_TARGET", edge.id(), "Missing target node " + edge.to()));
            }
            SemanticEdge key = new SemanticEdge(edge.type(), edge.from(), edge.to());
            Edge duplicate = semanticEdges.putIfAbsent(key, edge);
            if (duplicate != null && !duplicate.id().equals(edge.id())) {
                issues.add(error("DUPLICATE_RELATION", edge.id(),
                        "Same semantic relation is already stored as " + duplicate.id()));
            }
            validateMetadata(edge.id(), edge.metadata(), issues);
        });
        graph.nodes().forEach(node -> validateMetadata(node.id(), node.metadata(), issues));
        detectStoredInversePairs(graph.edges(), issues);
        return new ValidationReport(issues);
    }

    private void validateMetadata(StableId id, ElementMetadata metadata, List<ValidationIssue> issues) {
        if (metadata.evidence().isEmpty()) {
            issues.add(warning("MISSING_EVIDENCE", id, "Element has no supporting evidence"));
        }
        Confidence expected = confidenceEvaluator.evaluate(metadata);
        if (metadata.confidence() != expected) {
            issues.add(error("INVALID_CONFIDENCE", id, "Stored confidence " + metadata.confidence()
                    + " does not match evidence-derived confidence " + expected));
        }
        if (metadata.confidence() == Confidence.OBSERVED && metadata.evidence().stream().noneMatch(evidence ->
                evidence.type() == EvidenceType.RUNTIME_OBSERVATION
                        || evidence.type() == EvidenceType.PLAYWRIGHT_OBSERVATION)) {
            issues.add(error("OBSERVED_WITHOUT_OBSERVATION", id,
                    "Observed confidence requires runtime or Playwright observation evidence"));
        }
        if (metadata.confidence() == Confidence.DECLARED && metadata.evidence().stream().noneMatch(evidence ->
                evidence.type() == EvidenceType.SPRING_MAPPING || evidence.type() == EvidenceType.OPENAPI
                        || evidence.type() == EvidenceType.DATABASE_INTROSPECTION)) {
            issues.add(error("DECLARED_WITHOUT_DECLARATION", id,
                    "Declared confidence requires Spring, OpenAPI, or database declaration evidence"));
        }
    }

    private void detectStoredInversePairs(List<Edge> edges, List<ValidationIssue> issues) {
        Map<SemanticEdge, Edge> index = new HashMap<>();
        edges.forEach(edge -> index.put(new SemanticEdge(edge.type(), edge.from(), edge.to()), edge));
        for (Edge edge : edges) {
            EdgeType inverse = inverse(edge.type());
            if (inverse == null) continue;
            Edge reverse = index.get(new SemanticEdge(inverse, edge.to(), edge.from()));
            if (reverse != null && edge.id().compareTo(reverse.id()) < 0) {
                issues.add(error("DUPLICATE_INVERSE_RELATION", reverse.id(),
                        "Reverse relation duplicates " + edge.id() + "; use the bidirectional index instead"));
            }
        }
    }

    private EdgeType inverse(EdgeType type) {
        return switch (type) {
            case CALLS -> EdgeType.CALLED_BY;
            case CALLED_BY -> EdgeType.CALLS;
            default -> null;
        };
    }

    private ValidationIssue error(String code, StableId id, String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, code, id, message);
    }

    private ValidationIssue warning(String code, StableId id, String message) {
        return new ValidationIssue(ValidationSeverity.WARNING, code, id, message);
    }

    private record SemanticEdge(EdgeType type, StableId from, StableId to) {
    }
}
