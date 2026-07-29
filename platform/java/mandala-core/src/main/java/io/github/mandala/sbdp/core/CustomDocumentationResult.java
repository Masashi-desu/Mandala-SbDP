package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.DocumentationGraph;

import java.util.List;

public record CustomDocumentationResult(DocumentationGraph graph, List<Conflict> conflicts) {
    public CustomDocumentationResult {
        conflicts = conflicts == null ? List.of() : conflicts.stream().distinct().sorted().toList();
    }
}
