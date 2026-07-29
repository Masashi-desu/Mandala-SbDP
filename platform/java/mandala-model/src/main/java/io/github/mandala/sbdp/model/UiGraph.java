package io.github.mandala.sbdp.model;

import java.util.List;

public record UiGraph(List<Node> nodes, List<Edge> edges) {
    private static final List<NodeType> TYPES = List.of(NodeType.E2E_FLOW, NodeType.UI_ENTRY, NodeType.SCREEN,
            NodeType.SCREEN_STATE, NodeType.UI_ACTION, NodeType.SCREENSHOT, NodeType.HTTP_CLIENT_CALL);

    public UiGraph {
        nodes = nodes == null ? List.of() : nodes.stream().filter(node -> TYPES.contains(node.type())).sorted().toList();
        java.util.Set<StableId> ids = nodes.stream().map(Node::id).collect(java.util.stream.Collectors.toSet());
        edges = edges == null ? List.of() : edges.stream()
                .filter(edge -> ids.contains(edge.from()) && ids.contains(edge.to())).sorted().toList();
    }

    public static UiGraph from(DocumentationGraph graph) {
        return new UiGraph(graph.nodes(), graph.edges());
    }
}
