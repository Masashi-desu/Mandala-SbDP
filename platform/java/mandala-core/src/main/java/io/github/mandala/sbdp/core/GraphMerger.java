package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceScope;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.ReviewState;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.model.StaleInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reconciles graph fragments while preserving every source's evidence and surfacing disagreements. */
public final class GraphMerger {
    private final EvidenceMerger evidenceMerger;
    private final ConfidenceEvaluator confidenceEvaluator;
    private final ConflictDetector conflictDetector;

    public GraphMerger() {
        this(new EvidenceMerger(), new ConfidenceEvaluator(), new ConflictDetector());
    }

    public GraphMerger(EvidenceMerger evidenceMerger, ConfidenceEvaluator confidenceEvaluator,
                       ConflictDetector conflictDetector) {
        this.evidenceMerger = evidenceMerger;
        this.confidenceEvaluator = confidenceEvaluator;
        this.conflictDetector = conflictDetector;
    }

    public MergeResult merge(Collection<DocumentationGraph> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            throw new IllegalArgumentException("At least one graph fragment is required");
        }
        List<DocumentationGraph> graphs = List.copyOf(fragments);
        String projectId = graphs.getFirst().projectId();
        if (graphs.stream().anyMatch(graph -> !graph.projectId().equals(projectId))) {
            throw new IllegalArgumentException("Cannot merge graphs for different projects");
        }
        Instant mergedAt = graphs.stream().map(DocumentationGraph::analyzedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        String commit = graphs.stream().map(DocumentationGraph::targetCommit).filter(value -> !value.isBlank())
                .max(String::compareTo).orElse("");
        String schema = graphs.stream().map(DocumentationGraph::schemaVersion).max(String::compareTo)
                .orElse(DocumentationGraph.CURRENT_SCHEMA_VERSION);

        List<Conflict> conflicts = new ArrayList<>();
        Map<StableId, List<Node>> nodeGroups = graphs.stream().flatMap(graph -> graph.nodes().stream())
                .collect(Collectors.groupingBy(Node::id, TreeMap::new, Collectors.toList()));
        Map<StableId, Node> nodes = new TreeMap<>();
        nodeGroups.forEach((id, values) -> nodes.put(id, mergeNodes(values, mergedAt, conflicts)));
        Map<StableId, List<Edge>> edgeGroups = graphs.stream().flatMap(graph -> graph.edges().stream())
                .collect(Collectors.groupingBy(Edge::id, TreeMap::new, Collectors.toList()));
        Map<StableId, Edge> edges = new TreeMap<>();
        edgeGroups.forEach((id, values) -> edges.put(id, mergeEdges(values, mergedAt, conflicts)));

        Set<StableId> nodeIds = nodes.keySet();
        edges.replaceAll((id, edge) -> {
            List<Conflict> dangling = new ArrayList<>();
            if (!nodeIds.contains(edge.from())) dangling.add(conflictDetector.dangling(edge, edge.from(), mergedAt));
            if (!nodeIds.contains(edge.to())) dangling.add(conflictDetector.dangling(edge, edge.to(), mergedAt));
            if (dangling.isEmpty()) return edge;
            conflicts.addAll(dangling);
            ElementMetadata metadata = mergeMetadata(List.of(edge.metadata()), dangling);
            return edge.toBuilder().metadata(metadata).build();
        });

        DocumentationGraph graph = new DocumentationGraph(schema, projectId, commit, mergedAt,
                List.copyOf(nodes.values()), List.copyOf(edges.values()));
        List<Conflict> graphConflicts = java.util.stream.Stream.concat(
                        graph.nodes().stream().flatMap(node -> node.metadata().conflicts().stream()),
                        graph.edges().stream().flatMap(edge -> edge.metadata().conflicts().stream()))
                .toList();
        return new MergeResult(graph, graphConflicts);
    }

    private Node mergeNodes(List<Node> values, Instant detectedAt, List<Conflict> allConflicts) {
        List<Node> ordered = values.stream().sorted(Comparator.comparing(GraphMerger::nodeKey)).toList();
        List<Conflict> conflicts = pairwiseNodeConflicts(ordered, detectedAt);
        allConflicts.addAll(conflicts);
        Node technical = selectNode(ordered, EvidenceScope.TECHNICAL_FACT, node -> true);
        Node display = selectNode(ordered, EvidenceScope.TECHNICAL_FACT, node -> !node.displayName().isBlank());
        Node description = selectNode(ordered, EvidenceScope.DESIGN_INTENT, node -> !node.description().isBlank());
        if (description == null || confidenceEvaluator.metadataAuthority(
                description.metadata(), EvidenceScope.DESIGN_INTENT) == 0) {
            description = selectNode(ordered, EvidenceScope.TECHNICAL_FACT, node -> !node.description().isBlank());
        }
        Map<String, Object> attributes = new TreeMap<>();
        Set<String> keys = ordered.stream().flatMap(node -> node.attributes().keySet().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        for (String key : keys) {
            Node provider = selectNode(ordered, EvidenceScope.TECHNICAL_FACT,
                    node -> node.attributes().containsKey(key));
            attributes.put(key, provider.attributes().get(key));
        }
        ElementMetadata metadata = mergeMetadata(ordered.stream().map(Node::metadata).toList(), conflicts);
        return new Node(ordered.getFirst().id(), technical.type(), display.displayName(),
                description == null ? "" : description.description(), metadata, attributes);
    }

    private Edge mergeEdges(List<Edge> values, Instant detectedAt, List<Conflict> allConflicts) {
        List<Edge> ordered = values.stream().sorted(Comparator.comparing(GraphMerger::edgeKey)).toList();
        List<Conflict> conflicts = pairwiseEdgeConflicts(ordered, detectedAt);
        allConflicts.addAll(conflicts);
        Edge technical = selectEdge(ordered, EvidenceScope.TECHNICAL_FACT, edge -> true);
        Edge description = selectEdge(ordered, EvidenceScope.DESIGN_INTENT, edge -> !edge.description().isBlank());
        if (description == null || confidenceEvaluator.metadataAuthority(
                description.metadata(), EvidenceScope.DESIGN_INTENT) == 0) {
            description = selectEdge(ordered, EvidenceScope.TECHNICAL_FACT, edge -> !edge.description().isBlank());
        }
        Map<String, Object> attributes = new TreeMap<>();
        Set<String> keys = ordered.stream().flatMap(edge -> edge.attributes().keySet().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        for (String key : keys) {
            Edge provider = selectEdge(ordered, EvidenceScope.TECHNICAL_FACT,
                    edge -> edge.attributes().containsKey(key));
            attributes.put(key, provider.attributes().get(key));
        }
        ElementMetadata metadata = mergeMetadata(ordered.stream().map(Edge::metadata).toList(), conflicts);
        return new Edge(ordered.getFirst().id(), technical.type(), technical.from(), technical.to(),
                description == null ? "" : description.description(), metadata, attributes);
    }

    private Node selectNode(List<Node> values, EvidenceScope scope, java.util.function.Predicate<Node> filter) {
        return values.stream().filter(filter).max(Comparator
                .comparingInt((Node node) -> confidenceEvaluator.metadataAuthority(node.metadata(), scope))
                .thenComparing(node -> node.metadata().analyzedAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GraphMerger::nodeKey)).orElse(null);
    }

    private Edge selectEdge(List<Edge> values, EvidenceScope scope, java.util.function.Predicate<Edge> filter) {
        return values.stream().filter(filter).max(Comparator
                .comparingInt((Edge edge) -> confidenceEvaluator.metadataAuthority(edge.metadata(), scope))
                .thenComparing(edge -> edge.metadata().analyzedAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GraphMerger::edgeKey)).orElse(null);
    }

    private List<Conflict> pairwiseNodeConflicts(List<Node> values, Instant detectedAt) {
        List<Conflict> result = new ArrayList<>();
        for (int left = 0; left < values.size(); left++) {
            for (int right = left + 1; right < values.size(); right++) {
                result.addAll(conflictDetector.detect(values.get(left), values.get(right), detectedAt));
            }
        }
        return result;
    }

    private List<Conflict> pairwiseEdgeConflicts(List<Edge> values, Instant detectedAt) {
        List<Conflict> result = new ArrayList<>();
        for (int left = 0; left < values.size(); left++) {
            for (int right = left + 1; right < values.size(); right++) {
                result.addAll(conflictDetector.detect(values.get(left), values.get(right), detectedAt));
            }
        }
        return result;
    }

    private ElementMetadata mergeMetadata(List<ElementMetadata> metadata, List<Conflict> newConflicts) {
        var evidence = evidenceMerger.merge(metadata.stream().map(ElementMetadata::evidence).toList());
        List<SourceLocation> locations = metadata.stream().flatMap(value -> value.sourceLocations().stream())
                .distinct().sorted().toList();
        List<Conflict> conflicts = new ArrayList<>(metadata.stream().flatMap(value -> value.conflicts().stream()).toList());
        conflicts.addAll(newConflicts);
        List<String> warnings = metadata.stream().flatMap(value -> value.warnings().stream()).distinct().sorted().toList();
        Set<StableId> traces = metadata.stream().flatMap(value -> value.relatedTraces().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> scenarios = metadata.stream().flatMap(value -> value.relatedScenarios().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        String commit = metadata.stream().map(ElementMetadata::targetCommit).filter(value -> !value.isBlank())
                .max(String::compareTo).orElse("");
        Instant analyzedAt = metadata.stream().map(ElementMetadata::analyzedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        String adapters = metadata.stream().map(ElementMetadata::adapter).filter(value -> !value.isBlank())
                .flatMap(value -> List.of(value.split(",")).stream()).map(String::strip).distinct().sorted()
                .collect(Collectors.joining(","));
        ReviewState review = mergeReviewState(metadata.stream().map(ElementMetadata::reviewState).toList());
        StaleInfo stale = metadata.stream().map(ElementMetadata::stale).filter(StaleInfo::stale)
                .max(Comparator.comparing((StaleInfo value) -> value.detectedAt(),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(StaleInfo::reason).thenComparing(StaleInfo::sourceFingerprint)
                        .thenComparing(StaleInfo::currentFingerprint).thenComparing(StaleInfo::cause))
                .orElse(StaleInfo.fresh());
        ElementMetadata provisional = new ElementMetadata(evidence, locations, commit, analyzedAt, adapters,
                Confidence.UNKNOWN, review, stale, conflicts, warnings, traces, scenarios);
        return new ElementMetadata(evidence, locations, commit, analyzedAt, adapters,
                confidenceEvaluator.evaluate(provisional), review, stale, conflicts, warnings, traces, scenarios);
    }

    private ReviewState mergeReviewState(List<ReviewState> states) {
        Set<ReviewState> unique = Set.copyOf(states);
        if (unique.contains(ReviewState.NEEDS_REVIEW)) return ReviewState.NEEDS_REVIEW;
        if (unique.contains(ReviewState.REJECTED)) {
            return unique.stream().anyMatch(state -> state == ReviewState.APPROVED
                    || state == ReviewState.HUMAN_REVIEWED) ? ReviewState.NEEDS_REVIEW : ReviewState.REJECTED;
        }
        if (unique.contains(ReviewState.APPROVED)) return ReviewState.APPROVED;
        if (unique.contains(ReviewState.HUMAN_REVIEWED)) return ReviewState.HUMAN_REVIEWED;
        if (unique.contains(ReviewState.AGENT_REVIEWED)) return ReviewState.AGENT_REVIEWED;
        return ReviewState.UNREVIEWED;
    }

    private static String nodeKey(Node node) {
        return node.type() + "\u0000" + node.displayName() + "\u0000" + node.description() + "\u0000"
                + node.attributes() + "\u0000" + node.metadata().adapter() + "\u0000"
                + node.metadata().evidence().stream().map(value -> value.id().value()).sorted().toList();
    }

    private static String edgeKey(Edge edge) {
        return edge.type() + "\u0000" + edge.from() + "\u0000" + edge.to() + "\u0000" + edge.description()
                + "\u0000" + edge.attributes() + "\u0000" + edge.metadata().adapter() + "\u0000"
                + edge.metadata().evidence().stream().map(value -> value.id().value()).sorted().toList();
    }
}
