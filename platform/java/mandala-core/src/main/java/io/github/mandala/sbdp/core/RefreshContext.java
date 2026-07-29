package io.github.mandala.sbdp.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RefreshContext(
        String projectId,
        String targetCommit,
        String configurationHash,
        Path projectRoot,
        Instant analyzedAt,
        Map<String, Object> configuration
) {
    public RefreshContext {
        projectId = Objects.requireNonNull(projectId, "projectId").strip();
        targetCommit = Objects.requireNonNullElse(targetCommit, "").strip();
        configurationHash = Objects.requireNonNullElse(configurationHash, "").strip();
        projectRoot = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        analyzedAt = Objects.requireNonNull(analyzedAt, "analyzedAt");
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        if (projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank");
    }
}
