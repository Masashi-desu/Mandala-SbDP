package io.github.mandala.sbdp.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public record NodeChange(StableId id, Node before, Node after, Set<String> changedFields) {
    public NodeChange {
        if (id == null || before == null || after == null) {
            throw new IllegalArgumentException("Modified node change requires id, before and after");
        }
        changedFields = changedFields == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(changedFields)));
    }
}
