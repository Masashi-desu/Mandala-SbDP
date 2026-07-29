package io.github.mandala.sbdp.doma.sql;

import java.util.List;
import java.util.Objects;

public record JoinReference(
        String joinType,
        String rightItem,
        String condition,
        List<ColumnReference> columns) {
    public JoinReference {
        joinType = Objects.requireNonNullElse(joinType, "JOIN");
        rightItem = Objects.requireNonNullElse(rightItem, "");
        condition = Objects.requireNonNullElse(condition, "");
        columns = List.copyOf(columns == null ? List.of() : columns);
    }
}
