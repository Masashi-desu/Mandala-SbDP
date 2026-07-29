package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.StableId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public record StaleResult(DocumentationGraph graph, Set<StableId> staleIds) {
    public StaleResult {
        staleIds = staleIds == null || staleIds.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(staleIds)));
    }
}
