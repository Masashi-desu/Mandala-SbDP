package io.github.mandala.sbdp.doma.sql;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.ReturningClause;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import net.sf.jsqlparser.util.TablesNamesFinder;

/** PostgreSQL-capable AST analysis built on JSqlParser (never regex-only SQL classification). */
public final class PostgresSqlAnalyzer {
    private final SqlLiteralMasker literalMasker = new SqlLiteralMasker();

    public List<SqlStatementAnalysis> analyze(String sql) throws SqlAnalysisException {
        return analyze(sql, false);
    }

    public List<SqlStatementAnalysis> analyze(String sql, boolean dynamicTemplate) throws SqlAnalysisException {
        try {
            Statements parsed = CCJSqlParserUtil.parseStatements(sql);
            List<SqlStatementAnalysis> result = new ArrayList<>();
            int index = 0;
            for (Statement statement : parsed.getStatements()) {
                result.add(analyze(statement, index++, dynamicTemplate));
            }
            return List.copyOf(result);
        } catch (JSQLParserException | RuntimeException exception) {
            throw new SqlAnalysisException("Unable to parse PostgreSQL SQL: " + concise(exception.getMessage()), exception);
        }
    }

    private SqlStatementAnalysis analyze(Statement statement, int index, boolean dynamicTemplate) {
        SqlKind kind = kind(statement);
        AstCollector collector = new AstCollector();
        collector.collect(statement);

        List<Table> targetTables = targets(statement);
        Map<String, MutableTable> tables = new LinkedHashMap<>();
        for (Table table : collector.tables) {
            if (collector.ctes.stream().anyMatch(cte -> cte.equalsIgnoreCase(table.getUnquotedName()))
                    && blank(table.getUnquotedSchemaName())) {
                continue;
            }
            MutableTable mutable = mutable(table);
            tables.putIfAbsent(mutable.key(), mutable);
        }
        for (Table target : targetTables) {
            MutableTable mutable = mutable(target);
            tables.putIfAbsent(mutable.key(), mutable);
        }

        Set<String> targetKeys = targetTables.stream().map(this::mutable).map(MutableTable::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (MutableTable table : tables.values()) {
            boolean direct = targetKeys.contains(table.key());
            table.directTarget = direct;
            if (direct) {
                switch (kind) {
                    case INSERT -> {
                        table.operations.add(CrudOperation.CREATE);
                        if (statement instanceof Insert insert
                                && insert.getConflictAction() != null
                                && insert.getConflictAction().getConflictActionType() != null
                                && insert.getConflictAction().getConflictActionType().name().equals("DO_UPDATE")) {
                            table.operations.add(CrudOperation.UPDATE);
                        }
                    }
                    case UPDATE -> table.operations.add(CrudOperation.UPDATE);
                    case DELETE, TRUNCATE -> table.operations.add(CrudOperation.DELETE);
                    case MERGE -> table.operations.addAll(EnumSet.of(CrudOperation.CREATE, CrudOperation.UPDATE));
                    case SELECT, UNKNOWN -> table.operations.add(CrudOperation.READ);
                }
            } else if (kind != SqlKind.UNKNOWN) {
                table.operations.add(CrudOperation.READ);
            }
        }

        LinkedHashSet<ColumnReference> columns = new LinkedHashSet<>();
        collector.columns.forEach(column -> columns.add(reference(column, ColumnUsage.REFERENCED)));
        collector.whereExpressions.forEach(expression -> collectColumns(expression, ColumnUsage.WHERE, columns));
        List<JoinReference> joins = collector.joins.stream().map(join -> join(join, columns)).toList();
        addTargetColumns(statement, columns);
        addReturningColumns(statement, columns);

        List<String> warnings = new ArrayList<>();
        if (dynamicTemplate) {
            warnings.add("Doma dynamic branches were flattened for static AST analysis; runtime SQL may add references");
        }
        if (kind == SqlKind.UNKNOWN) {
            warnings.add("Statement type is outside the initial CRUD classification scope: "
                    + statement.getClass().getSimpleName());
        }
        return new SqlStatementAnalysis(
                index,
                kind,
                literalMasker.mask(statement.toString()),
                tables.values().stream().map(MutableTable::immutable).toList(),
                List.copyOf(columns),
                joins,
                collector.ctes,
                collector.functions,
                !collector.whereExpressions.isEmpty(),
                collector.parenthesedSelects > 0,
                dynamicTemplate,
                warnings);
    }

    private void addTargetColumns(Statement statement, Set<ColumnReference> columns) {
        if (statement instanceof Insert insert && insert.getColumns() != null) {
            insert.getColumns().forEach(column -> columns.add(reference(column, ColumnUsage.INSERT_TARGET)));
            if (insert.getConflictAction() != null && insert.getConflictAction().getUpdateSets() != null) {
                insert.getConflictAction().getUpdateSets().stream()
                        .filter(set -> set.getColumns() != null)
                        .flatMap(set -> set.getColumns().stream())
                        .forEach(column -> columns.add(reference(column, ColumnUsage.UPDATE_TARGET)));
            }
        } else if (statement instanceof Update update) {
            List<UpdateSet> sets = update.getUpdateSets();
            if (sets != null) {
                sets.stream().filter(set -> set.getColumns() != null).flatMap(set -> set.getColumns().stream())
                        .forEach(column -> columns.add(reference(column, ColumnUsage.UPDATE_TARGET)));
            } else if (update.getColumns() != null) {
                update.getColumns().forEach(column -> columns.add(reference(column, ColumnUsage.UPDATE_TARGET)));
            }
        }
    }

    private void addReturningColumns(Statement statement, Set<ColumnReference> columns) {
        ReturningClause returning = null;
        if (statement instanceof Insert insert) {
            returning = insert.getReturningClause();
        } else if (statement instanceof Update update) {
            returning = update.getReturningClause();
        } else if (statement instanceof Delete delete) {
            returning = delete.getReturningClause();
        }
        if (returning != null) {
            for (SelectItem<?> item : returning) {
                collectColumns(item.getExpression(), ColumnUsage.RETURNING, columns);
            }
        }
    }

    private JoinReference join(Join join, Set<ColumnReference> allColumns) {
        String type;
        if (join.isLeft()) {
            type = "LEFT";
        } else if (join.isRight()) {
            type = "RIGHT";
        } else if (join.isFull()) {
            type = "FULL";
        } else if (join.isCross()) {
            type = "CROSS";
        } else if (join.isNatural()) {
            type = "NATURAL";
        } else {
            type = "INNER";
        }
        LinkedHashSet<ColumnReference> joinColumns = new LinkedHashSet<>();
        if (join.getOnExpressions() != null) {
            join.getOnExpressions().forEach(expression -> collectColumns(expression, ColumnUsage.JOIN, joinColumns));
        }
        if (join.getUsingColumns() != null) {
            join.getUsingColumns().forEach(column -> joinColumns.add(reference(column, ColumnUsage.JOIN)));
        }
        allColumns.addAll(joinColumns);
        String condition = join.getOnExpressions() == null || join.getOnExpressions().isEmpty()
                ? ""
                : join.getOnExpressions().stream().map(Object::toString).reduce((a, b) -> a + " AND " + b).orElse("");
        return new JoinReference(type, String.valueOf(join.getRightItem()), literalMasker.mask(condition), List.copyOf(joinColumns));
    }

    private void collectColumns(Expression expression, ColumnUsage usage, Set<ColumnReference> target) {
        if (expression == null) {
            return;
        }
        expression.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Column column, S context) {
                target.add(reference(column, usage));
                return super.visit(column, context);
            }
        }, null);
    }

    private ColumnReference reference(Column column, ColumnUsage usage) {
        String qualifier = column.getTable() == null ? "" : value(column.getTable().getUnquotedName());
        return new ColumnReference(qualifier, value(column.getUnquotedColumnName()), usage);
    }

    private MutableTable mutable(Table table) {
        return new MutableTable(
                value(table.getUnquotedSchemaName()),
                value(table.getUnquotedName()),
                table.getAlias() == null ? "" : value(table.getAlias().getUnquotedName()));
    }

    private List<Table> targets(Statement statement) {
        if (statement instanceof Insert insert) {
            return insert.getTable() == null ? List.of() : List.of(insert.getTable());
        }
        if (statement instanceof Update update) {
            return update.getTable() == null ? List.of() : List.of(update.getTable());
        }
        if (statement instanceof Delete delete) {
            return delete.getTable() == null ? List.of() : List.of(delete.getTable());
        }
        if (statement instanceof Merge merge) {
            return merge.getTable() == null ? List.of() : List.of(merge.getTable());
        }
        if (statement instanceof Truncate truncate) {
            LinkedHashSet<Table> tables = new LinkedHashSet<>();
            if (truncate.getTable() != null) {
                tables.add(truncate.getTable());
            }
            if (truncate.getTables() != null) {
                tables.addAll(truncate.getTables());
            }
            return List.copyOf(tables);
        }
        return List.of();
    }

    private SqlKind kind(Statement statement) {
        if (statement instanceof Select) {
            return SqlKind.SELECT;
        }
        if (statement instanceof Insert) {
            return SqlKind.INSERT;
        }
        if (statement instanceof Update) {
            return SqlKind.UPDATE;
        }
        if (statement instanceof Delete) {
            return SqlKind.DELETE;
        }
        if (statement instanceof Merge) {
            return SqlKind.MERGE;
        }
        if (statement instanceof Truncate) {
            return SqlKind.TRUNCATE;
        }
        return SqlKind.UNKNOWN;
    }

    private String concise(String message) {
        if (message == null) {
            return "unknown parser error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static final class MutableTable {
        private final String schema;
        private final String name;
        private final String alias;
        private final EnumSet<CrudOperation> operations = EnumSet.noneOf(CrudOperation.class);
        private boolean directTarget;

        private MutableTable(String schema, String name, String alias) {
            this.schema = schema;
            this.name = name;
            this.alias = alias;
        }

        private String key() {
            return (schema + "." + name).toLowerCase(Locale.ROOT);
        }

        private TableReference immutable() {
            return new TableReference(schema, name, alias, operations, directTarget);
        }
    }

    private static final class AstCollector extends TablesNamesFinder<Void> {
        private final List<Table> tables = new ArrayList<>();
        private final List<Column> columns = new ArrayList<>();
        private final List<Join> joins = new ArrayList<>();
        private final List<Expression> whereExpressions = new ArrayList<>();
        private final Set<String> ctes = new LinkedHashSet<>();
        private final Set<String> functions = new LinkedHashSet<>();
        private int parenthesedSelects;

        private void collect(Statement statement) {
            getTables(statement);
        }

        @Override
        public <S> Void visit(Table table, S context) {
            tables.add(table);
            return super.visit(table, context);
        }

        @Override
        public <S> Void visit(Column column, S context) {
            columns.add(column);
            return super.visit(column, context);
        }

        @Override
        public <S> Void visit(Function function, S context) {
            if (function.getName() != null) {
                functions.add(function.getName());
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            if (select.getWhere() != null) {
                whereExpressions.add(select.getWhere());
            }
            if (select.getJoins() != null) {
                joins.addAll(select.getJoins());
            }
            return super.visit(select, context);
        }

        @Override
        public <S> Void visit(ParenthesedSelect select, S context) {
            parenthesedSelects++;
            return super.visit(select, context);
        }

        @Override
        public <S> Void visit(WithItem<?> item, S context) {
            if (item.getAliasName() != null) {
                ctes.add(item.getAliasName());
            }
            return super.visit(item, context);
        }

        @Override
        public <S> Void visit(Update update, S context) {
            if (update.getWhere() != null) {
                whereExpressions.add(update.getWhere());
            }
            if (update.getJoins() != null) {
                joins.addAll(update.getJoins());
            }
            return super.visit(update, context);
        }

        @Override
        public <S> Void visit(Delete delete, S context) {
            if (delete.getWhere() != null) {
                whereExpressions.add(delete.getWhere());
            }
            if (delete.getJoins() != null) {
                joins.addAll(delete.getJoins());
            }
            return super.visit(delete, context);
        }
    }
}
