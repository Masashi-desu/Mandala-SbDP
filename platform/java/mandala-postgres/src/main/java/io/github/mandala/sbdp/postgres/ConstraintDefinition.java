package io.github.mandala.sbdp.postgres;

import java.util.List;
import java.util.Objects;

public record ConstraintDefinition(
        String name,
        ConstraintType type,
        List<String> columns,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        String definition,
        boolean deferrable,
        boolean initiallyDeferred) {
    public ConstraintDefinition {
        name = Objects.requireNonNull(name, "name");
        type = Objects.requireNonNull(type, "type");
        columns = List.copyOf(columns == null ? List.of() : columns);
        referencedSchema = Objects.requireNonNullElse(referencedSchema, "");
        referencedTable = Objects.requireNonNullElse(referencedTable, "");
        referencedColumns = List.copyOf(referencedColumns == null ? List.of() : referencedColumns);
        definition = Objects.requireNonNullElse(definition, "");
    }
}
