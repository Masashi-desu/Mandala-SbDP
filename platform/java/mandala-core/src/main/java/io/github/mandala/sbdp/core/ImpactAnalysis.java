package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.StableId;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public record ImpactAnalysis(
        Set<StableId> direct,
        Set<StableId> transitive,
        Set<StableId> impactedFlows,
        Map<StableId, List<StableId>> paths
) {
    public ImpactAnalysis {
        direct = immutableSortedSet(direct);
        transitive = immutableSortedSet(transitive);
        impactedFlows = immutableSortedSet(impactedFlows);
        if (paths == null || paths.isEmpty()) {
            paths = Map.of();
        } else {
            Map<StableId, List<StableId>> copied = new LinkedHashMap<>();
            paths.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> copied.put(entry.getKey(), List.copyOf(entry.getValue())));
            paths = Collections.unmodifiableMap(copied);
        }
    }

    public Set<StableId> allImpacted() {
        Set<StableId> result = new TreeSet<>(direct);
        result.addAll(transitive);
        return immutableSortedSet(result);
    }

    private static Set<StableId> immutableSortedSet(Set<StableId> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
    }
}
