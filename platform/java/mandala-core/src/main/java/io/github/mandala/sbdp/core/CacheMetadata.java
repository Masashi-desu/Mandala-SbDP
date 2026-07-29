package io.github.mandala.sbdp.core;

import java.time.Instant;
import java.util.Objects;

public record CacheMetadata(
        CacheKind kind,
        String projectId,
        String name,
        String targetCommit,
        String configurationHash,
        String adapterName,
        String adapterVersion,
        Instant createdAt,
        String contentSha256
) {
    public CacheMetadata {
        kind = Objects.requireNonNull(kind, "kind");
        projectId = Objects.requireNonNull(projectId, "projectId").strip();
        name = Objects.requireNonNull(name, "name").strip();
        targetCommit = Objects.requireNonNullElse(targetCommit, "").strip();
        configurationHash = Objects.requireNonNullElse(configurationHash, "").strip();
        adapterName = Objects.requireNonNullElse(adapterName, "").strip();
        adapterVersion = Objects.requireNonNullElse(adapterVersion, "").strip();
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        contentSha256 = Objects.requireNonNull(contentSha256, "contentSha256").strip().toLowerCase();
        if (projectId.isBlank() || name.isBlank() || configurationHash.isBlank()
                || adapterName.isBlank() || adapterVersion.isBlank()) {
            throw new IllegalArgumentException("Cache identity, configuration, adapter, and version are required");
        }
        if (!contentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Cache content SHA-256 must be 64 lowercase hexadecimal characters");
        }
    }
}
