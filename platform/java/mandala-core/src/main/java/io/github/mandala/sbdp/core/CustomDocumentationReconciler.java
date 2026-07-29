package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.ConflictStatus;
import io.github.mandala.sbdp.model.ConflictType;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Reconciles structured claims captured alongside custom HTML with the current generated graph. */
public final class CustomDocumentationReconciler {
    private final ConfidenceEvaluator confidenceEvaluator = new ConfidenceEvaluator();

    /**
     * Custom nodes may contain {@code references: [stable-id]} and
     * {@code assertions: {stable-id: {field: expectedValue}}}. Supported fields are type, displayName,
     * description and attributes.&lt;key&gt;.
     */
    public CustomDocumentationResult reconcile(DocumentationGraph graph, Instant detectedAt) {
        Map<StableId, Node> nodes = graph.nodeMap();
        List<Conflict> allConflicts = new ArrayList<>();
        List<Node> reconciled = graph.nodes().stream().map(custom -> {
            if (custom.type() != NodeType.CUSTOM_HTML_SECTION) return custom;
            List<Conflict> conflicts = inspect(custom, nodes, detectedAt);
            allConflicts.addAll(conflicts);
            List<Conflict> combined = new ArrayList<>(custom.metadata().conflicts().stream()
                    .filter(conflict -> conflict.type() != ConflictType.CUSTOM_DOCUMENTATION_MISMATCH).toList());
            combined.addAll(conflicts);
            ElementMetadata provisional = copy(custom.metadata(), combined, Confidence.UNKNOWN);
            return custom.toBuilder().metadata(copy(custom.metadata(), combined,
                    confidenceEvaluator.evaluate(provisional))).build();
        }).toList();
        Set<StableId> customIds = reconciled.stream().filter(node -> node.type() == NodeType.CUSTOM_HTML_SECTION)
                .map(Node::id).collect(java.util.stream.Collectors.toSet());
        List<Edge> edges = new ArrayList<>(graph.edges().stream()
                .filter(edge -> !(edge.type() == EdgeType.DOCUMENTED_BY && customIds.contains(edge.to())
                        && edge.description().equals("Generated link to retained custom documentation")))
                .toList());
        Set<String> semanticEdges = new TreeSet<>();
        edges.forEach(edge -> semanticEdges.add(edge.type() + "\u0000" + edge.from() + "\u0000" + edge.to()));
        StableIdGenerator ids = new StableIdGenerator();
        for (Node custom : reconciled) {
            if (custom.type() != NodeType.CUSTOM_HTML_SECTION) continue;
            for (String reference : references(custom)) {
                StableId targetId;
                try {
                    targetId = StableId.of(reference);
                } catch (IllegalArgumentException invalid) {
                    continue;
                }
                if (!nodes.containsKey(targetId) || targetId.equals(custom.id())) continue;
                String semantic = EdgeType.DOCUMENTED_BY + "\u0000" + targetId + "\u0000" + custom.id();
                if (!semanticEdges.add(semantic)) continue;
                ElementMetadata relationshipProvisional = new ElementMetadata(custom.metadata().evidence(),
                        custom.metadata().sourceLocations(), custom.metadata().targetCommit(),
                        custom.metadata().analyzedAt(), custom.metadata().adapter(), Confidence.UNKNOWN,
                        custom.metadata().reviewState(), custom.metadata().stale(), List.of(),
                        custom.metadata().warnings(), custom.metadata().relatedTraces(),
                        custom.metadata().relatedScenarios());
                ElementMetadata relationshipMetadata = copy(relationshipProvisional, List.of(),
                        confidenceEvaluator.evaluate(relationshipProvisional));
                edges.add(Edge.builder(ids.edge(EdgeType.DOCUMENTED_BY, targetId, custom.id()),
                                EdgeType.DOCUMENTED_BY, targetId, custom.id())
                        .description("Generated link to retained custom documentation")
                        .metadata(relationshipMetadata).build());
            }
        }
        return new CustomDocumentationResult(new DocumentationGraph(graph.schemaVersion(), graph.projectId(),
                graph.targetCommit(), graph.analyzedAt(), reconciled, edges), allConflicts);
    }

    private List<Conflict> inspect(Node custom, Map<StableId, Node> nodes, Instant detectedAt) {
        List<Conflict> conflicts = new ArrayList<>();
        Set<String> references = references(custom);
        Map<String, Map<String, Object>> assertions = assertions(custom.attributes().get("assertions"));
        references.addAll(assertions.keySet());
        for (String reference : references) {
            StableId targetId;
            try {
                targetId = StableId.of(reference);
            } catch (IllegalArgumentException invalid) {
                conflicts.add(conflict(custom, "references", "Custom documentation contains invalid stable id '"
                        + reference + "'", List.of(), detectedAt));
                continue;
            }
            Node target = nodes.get(targetId);
            if (target == null) {
                conflicts.add(conflict(custom, "references." + reference,
                        "Custom documentation references missing item " + reference, List.of(), detectedAt));
                continue;
            }
            for (Map.Entry<String, Object> assertion : assertions.getOrDefault(reference, Map.of()).entrySet()) {
                Object actual = actualValue(target, assertion.getKey());
                if (!Objects.deepEquals(assertion.getValue(), actual)) {
                    conflicts.add(conflict(custom, "assertions." + reference + "." + assertion.getKey(),
                            "Custom documentation expects '" + assertion.getValue() + "' but the graph contains '"
                                    + actual + "' for " + reference + " " + assertion.getKey(),
                            target.metadata().evidence(), detectedAt));
                }
            }
        }
        return conflicts;
    }

    private Set<String> references(Node custom) {
        Set<String> references = new TreeSet<>();
        Object rawReferences = custom.attributes().get("references");
        if (rawReferences instanceof Collection<?> collection) {
            collection.stream().map(String::valueOf).forEach(references::add);
        } else if (rawReferences instanceof String reference) {
            references.add(reference);
        }
        references.addAll(assertions(custom.attributes().get("assertions")).keySet());
        return references;
    }

    private Map<String, Map<String, Object>> assertions(Object raw) {
        if (!(raw instanceof Map<?, ?> outer)) return Map.of();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        outer.forEach((id, claims) -> {
            if (!(claims instanceof Map<?, ?> claimMap)) return;
            Map<String, Object> values = new LinkedHashMap<>();
            claimMap.forEach((field, expected) -> values.put(String.valueOf(field), expected));
            result.put(String.valueOf(id), Collections.unmodifiableMap(new LinkedHashMap<>(values)));
        });
        return Map.copyOf(result);
    }

    private Object actualValue(Node target, String field) {
        return switch (field) {
            case "type" -> target.type().name();
            case "displayName" -> target.displayName();
            case "description" -> target.description();
            default -> {
                if (!field.startsWith("attributes.")) yield ValueMarker.UNSUPPORTED_FIELD;
                String key = field.substring("attributes.".length());
                yield target.attributes().containsKey(key) ? target.attributes().get(key) : ValueMarker.MISSING_VALUE;
            }
        };
    }

    private Conflict conflict(Node custom, String field, String description, Collection<Evidence> targetEvidence,
                              Instant detectedAt) {
        List<Evidence> evidence = new EvidenceMerger().merge(custom.metadata().evidence(), List.copyOf(targetEvidence));
        String material = custom.id() + "\u0000" + field + "\u0000" + description;
        return new Conflict(StableId.of("conflict:" + StableIdGenerator.digest(material)),
                ConflictType.CUSTOM_DOCUMENTATION_MISMATCH, custom.id(), field, description, evidence,
                detectedAt, ConflictStatus.OPEN, "");
    }

    private ElementMetadata copy(ElementMetadata metadata, List<Conflict> conflicts, Confidence confidence) {
        return new ElementMetadata(metadata.evidence(), metadata.sourceLocations(), metadata.targetCommit(),
                metadata.analyzedAt(), metadata.adapter(), confidence, metadata.reviewState(), metadata.stale(),
                conflicts, metadata.warnings(), metadata.relatedTraces(), metadata.relatedScenarios());
    }

    private enum ValueMarker {
        UNSUPPORTED_FIELD,
        MISSING_VALUE
    }
}
