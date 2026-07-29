package io.github.mandala.sbdp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record Conflict(
        StableId id,
        ConflictType type,
        StableId subjectId,
        String field,
        String description,
        List<Evidence> evidence,
        Instant detectedAt,
        ConflictStatus status,
        String resolution
) implements Comparable<Conflict> {
    public Conflict {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        subjectId = Objects.requireNonNull(subjectId, "subjectId");
        field = Objects.requireNonNullElse(field, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
        evidence = ElementMetadata.normalizeEvidence(evidence);
        status = status == null ? ConflictStatus.OPEN : status;
        resolution = Objects.requireNonNullElse(resolution, "").strip();
        if (description.isBlank()) throw new IllegalArgumentException("Conflict description must not be blank");
        if (status == ConflictStatus.RESOLVED && resolution.isBlank()) {
            throw new IllegalArgumentException("A resolved conflict requires a resolution");
        }
    }

    @JsonIgnore
    public boolean isOpen() {
        return status == ConflictStatus.OPEN;
    }

    @Override
    public int compareTo(Conflict other) {
        return id.compareTo(other.id);
    }
}
