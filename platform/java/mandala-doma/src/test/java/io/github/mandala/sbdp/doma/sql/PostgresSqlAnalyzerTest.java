package io.github.mandala.sbdp.doma.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresSqlAnalyzerTest {
    private final PostgresSqlAnalyzer analyzer = new PostgresSqlAnalyzer();

    @Test
    void extractsCteJoinWhereSubqueryFunctionsTablesAndColumns() throws Exception {
        String sql = """
                with active_users as (
                    select u.id from public.users u where u.disabled = false
                )
                select p.id, upper(p.name), count(t.id)
                  from public.projects p
                  join active_users u on u.id = p.owner_id
                  left join public.tasks t on t.project_id = p.id
                 where p.name ilike 'secret-name'
                   and exists (select 1 from audit_logs a where a.project_id = p.id)
                 group by p.id, p.name
                """;

        SqlStatementAnalysis analysis = analyzer.analyze(sql).getFirst();

        assertEquals(SqlKind.SELECT, analysis.kind());
        assertTrue(analysis.ctes().contains("active_users"));
        assertTrue(analysis.hasWhere());
        assertTrue(analysis.hasSubquery());
        assertEquals(2, analysis.joins().size());
        assertTrue(analysis.functions().stream().anyMatch(function -> function.equalsIgnoreCase("upper")));
        assertEquals(analysis.functions().stream().sorted().toList(), List.copyOf(analysis.functions()));
        assertTrue(analysis.tables().stream().anyMatch(table -> table.qualifiedName().equals("public.projects")));
        assertTrue(analysis.tables().stream().anyMatch(table -> table.table().equals("audit_logs")));
        assertFalse(analysis.tables().stream().anyMatch(table -> table.table().equals("active_users")));
        assertTrue(analysis.tables().stream().allMatch(table -> table.operations().contains(CrudOperation.READ)));
        assertFalse(analysis.normalizedSql().contains("secret-name"));
        assertTrue(analysis.columns().stream().anyMatch(column -> column.column().equals("owner_id")
                && column.usage() == ColumnUsage.JOIN));
    }

    @Test
    void classifiesInsertUpdateDeleteAndTargetColumnsFromAst() throws Exception {
        String sql = """
                insert into public.projects (owner_id, name)
                values (42, 'private') returning id;
                update public.projects set deleted_at = now() where id = 42 returning deleted_at;
                delete from public.audit_logs where project_id = 42;
                """;

        List<SqlStatementAnalysis> statements = analyzer.analyze(sql);

        assertEquals(3, statements.size());
        assertEquals(CrudOperation.CREATE, statements.get(0).tables().getFirst().operations().iterator().next());
        assertTrue(statements.get(0).columns().stream().anyMatch(column -> column.column().equals("owner_id")
                && column.usage() == ColumnUsage.INSERT_TARGET));
        assertEquals(SqlKind.UPDATE, statements.get(1).kind());
        assertTrue(statements.get(1).tables().getFirst().operations().contains(CrudOperation.UPDATE));
        assertTrue(statements.get(1).columns().stream().anyMatch(column -> column.column().equals("deleted_at")
                && column.usage() == ColumnUsage.UPDATE_TARGET));
        assertTrue(statements.get(1).columns().stream().anyMatch(column -> column.column().equals("deleted_at")
                && column.usage() == ColumnUsage.RETURNING));
        assertTrue(statements.get(2).tables().getFirst().operations().contains(CrudOperation.DELETE));
        assertFalse(statements.get(0).normalizedSql().contains("private"));
    }

    @Test
    void classifiesPostgresUpsertAndEveryTruncateTarget() throws Exception {
        List<SqlStatementAnalysis> statements = analyzer.analyze("""
                insert into projects (id, name) values (1, 'one')
                on conflict (id) do update set name = excluded.name;
                truncate table tasks, audit_logs;
                """);

        TableReference upsert = statements.getFirst().tables().stream()
                .filter(TableReference::directTarget).findFirst().orElseThrow();
        assertTrue(upsert.operations().contains(CrudOperation.CREATE));
        assertTrue(upsert.operations().contains(CrudOperation.UPDATE));
        assertTrue(statements.getFirst().columns().stream().anyMatch(column -> column.column().equals("name")
                && column.usage() == ColumnUsage.UPDATE_TARGET));
        assertEquals(2, statements.get(1).tables().size());
        assertTrue(statements.get(1).tables().stream().allMatch(table -> table.directTarget()
                && table.operations().contains(CrudOperation.DELETE)));
    }
}
