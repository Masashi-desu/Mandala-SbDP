package io.github.mandala.sbdp.model;

import java.util.List;

public record SchemaGraph(List<Node> nodes, List<Edge> edges) {
    private static final List<NodeType> TYPES = List.of(NodeType.DB_SCHEMA, NodeType.DB_TABLE, NodeType.DB_COLUMN,
            NodeType.DB_VIEW, NodeType.DB_MATERIALIZED_VIEW, NodeType.DB_FUNCTION, NodeType.DB_TRIGGER,
            NodeType.DB_POLICY);

    public SchemaGraph {
        nodes = nodes == null ? List.of() : nodes.stream().filter(node -> TYPES.contains(node.type())).sorted().toList();
        java.util.Set<StableId> ids = nodes.stream().map(Node::id).collect(java.util.stream.Collectors.toSet());
        edges = edges == null ? List.of() : edges.stream()
                .filter(edge -> ids.contains(edge.from()) && ids.contains(edge.to())).sorted().toList();
    }

    public static SchemaGraph from(DocumentationGraph graph) {
        return new SchemaGraph(graph.nodes(), graph.edges());
    }
}
