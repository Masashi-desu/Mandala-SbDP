package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.StableId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Provides reverse lookup without materializing duplicate reverse edges. */
public final class BidirectionalGraphIndex {
    private final Map<StableId, Node> nodes;
    private final Map<StableId, List<Edge>> outgoing;
    private final Map<StableId, List<Edge>> incoming;

    public BidirectionalGraphIndex(DocumentationGraph graph) {
        nodes = new TreeMap<>(graph.nodeMap());
        Map<StableId, List<Edge>> outgoingMutable = new TreeMap<>();
        Map<StableId, List<Edge>> incomingMutable = new TreeMap<>();
        graph.edges().forEach(edge -> {
            outgoingMutable.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            incomingMutable.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge);
        });
        Comparator<Edge> comparator = Comparator.comparing(Edge::type).thenComparing(Edge::id);
        outgoing = immutableLists(outgoingMutable, comparator);
        incoming = immutableLists(incomingMutable, comparator);
    }

    public Optional<Node> node(StableId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public List<Edge> outgoing(StableId id) {
        return outgoing.getOrDefault(id, List.of());
    }

    public List<Edge> outgoing(StableId id, EdgeType type) {
        return outgoing(id).stream().filter(edge -> edge.type() == type).toList();
    }

    public List<Edge> incoming(StableId id) {
        return incoming.getOrDefault(id, List.of());
    }

    public List<Edge> incoming(StableId id, EdgeType type) {
        return incoming(id).stream().filter(edge -> edge.type() == type).toList();
    }

    public List<Edge> edgesBetween(StableId first, StableId second) {
        return outgoing(first).stream().filter(edge -> edge.to().equals(second)).toList();
    }

    public List<Node> successors(StableId id) {
        return outgoing(id).stream().map(Edge::to).distinct().map(nodes::get).filter(java.util.Objects::nonNull).toList();
    }

    public List<Node> predecessors(StableId id) {
        return incoming(id).stream().map(Edge::from).distinct().map(nodes::get).filter(java.util.Objects::nonNull).toList();
    }

    public Set<StableId> traverse(Collection<StableId> starts, TraversalDirection direction, int maxDepth,
                                  Set<EdgeType> edgeTypes) {
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must not be negative");
        Set<EdgeType> allowed = edgeTypes == null || edgeTypes.isEmpty()
                ? EnumSet.allOf(EdgeType.class) : EnumSet.copyOf(edgeTypes);
        LinkedHashSet<StableId> visited = new LinkedHashSet<>();
        Deque<Visit> queue = new ArrayDeque<>();
        starts.stream().sorted().forEach(id -> {
            if (visited.add(id)) queue.addLast(new Visit(id, 0));
        });
        while (!queue.isEmpty()) {
            Visit current = queue.removeFirst();
            if (current.depth() >= maxDepth) continue;
            adjacent(current.id(), direction).stream().filter(edge -> allowed.contains(edge.type()))
                    .map(edge -> neighbor(edge, current.id(), direction)).distinct().sorted()
                    .forEach(next -> {
                        if (visited.add(next)) queue.addLast(new Visit(next, current.depth() + 1));
                    });
        }
        return Set.copyOf(visited);
    }

    public List<StableId> shortestPath(StableId from, StableId to, TraversalDirection direction, int maxDepth) {
        if (from.equals(to)) return List.of(from);
        Deque<StableId> queue = new ArrayDeque<>();
        Map<StableId, StableId> parent = new LinkedHashMap<>();
        Map<StableId, Integer> depth = new LinkedHashMap<>();
        queue.add(from);
        depth.put(from, 0);
        while (!queue.isEmpty()) {
            StableId current = queue.removeFirst();
            int currentDepth = depth.get(current);
            if (currentDepth >= maxDepth) continue;
            List<StableId> neighbors = adjacent(current, direction).stream()
                    .map(edge -> neighbor(edge, current, direction)).distinct().sorted().toList();
            for (StableId next : neighbors) {
                if (depth.containsKey(next)) continue;
                parent.put(next, current);
                depth.put(next, currentDepth + 1);
                if (next.equals(to)) return rebuildPath(parent, from, to);
                queue.addLast(next);
            }
        }
        return List.of();
    }

    private List<Edge> adjacent(StableId id, TraversalDirection direction) {
        return switch (direction) {
            case OUTGOING -> outgoing(id);
            case INCOMING -> incoming(id);
            case BOTH -> {
                List<Edge> edges = new ArrayList<>(outgoing(id));
                edges.addAll(incoming(id));
                edges.sort(Comparator.comparing(Edge::id));
                yield edges;
            }
        };
    }

    private StableId neighbor(Edge edge, StableId current, TraversalDirection direction) {
        return switch (direction) {
            case OUTGOING -> edge.to();
            case INCOMING -> edge.from();
            case BOTH -> edge.from().equals(current) ? edge.to() : edge.from();
        };
    }

    private static List<StableId> rebuildPath(Map<StableId, StableId> parent, StableId from, StableId to) {
        Deque<StableId> path = new ArrayDeque<>();
        StableId current = to;
        path.addFirst(current);
        while (!current.equals(from)) {
            current = parent.get(current);
            if (current == null) return List.of();
            path.addFirst(current);
        }
        return List.copyOf(path);
    }

    private static Map<StableId, List<Edge>> immutableLists(Map<StableId, List<Edge>> source,
                                                             Comparator<Edge> comparator) {
        Map<StableId, List<Edge>> result = new TreeMap<>();
        source.forEach((id, edges) -> result.put(id, edges.stream().sorted(comparator).toList()));
        return Map.copyOf(result);
    }

    private record Visit(StableId id, int depth) {
    }
}
