package io.github.mandala.sbdp.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public record EdgeChange(StableId id, Edge before, Edge after, Set<String> changedFields) {
    public EdgeChange {
        if (id == null || before == null || after == null) {
            throw new IllegalArgumentException("Modified edge change requires id, before and after");
        }
        changedFields = changedFields == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(changedFields)));
    }
}
