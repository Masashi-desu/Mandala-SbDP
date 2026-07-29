package io.github.mandala.sbdp.doma.sql;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record SqlStatementAnalysis(
        int statementIndex,
        SqlKind kind,
        String normalizedSql,
        List<TableReference> tables,
        List<ColumnReference> columns,
        List<JoinReference> joins,
        Set<String> ctes,
        Set<String> functions,
        boolean hasWhere,
        boolean hasSubquery,
        boolean dynamicTemplate,
        List<String> warnings) {
    public SqlStatementAnalysis {
        if (statementIndex < 0) {
            throw new IllegalArgumentException("statementIndex must not be negative");
        }
        normalizedSql = normalizedSql == null ? "" : normalizedSql;
        tables = List.copyOf(tables == null ? List.of() : tables);
        columns = List.copyOf(columns == null ? List.of() : columns);
        joins = List.copyOf(joins == null ? List.of() : joins);
        ctes = immutableSortedSet(ctes);
        functions = immutableSortedSet(functions);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    private static Set<String> immutableSortedSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
    }
}
