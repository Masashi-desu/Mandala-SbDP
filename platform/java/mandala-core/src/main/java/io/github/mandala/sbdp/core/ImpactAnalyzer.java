package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ImpactAnalyzer {
    public ImpactAnalysis analyze(DocumentationGraph graph, Collection<StableId> changedNodes, int maxDepth) {
        Set<StableId> direct = new TreeSet<>(changedNodes);
        BidirectionalGraphIndex index = new BidirectionalGraphIndex(graph);
        Set<StableId> all = new TreeSet<>(index.traverse(direct, TraversalDirection.BOTH, maxDepth, Set.of()));
        all.removeAll(direct);
        Set<StableId> flows = new TreeSet<>();
        Map<StableId, List<StableId>> paths = new LinkedHashMap<>();
        for (StableId source : direct) {
            if (index.node(source).map(node -> node.type() == NodeType.E2E_FLOW).orElse(false)) {
                flows.add(source);
                paths.put(source, List.of(source));
            }
        }
        for (StableId candidate : all) {
            if (index.node(candidate).map(node -> node.type() == NodeType.E2E_FLOW).orElse(false)) {
                flows.add(candidate);
                for (StableId source : direct) {
                    List<StableId> path = index.shortestPath(source, candidate, TraversalDirection.BOTH, maxDepth);
                    if (!path.isEmpty() && (!paths.containsKey(candidate) || path.size() < paths.get(candidate).size())) {
                        paths.put(candidate, path);
                    }
                }
            }
        }
        return new ImpactAnalysis(direct, all, flows, paths);
    }
}
