package io.github.mandala.sbdp.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdbcPostgresSchemaAnalyzerTest {
    @Test
    void convertsAllSupportedCatalogObjectsIntoSnapshot() throws Exception {
        CatalogQueryExecutor executor = sql -> {
            if (sql.contains("mandala:database")) {
                return rows(row("database_name", "mandala", "server_version", "17.5"));
            }
            if (sql.contains("mandala:schemas")) {
                return rows(row("schema_name", "public", "owner_name", "app", "comment", "main schema"));
            }
            if (sql.contains("mandala:relations")) {
                return rows(row(
                        "schema_name", "public", "relation_name", "projects", "relation_kind", "r",
                        "owner_name", "app", "comment", "projects table", "definition", "",
                        "parent_schema", "", "parent_table", "", "rls_enabled", true, "rls_forced", false));
            }
            if (sql.contains("mandala:columns")) {
                return rows(
                        row(
                                "schema_name", "public", "relation_name", "projects", "column_name", "id",
                                "ordinal", 1, "formatted_type", "bigint", "type_schema", "pg_catalog",
                                "type_name", "int8", "nullable", false, "default_expression", "nextval('projects_id_seq')",
                                "identity_kind", "", "generated_kind", "", "comment", "identifier"),
                        row(
                                "schema_name", "public", "relation_name", "projects", "column_name", "name",
                                "ordinal", 2, "formatted_type", "text", "type_schema", "pg_catalog",
                                "type_name", "text", "nullable", false, "default_expression", "",
                                "identity_kind", "", "generated_kind", "", "comment", "project name"));
            }
            if (sql.contains("mandala:constraints")) {
                return rows(row(
                        "schema_name", "public", "relation_name", "projects", "constraint_name", "projects_pkey",
                        "constraint_type", "p", "columns", new Object[] {"id"}, "referenced_schema", "",
                        "referenced_table", "", "referenced_columns", new Object[0], "definition", "PRIMARY KEY (id)",
                        "deferrable", false, "initially_deferred", false));
            }
            if (sql.contains("mandala:indexes")) {
                return rows(row(
                        "schema_name", "public", "relation_name", "projects", "index_name", "projects_name_idx",
                        "is_unique", true, "is_primary", false, "access_method", "btree",
                        "columns", new Object[] {"name"}, "predicate", "", "definition", "CREATE UNIQUE INDEX ..."));
            }
            if (sql.contains("mandala:triggers")) {
                return rows(row(
                        "schema_name", "public", "relation_name", "projects", "trigger_name", "audit_projects",
                        "definition", "CREATE TRIGGER ...", "function_schema", "public",
                        "function_name", "audit_project", "enabled", "O"));
            }
            if (sql.contains("mandala:policies")) {
                return rows(row(
                        "schema_name", "public", "relation_name", "projects", "policy_name", "members",
                        "command", "r", "permissive", true, "roles", new Object[] {"app_user"},
                        "using_expression", "owner_id = current_user_id()", "check_expression", ""));
            }
            if (sql.contains("mandala:sequences")) {
                return rows(row(
                        "schema_name", "public", "sequence_name", "projects_id_seq", "data_type", "bigint",
                        "start_value", 1L, "minimum_value", 1L, "maximum_value", Long.MAX_VALUE,
                        "increment", 1L, "cache_size", 1L, "cycle", false));
            }
            if (sql.contains("mandala:enums")) {
                return rows(
                        row("schema_name", "public", "enum_name", "task_status", "enum_value", "OPEN", "comment", "status"),
                        row("schema_name", "public", "enum_name", "task_status", "enum_value", "DONE", "comment", "status"));
            }
            if (sql.contains("mandala:domains")) {
                return rows(row(
                        "schema_name", "public", "domain_name", "non_empty_text", "base_type", "text",
                        "not_null", true, "default_expression", "", "checks", new Object[] {"CHECK (VALUE <> '')"},
                        "comment", "non-empty value"));
            }
            if (sql.contains("mandala:functions")) {
                return rows(row(
                        "schema_name", "public", "function_name", "audit_project", "identity_arguments", "",
                        "result_type", "trigger", "language", "plpgsql", "function_kind", "f", "volatility", "v",
                        "security_definer", false, "definition", "CREATE FUNCTION ...", "comment", "audit"));
            }
            throw new AssertionError("Unexpected catalog query: " + sql);
        };
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

        PostgresSchemaSnapshot snapshot = new JdbcPostgresSchemaAnalyzer(clock)
                .capture(executor, PostgresCaptureOptions.userSchemas());

        assertEquals("mandala", snapshot.database());
        assertEquals(Instant.parse("2026-07-22T00:00:00Z"), snapshot.capturedAt());
        PostgresSchema schema = snapshot.schemas().getFirst();
        PostgresRelation table = schema.relations().getFirst();
        assertEquals(RelationKind.TABLE, table.kind());
        assertEquals(2, table.columns().size());
        assertEquals(ConstraintType.PRIMARY_KEY, table.constraints().getFirst().type());
        assertEquals(List.of("id"), table.constraints().getFirst().columns());
        assertEquals("SELECT", table.policies().getFirst().command());
        assertEquals(List.of("OPEN", "DONE"), schema.enums().getFirst().values());
        assertEquals(1, schema.domains().size());
        assertEquals(1, schema.functions().size());
        assertTrue(snapshot.warnings().isEmpty(), () -> String.join("\n", snapshot.warnings()));
    }

    private static List<CatalogRow> rows(CatalogRow... rows) {
        return List.of(rows);
    }

    private static CatalogRow row(Object... values) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return new CatalogRow(map);
    }
}
