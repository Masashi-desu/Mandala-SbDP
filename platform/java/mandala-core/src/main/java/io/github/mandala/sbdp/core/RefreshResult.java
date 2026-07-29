package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.StableId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record RefreshResult(
        DocumentationGraph graph,
        RefreshPlan plan,
        Diff diff,
        ImpactAnalysis impact,
        List<Conflict> conflicts,
        Set<StableId> staleIds,
        ValidationReport validation,
        List<AdapterRun> adapterRuns
) {
    public RefreshResult {
        conflicts = conflicts == null ? List.of() : conflicts.stream().distinct().sorted().toList();
        staleIds = staleIds == null || staleIds.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(staleIds)));
        adapterRuns = adapterRuns == null ? List.of() : List.copyOf(adapterRuns);
    }
}
