package io.github.mandala.sbdp.postgres;

import java.util.List;
import java.util.Objects;

public record IndexDefinition(
        String name,
        boolean unique,
        boolean primary,
        String accessMethod,
        List<String> columns,
        String predicate,
        String definition) {
    public IndexDefinition {
        name = Objects.requireNonNull(name, "name");
        accessMethod = Objects.requireNonNullElse(accessMethod, "");
        columns = List.copyOf(columns == null ? List.of() : columns);
        predicate = Objects.requireNonNullElse(predicate, "");
        definition = Objects.requireNonNullElse(definition, "");
    }
}
