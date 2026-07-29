package io.github.mandala.sbdp.renderer;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.StableId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GraphNavigator {
    private final Map<StableId, Node> nodes;
    private final Map<StableId, List<Edge>> outgoing = new HashMap<>();
    private final Map<StableId, List<Edge>> incoming = new HashMap<>();

    GraphNavigator(DocumentationGraph graph) {
        nodes = graph.nodeMap();
        graph.edges().forEach(edge -> {
            outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge);
        });
        outgoing.values().forEach(list -> list.sort(Edge::compareTo));
        incoming.values().forEach(list -> list.sort(Edge::compareTo));
    }

    List<Edge> outgoing(Node node) { return outgoing.getOrDefault(node.id(), List.of()); }
    List<Edge> incoming(Node node) { return incoming.getOrDefault(node.id(), List.of()); }
    Node node(StableId id) { return nodes.get(id); }
}
