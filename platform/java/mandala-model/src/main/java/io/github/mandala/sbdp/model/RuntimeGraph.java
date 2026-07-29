package io.github.mandala.sbdp.model;

import java.util.List;

public record RuntimeGraph(List<Node> nodes, List<Edge> edges) {
    public RuntimeGraph {
        nodes = nodes == null ? List.of() : nodes.stream()
                .filter(node -> node.type() == NodeType.TRACE || node.type() == NodeType.SPAN).sorted().toList();
        java.util.Set<StableId> ids = nodes.stream().map(Node::id).collect(java.util.stream.Collectors.toSet());
        edges = edges == null ? List.of() : edges.stream()
                .filter(edge -> ids.contains(edge.from()) && ids.contains(edge.to())).sorted().toList();
    }

    public static RuntimeGraph from(DocumentationGraph graph) {
        return new RuntimeGraph(graph.nodes(), graph.edges());
    }
}
