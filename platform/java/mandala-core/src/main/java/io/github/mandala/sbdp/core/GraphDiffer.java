package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeChange;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeChange;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Computes semantic differences; ordering, analysis timestamps and source line movement are ignored. */
public final class GraphDiffer {
    private final Clock clock;
    private final ImpactAnalyzer impactAnalyzer;

    public GraphDiffer() {
        this(Clock.systemUTC(), new ImpactAnalyzer());
    }

    public GraphDiffer(Clock clock, ImpactAnalyzer impactAnalyzer) {
        this.clock = clock;
        this.impactAnalyzer = impactAnalyzer;
    }

    public Diff diff(DocumentationGraph before, DocumentationGraph after) {
        Map<StableId, Node> oldNodes = before.nodeMap();
        Map<StableId, Node> newNodes = after.nodeMap();
        List<Node> addedNodes = after.nodes().stream().filter(node -> !oldNodes.containsKey(node.id())).toList();
        List<Node> removedNodes = before.nodes().stream().filter(node -> !newNodes.containsKey(node.id())).toList();
        List<NodeChange> modifiedNodes = new ArrayList<>();
        oldNodes.keySet().stream().filter(newNodes::containsKey).sorted().forEach(id -> {
            Set<String> fields = changedFields(oldNodes.get(id), newNodes.get(id));
            if (!fields.isEmpty()) modifiedNodes.add(new NodeChange(id, oldNodes.get(id), newNodes.get(id), fields));
        });

        Map<StableId, Edge> oldEdges = before.edgeMap();
        Map<StableId, Edge> newEdges = after.edgeMap();
        List<Edge> addedEdges = after.edges().stream().filter(edge -> !oldEdges.containsKey(edge.id())).toList();
        List<Edge> removedEdges = before.edges().stream().filter(edge -> !newEdges.containsKey(edge.id())).toList();
        List<EdgeChange> modifiedEdges = new ArrayList<>();
        oldEdges.keySet().stream().filter(newEdges::containsKey).sorted().forEach(id -> {
            Set<String> fields = changedFields(oldEdges.get(id), newEdges.get(id));
            if (!fields.isEmpty()) modifiedEdges.add(new EdgeChange(id, oldEdges.get(id), newEdges.get(id), fields));
        });

        Set<StableId> direct = new TreeSet<>();
        addedNodes.forEach(node -> direct.add(node.id()));
        removedNodes.forEach(node -> direct.add(node.id()));
        modifiedNodes.forEach(change -> direct.add(change.id()));
        addedEdges.forEach(edge -> { direct.add(edge.from()); direct.add(edge.to()); });
        removedEdges.forEach(edge -> { direct.add(edge.from()); direct.add(edge.to()); });
        modifiedEdges.forEach(change -> {
            direct.add(change.before().from()); direct.add(change.before().to());
            direct.add(change.after().from()); direct.add(change.after().to());
        });
        Set<StableId> impacted = new TreeSet<>(direct);
        impacted.addAll(impactAnalyzer.analyze(after, direct, Integer.MAX_VALUE).allImpacted());
        impacted.addAll(impactAnalyzer.analyze(before, direct, Integer.MAX_VALUE).allImpacted());
        return new Diff(before.targetCommit(), after.targetCommit(), Instant.now(clock), addedNodes, removedNodes,
                modifiedNodes, addedEdges, removedEdges, modifiedEdges, impacted);
    }

    private Set<String> changedFields(Node before, Node after) {
        Set<String> fields = new TreeSet<>();
        if (before.type() != after.type()) fields.add("type");
        if (!before.displayName().equals(after.displayName())) fields.add("displayName");
        if (!before.description().equals(after.description())) fields.add("description");
        boolean runtime = runtimeNode(before) || runtimeNode(after);
        if (!SemanticAttributes.normalize(before.attributes(), runtime)
                .equals(SemanticAttributes.normalize(after.attributes(), runtime))) {
            fields.add("attributes");
        }
        if (!semanticMetadata(before.metadata()).equals(semanticMetadata(after.metadata()))) fields.add("metadata");
        return fields;
    }

    private Set<String> changedFields(Edge before, Edge after) {
        Set<String> fields = new TreeSet<>();
        if (before.type() != after.type()) fields.add("type");
        if (!before.from().equals(after.from())) fields.add("from");
        if (!before.to().equals(after.to())) fields.add("to");
        if (!before.description().equals(after.description())) fields.add("description");
        boolean runtime = runtimeMetadata(before.metadata()) || runtimeMetadata(after.metadata());
        if (!SemanticAttributes.normalize(before.attributes(), runtime)
                .equals(SemanticAttributes.normalize(after.attributes(), runtime))) {
            fields.add("attributes");
        }
        if (!semanticMetadata(before.metadata()).equals(semanticMetadata(after.metadata()))) fields.add("metadata");
        return fields;
    }

    private SemanticMetadata semanticMetadata(ElementMetadata metadata) {
        List<SemanticEvidence> evidence = metadata.evidence().stream().map(this::semanticEvidence).sorted().toList();
        return new SemanticMetadata(evidence, metadata.confidence(), metadata.reviewState(), metadata.stale().stale(),
                metadata.stale().reason(), metadata.conflicts().stream().filter(conflict -> conflict.isOpen())
                .map(conflict -> conflict.id().value()).sorted().toList(), metadata.warnings(), metadata.relatedTraces(),
                metadata.relatedScenarios());
    }

    private SemanticEvidence semanticEvidence(Evidence evidence) {
        return new SemanticEvidence(evidence.id().value(), evidence.type().name(), evidence.scope().name(),
                evidence.source(), evidence.description(), evidence.confidence().name(),
                SemanticAttributes.normalize(evidence.details(), runtimeEvidence(evidence)));
    }

    private boolean runtimeNode(Node node) {
        return node.type() == NodeType.TRACE || node.type() == NodeType.SPAN || runtimeMetadata(node.metadata());
    }

    private boolean runtimeMetadata(ElementMetadata metadata) {
        return metadata.evidence().stream().anyMatch(this::runtimeEvidence);
    }

    private boolean runtimeEvidence(Evidence evidence) {
        return evidence.type() == EvidenceType.RUNTIME_OBSERVATION
                || evidence.type() == EvidenceType.PLAYWRIGHT_OBSERVATION;
    }

    private record SemanticMetadata(List<SemanticEvidence> evidence, Object confidence, Object reviewState,
                                    boolean stale, String staleReason, List<String> conflicts, List<String> warnings,
                                    Set<StableId> traces, Set<String> scenarios) {
    }

    private record SemanticEvidence(String id, String type, String scope, String source, String description,
                                    String confidence, Map<String, Object> details) implements Comparable<SemanticEvidence> {
        @Override
        public int compareTo(SemanticEvidence other) { return id.compareTo(other.id); }
    }
}
