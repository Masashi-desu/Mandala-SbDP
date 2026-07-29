package io.github.mandala.sbdp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record DocumentationGraph(
        String schemaVersion,
        String projectId,
        String targetCommit,
        Instant analyzedAt,
        List<Node> nodes,
        List<Edge> edges
) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public DocumentationGraph {
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion").strip();
        projectId = Objects.requireNonNullElse(projectId, "").strip();
        targetCommit = Objects.requireNonNullElse(targetCommit, "").strip();
        nodes = Objects.requireNonNull(nodes, "nodes").stream().sorted().toList();
        edges = Objects.requireNonNull(edges, "edges").stream().sorted().toList();
        if (schemaVersion.isBlank()) throw new IllegalArgumentException("Schema version must not be blank");
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported Documentation Graph schema version: " + schemaVersion
                    + "; supported version is " + CURRENT_SCHEMA_VERSION);
        }
        if (projectId.isBlank()) throw new IllegalArgumentException("Project id must not be blank");
        ensureUnique(nodes.stream().map(Node::id).toList(), "node");
        ensureUnique(edges.stream().map(Edge::id).toList(), "edge");
    }

    public static DocumentationGraph of(String projectId, String targetCommit, Instant analyzedAt,
                                        List<Node> nodes, List<Edge> edges) {
        return new DocumentationGraph(CURRENT_SCHEMA_VERSION, projectId, targetCommit, analyzedAt, nodes, edges);
    }

    public static DocumentationGraph empty(String projectId) {
        return of(projectId, "", null, List.of(), List.of());
    }

    @JsonIgnore
    public Map<StableId, Node> nodeMap() {
        return nodes.stream().collect(Collectors.toUnmodifiableMap(Node::id, Function.identity()));
    }

    @JsonIgnore
    public Map<StableId, Edge> edgeMap() {
        return edges.stream().collect(Collectors.toUnmodifiableMap(Edge::id, Function.identity()));
    }

    public Optional<Node> node(StableId id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst();
    }

    public Optional<Edge> edge(StableId id) {
        return edges.stream().filter(edge -> edge.id().equals(id)).findFirst();
    }

    private static void ensureUnique(List<StableId> ids, String kind) {
        Map<StableId, Integer> counts = new LinkedHashMap<>();
        ids.forEach(id -> counts.merge(id, 1, Integer::sum));
        counts.entrySet().stream().filter(entry -> entry.getValue() > 1).findFirst().ifPresent(entry -> {
            throw new IllegalArgumentException("Duplicate " + kind + " id: " + entry.getKey());
        });
    }
}
