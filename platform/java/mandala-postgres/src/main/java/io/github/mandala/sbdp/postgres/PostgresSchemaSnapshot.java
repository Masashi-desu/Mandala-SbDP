package io.github.mandala.sbdp.postgres;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PostgresSchemaSnapshot(
        String database,
        String serverVersion,
        Instant capturedAt,
        List<PostgresSchema> schemas,
        List<String> warnings) {
    public PostgresSchemaSnapshot {
        database = Objects.requireNonNullElse(database, "");
        serverVersion = Objects.requireNonNullElse(serverVersion, "");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        schemas = List.copyOf(schemas == null ? List.of() : schemas);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
