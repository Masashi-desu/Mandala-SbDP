package io.github.mandala.sbdp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class RefreshPlanner {
    public RefreshPlan plan(RefreshRequest request, Collection<GraphAdapter> adapters) {
        Set<String> affected = new TreeSet<>();
        adapters.stream().filter(adapter -> adapter.affectedBy(request.changes())).forEach(adapter -> affected.add(adapter.name()));
        if (request.mode() == RefreshMode.FULL) {
            adapters.forEach(adapter -> affected.add(adapter.name()));
            return new RefreshPlan(RefreshMode.FULL, RefreshMode.FULL, false, affected, List.of("Full refresh requested"));
        }
        List<String> unsafe = new ArrayList<>();
        if (request.previousGraph() == null) unsafe.add("No previous Documentation Graph is available");
        if (request.changes().categories().contains(ChangeCategory.CONFIGURATION)) {
            unsafe.add("Configuration or build inputs changed");
        }
        if (request.changes().categories().contains(ChangeCategory.UNKNOWN)) {
            unsafe.add("At least one changed file could not be safely classified");
        }
        if (request.changes().categories().contains(ChangeCategory.UNSAFE_GIT_STATE)) {
            unsafe.add("Git diff is unavailable or untracked files make the incremental baseline unsafe");
        }
        request.changes().categories().stream()
                .filter(category -> category != ChangeCategory.UNKNOWN
                        && category != ChangeCategory.CONFIGURATION
                        && category != ChangeCategory.UNSAFE_GIT_STATE)
                .filter(category -> adapters.stream().noneMatch(adapter -> adapter.changeCategories().contains(category)))
                .forEach(category -> unsafe.add("No adapter covers changed category " + category));
        adapters.stream().filter(adapter -> affected.contains(adapter.name()) && !adapter.supportsIncremental())
                .forEach(adapter -> unsafe.add("Adapter " + adapter.name() + " does not support incremental analysis"));
        if (!unsafe.isEmpty()) {
            return new RefreshPlan(RefreshMode.INCREMENTAL, RefreshMode.FULL, true,
                    adapters.stream().map(GraphAdapter::name).collect(java.util.stream.Collectors.toSet()), unsafe);
        }
        return new RefreshPlan(RefreshMode.INCREMENTAL, RefreshMode.INCREMENTAL, false, affected,
                request.changes().files().isEmpty() ? List.of("No files changed") : List.of("All affected adapters support incremental analysis"));
    }
}
