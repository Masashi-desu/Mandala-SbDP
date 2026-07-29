package io.github.mandala.sbdp.doma.sql;

import java.util.Objects;

public record ColumnReference(String qualifier, String column, ColumnUsage usage) {
    public ColumnReference {
        qualifier = Objects.requireNonNullElse(qualifier, "");
        column = Objects.requireNonNull(column, "column");
        usage = Objects.requireNonNull(usage, "usage");
    }
}
