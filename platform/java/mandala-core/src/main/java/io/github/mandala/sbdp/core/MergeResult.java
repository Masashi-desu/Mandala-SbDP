package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.DocumentationGraph;

import java.util.List;
import java.util.TreeMap;

public record MergeResult(DocumentationGraph graph, List<Conflict> conflicts) {
    public MergeResult {
        if (conflicts == null || conflicts.isEmpty()) {
            conflicts = List.of();
        } else {
            TreeMap<io.github.mandala.sbdp.model.StableId, Conflict> unique = new TreeMap<>();
            conflicts.forEach(conflict -> unique.merge(conflict.id(), conflict,
                    (left, right) -> right.toString().compareTo(left.toString()) > 0 ? right : left));
            conflicts = List.copyOf(unique.values());
        }
    }
}
