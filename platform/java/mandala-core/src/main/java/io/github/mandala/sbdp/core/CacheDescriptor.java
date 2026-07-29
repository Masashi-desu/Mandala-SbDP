package io.github.mandala.sbdp.core;

import java.util.Objects;

public record CacheDescriptor(CacheKind kind, String projectId, String name) {
    public CacheDescriptor {
        kind = Objects.requireNonNull(kind, "kind");
        projectId = Objects.requireNonNull(projectId, "projectId").strip();
        name = Objects.requireNonNull(name, "name").strip();
        if (projectId.isBlank() || name.isBlank()) throw new IllegalArgumentException("Cache project and name are required");
    }
}
