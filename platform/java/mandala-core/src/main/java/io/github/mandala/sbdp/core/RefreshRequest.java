package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record RefreshRequest(
        String projectId,
        String targetCommit,
        String configurationHash,
        Path projectRoot,
        RefreshMode mode,
        ChangeSet changes,
        boolean fallbackToFull,
        DocumentationGraph previousGraph,
        Map<String, Object> configuration
) {
    public RefreshRequest {
        projectId = Objects.requireNonNull(projectId, "projectId").strip();
        targetCommit = Objects.requireNonNullElse(targetCommit, "").strip();
        configurationHash = Objects.requireNonNullElse(configurationHash, "").strip();
        projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        mode = mode == null ? RefreshMode.FULL : mode;
        changes = changes == null ? ChangeSet.empty() : changes;
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        if (projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank");
        if (mode == RefreshMode.INCREMENTAL && previousGraph == null) {
            if (!fallbackToFull) throw new IllegalArgumentException("Incremental refresh requires a previous graph");
        }
        if (previousGraph != null && !previousGraph.projectId().equals(projectId)) {
            throw new IllegalArgumentException("Previous graph project " + previousGraph.projectId()
                    + " does not match refresh project " + projectId);
        }
    }

    public static RefreshRequest full(String projectId, String commit, String configurationHash, Path root) {
        return new RefreshRequest(projectId, commit, configurationHash, root, RefreshMode.FULL, ChangeSet.empty(),
                true, null, Map.of());
    }
}
