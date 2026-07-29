package io.github.mandala.sbdp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record Diff(
        String baseCommit,
        String currentCommit,
        Instant createdAt,
        List<Node> addedNodes,
        List<Node> removedNodes,
        List<NodeChange> modifiedNodes,
        List<Edge> addedEdges,
        List<Edge> removedEdges,
        List<EdgeChange> modifiedEdges,
        Set<StableId> impactedNodes
) {
    public Diff {
        baseCommit = baseCommit == null ? "" : baseCommit;
        currentCommit = currentCommit == null ? "" : currentCommit;
        addedNodes = addedNodes == null ? List.of() : addedNodes.stream().sorted().toList();
        removedNodes = removedNodes == null ? List.of() : removedNodes.stream().sorted().toList();
        modifiedNodes = modifiedNodes == null ? List.of() : List.copyOf(modifiedNodes);
        addedEdges = addedEdges == null ? List.of() : addedEdges.stream().sorted().toList();
        removedEdges = removedEdges == null ? List.of() : removedEdges.stream().sorted().toList();
        modifiedEdges = modifiedEdges == null ? List.of() : List.copyOf(modifiedEdges);
        impactedNodes = impactedNodes == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(impactedNodes)));
    }

    @JsonIgnore
    public boolean isEmpty() {
        return addedNodes.isEmpty() && removedNodes.isEmpty() && modifiedNodes.isEmpty()
                && addedEdges.isEmpty() && removedEdges.isEmpty() && modifiedEdges.isEmpty();
    }
}
