package io.github.mandala.sbdp.doma.sql;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record TableReference(
        String schema,
        String table,
        String alias,
        Set<CrudOperation> operations,
        boolean directTarget) {
    public TableReference {
        schema = Objects.requireNonNullElse(schema, "");
        table = Objects.requireNonNull(table, "table");
        alias = Objects.requireNonNullElse(alias, "");
        operations = operations == null || operations.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(operations));
    }

    public String qualifiedName() {
        return schema.isBlank() ? table : schema + "." + table;
    }
}
