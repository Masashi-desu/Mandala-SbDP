package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.model.StaleInfo;
import io.github.mandala.sbdp.model.StaleCause;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class StaleDetector {
    private final ConfidenceEvaluator confidenceEvaluator = new ConfidenceEvaluator();
    private final ImpactAnalyzer impactAnalyzer = new ImpactAnalyzer();

    /** Marks elements when the fingerprint recorded by their source adapter no longer matches current content. */
    public StaleResult detectFingerprints(DocumentationGraph graph, Map<StableId, String> currentFingerprints,
                                          boolean completeSnapshot, Instant detectedAt) {
        Set<StableId> staleIds = new TreeSet<>();
        List<Node> nodes = graph.nodes().stream().map(node -> {
            String recorded = recordedFingerprint(node.metadata(), node.attributes());
            if (recorded.isBlank()) return node;
            String current = currentFingerprints.get(node.id());
            if (current == null && !completeSnapshot) return node;
            if (current == null || !recorded.equals(current)) {
                staleIds.add(node.id());
                String reason = current == null ? "The source item no longer exists"
                        : "The source content changed after this item was analyzed";
                return node.toBuilder().metadata(mark(node.metadata(),
                        current == null ? StaleCause.SOURCE_MISSING : StaleCause.SOURCE_CHANGED,
                        reason, detectedAt, recorded, current == null ? "<missing>" : current)).build();
            }
            if (node.metadata().stale().stale() && node.attributes().containsKey("sourceFingerprint")) {
                return node.toBuilder().metadata(clearFingerprintStale(node.metadata())).build();
            }
            return node;
        }).toList();
        List<Edge> edges = graph.edges().stream().map(edge -> {
            String recorded = recordedFingerprint(edge.metadata(), edge.attributes());
            if (recorded.isBlank()) return edge;
            String current = currentFingerprints.get(edge.id());
            if (current == null && !completeSnapshot) return edge;
            if (current == null || !recorded.equals(current)) {
                staleIds.add(edge.id());
                String reason = current == null ? "The source relation no longer exists"
                        : "The source relation changed after this item was analyzed";
                return edge.toBuilder().metadata(mark(edge.metadata(),
                        current == null ? StaleCause.SOURCE_MISSING : StaleCause.SOURCE_CHANGED,
                        reason, detectedAt, recorded, current == null ? "<missing>" : current)).build();
            }
            if (edge.metadata().stale().stale() && edge.attributes().containsKey("sourceFingerprint")) {
                return edge.toBuilder().metadata(clearFingerprintStale(edge.metadata())).build();
            }
            return edge;
        }).toList();
        return new StaleResult(new DocumentationGraph(graph.schemaVersion(), graph.projectId(), graph.targetCommit(),
                graph.analyzedAt(), nodes, edges), staleIds);
    }

    /** Marks reviewed/inferred descriptions affected by an implementation graph change. */
    public StaleResult detectAffectedDocumentation(DocumentationGraph previous, DocumentationGraph current,
                                                   Diff diff, Instant detectedAt) {
        Set<StableId> changed = implementationChangeRoots(diff);
        Set<StableId> impacted = new TreeSet<>(impactAnalyzer.analyze(current, changed, Integer.MAX_VALUE).allImpacted());
        impacted.addAll(impactAnalyzer.analyze(previous, changed, Integer.MAX_VALUE).allImpacted());
        impacted.addAll(changed);
        Set<StableId> staleIds = new TreeSet<>();
        List<Node> nodes = current.nodes().stream().map(node -> {
            Node baseline = baseline(previous, node);
            boolean retainedSource = !node.id().equals(baseline == null ? node.id() : baseline.id());
            boolean unchangedDocumentation = baseline != null
                    && (retainedSource || durableSignature(node).equals(durableSignature(baseline)));
            boolean affected = impacted.contains(node.id())
                    || (baseline != null && impacted.contains(baseline.id()));
            if (affected && unchangedDocumentation && hasDurableDescription(node)
                    && hasDurableDescription(baseline)) {
                staleIds.add(node.id());
                String previousFingerprint = semanticFingerprint(baseline);
                String currentFingerprint = semanticFingerprint(node);
                return node.toBuilder().metadata(mark(node.metadata(), StaleCause.IMPACTED_IMPLEMENTATION,
                        "A related implementation element changed; review the retained description",
                        detectedAt, previousFingerprint, currentFingerprint)).build();
            }
            return node;
        }).toList();
        Map<StableId, Edge> previousEdges = previous.edgeMap();
        List<Edge> edges = current.edges().stream().map(edge -> {
            Edge baseline = previousEdges.get(edge.id());
            if (edge.type() != io.github.mandala.sbdp.model.EdgeType.DOCUMENTED_BY && baseline != null
                    && hasDurableDescription(edge.metadata()) && hasDurableDescription(baseline.metadata())
                    && durableSignature(edge.metadata(), edge.description())
                    .equals(durableSignature(baseline.metadata(), baseline.description()))
                    && (impacted.contains(edge.from()) || impacted.contains(edge.to()))) {
                staleIds.add(edge.id());
                return edge.toBuilder().metadata(mark(edge.metadata(), StaleCause.IMPACTED_IMPLEMENTATION,
                        "A related implementation element changed; review the retained relation description",
                        detectedAt, semanticFingerprint(baseline), semanticFingerprint(edge))).build();
            }
            return edge;
        }).toList();
        nodes.stream().filter(node -> node.metadata().stale().stale()).map(Node::id).forEach(staleIds::add);
        edges.stream().filter(edge -> edge.metadata().stale().stale()).map(Edge::id).forEach(staleIds::add);
        return new StaleResult(new DocumentationGraph(current.schemaVersion(), current.projectId(),
                current.targetCommit(), current.analyzedAt(), nodes, edges), staleIds);
    }

    private Set<StableId> implementationChangeRoots(Diff diff) {
        Set<StableId> changed = new TreeSet<>();
        diff.addedNodes().stream().filter(node -> node.type() != NodeType.CUSTOM_HTML_SECTION)
                .forEach(node -> changed.add(node.id()));
        diff.removedNodes().stream().filter(node -> node.type() != NodeType.CUSTOM_HTML_SECTION)
                .forEach(node -> changed.add(node.id()));
        diff.modifiedNodes().stream()
                .filter(change -> change.after().type() != NodeType.CUSTOM_HTML_SECTION)
                .filter(change -> change.changedFields().stream()
                        .anyMatch(field -> field.equals("type") || field.equals("displayName") || field.equals("attributes")))
                .forEach(change -> changed.add(change.id()));
        diff.addedEdges().stream().filter(edge -> edge.type() != io.github.mandala.sbdp.model.EdgeType.DOCUMENTED_BY)
                .forEach(edge -> { changed.add(edge.from()); changed.add(edge.to()); });
        diff.removedEdges().stream().filter(edge -> edge.type() != io.github.mandala.sbdp.model.EdgeType.DOCUMENTED_BY)
                .forEach(edge -> { changed.add(edge.from()); changed.add(edge.to()); });
        diff.modifiedEdges().stream()
                .filter(change -> change.before().type() != io.github.mandala.sbdp.model.EdgeType.DOCUMENTED_BY
                        || change.after().type() != io.github.mandala.sbdp.model.EdgeType.DOCUMENTED_BY)
                .forEach(change -> {
                    changed.add(change.before().from()); changed.add(change.before().to());
                    changed.add(change.after().from()); changed.add(change.after().to());
                });
        return changed;
    }

    private Node baseline(DocumentationGraph previous, Node current) {
        Object sourceNodeId = current.attributes().get("sourceNodeId");
        if (sourceNodeId != null) {
            try {
                return previous.node(StableId.of(String.valueOf(sourceNodeId))).orElse(null);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return previous.node(current.id()).orElse(null);
    }

    private boolean hasDurableDescription(Node node) {
        if (node == null) return false;
        return node.type() == NodeType.CUSTOM_HTML_SECTION || node.metadata().evidence().stream().anyMatch(evidence ->
                evidence.type() == EvidenceType.HUMAN_INPUT || evidence.type() == EvidenceType.JAVADOC
                        || evidence.type() == EvidenceType.AGENT_INFERENCE);
    }

    private boolean hasDurableDescription(ElementMetadata metadata) {
        return metadata.evidence().stream().anyMatch(evidence -> evidence.type() == EvidenceType.HUMAN_INPUT
                || evidence.type() == EvidenceType.JAVADOC || evidence.type() == EvidenceType.AGENT_INFERENCE);
    }

    private String durableSignature(Node node) {
        return durableSignature(node.metadata(), node.description());
    }

    private String durableSignature(ElementMetadata metadata, String description) {
        List<String> evidence = metadata.evidence().stream().filter(item -> item.type() == EvidenceType.HUMAN_INPUT
                        || item.type() == EvidenceType.JAVADOC || item.type() == EvidenceType.AGENT_INFERENCE)
                .map(item -> item.type() + "\u0000" + item.scope() + "\u0000" + item.source() + "\u0000"
                        + item.description() + "\u0000" + item.details())
                .sorted().toList();
        return description + "\u0000" + metadata.reviewState() + "\u0000" + evidence;
    }

    private ElementMetadata mark(ElementMetadata metadata, StaleCause cause, String reason, Instant detectedAt,
                                 String previousFingerprint, String currentFingerprint) {
        StaleInfo stale = StaleInfo.stale(cause, reason, detectedAt, previousFingerprint, currentFingerprint);
        ElementMetadata provisional = new ElementMetadata(metadata.evidence(), metadata.sourceLocations(),
                metadata.targetCommit(), metadata.analyzedAt(), metadata.adapter(), Confidence.STALE,
                metadata.reviewState(), stale,
                metadata.conflicts(), metadata.warnings(), metadata.relatedTraces(), metadata.relatedScenarios());
        return new ElementMetadata(metadata.evidence(), metadata.sourceLocations(), metadata.targetCommit(),
                metadata.analyzedAt(), metadata.adapter(), confidenceEvaluator.evaluate(provisional),
                metadata.reviewState(), stale, metadata.conflicts(), metadata.warnings(), metadata.relatedTraces(),
                metadata.relatedScenarios());
    }

    private ElementMetadata clearFingerprintStale(ElementMetadata metadata) {
        if (metadata.stale().cause() != StaleCause.SOURCE_CHANGED
                && metadata.stale().cause() != StaleCause.SOURCE_MISSING) return metadata;
        ElementMetadata provisional = new ElementMetadata(metadata.evidence(), metadata.sourceLocations(),
                metadata.targetCommit(), metadata.analyzedAt(), metadata.adapter(), Confidence.UNKNOWN,
                metadata.reviewState(), StaleInfo.fresh(), metadata.conflicts(), metadata.warnings(),
                metadata.relatedTraces(), metadata.relatedScenarios());
        return new ElementMetadata(metadata.evidence(), metadata.sourceLocations(), metadata.targetCommit(),
                metadata.analyzedAt(), metadata.adapter(), confidenceEvaluator.evaluate(provisional),
                metadata.reviewState(), StaleInfo.fresh(), metadata.conflicts(), metadata.warnings(),
                metadata.relatedTraces(), metadata.relatedScenarios());
    }

    private String recordedFingerprint(ElementMetadata metadata, Map<String, Object> attributes) {
        Object value = attributes.get("sourceFingerprint");
        if (value != null) return String.valueOf(value);
        return metadata.stale().sourceFingerprint();
    }

    private String semanticFingerprint(Node node) {
        if (node == null) return "<missing>";
        return StableIdGenerator.digest(node.type() + "\u0000" + node.displayName() + "\u0000" + node.description()
                + "\u0000" + node.attributes());
    }

    private String semanticFingerprint(Edge edge) {
        return StableIdGenerator.digest(edge.type() + "\u0000" + edge.from() + "\u0000" + edge.to()
                + "\u0000" + edge.description() + "\u0000" + edge.attributes());
    }
}
