package io.github.mandala.sbdp.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record RefreshPlan(
        RefreshMode requestedMode,
        RefreshMode executionMode,
        boolean fallback,
        Set<String> affectedAdapters,
        List<String> reasons
) {
    public RefreshPlan {
        affectedAdapters = affectedAdapters == null || affectedAdapters.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(affectedAdapters)));
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
