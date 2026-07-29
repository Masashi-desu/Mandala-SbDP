package io.github.mandala.sbdp.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PostgresIntegrationTest {
    @Test
    void capturesObjectsFromARealPostgresCatalog() throws Exception {
        String url = System.getenv("MANDALA_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "MANDALA_TEST_POSTGRES_URL is not configured");
        String user = System.getenv().getOrDefault("MANDALA_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("MANDALA_TEST_POSTGRES_PASSWORD", "postgres");
        String schemaName = "mandala_it_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schemaName);
            try {
                statement.execute("CREATE TYPE " + schemaName + ".task_status AS ENUM ('OPEN', 'DONE')");
                statement.execute("CREATE DOMAIN " + schemaName + ".non_empty_text AS text CHECK (VALUE <> '')");
                statement.execute("CREATE TABLE " + schemaName
                        + ".projects (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name " + schemaName
                        + ".non_empty_text NOT NULL UNIQUE, status " + schemaName + ".task_status NOT NULL)");
                statement.execute("CREATE TABLE " + schemaName
                        + ".tasks (id bigint PRIMARY KEY, project_id bigint NOT NULL REFERENCES " + schemaName
                        + ".projects(id), title text CHECK (length(title) > 0))");
                statement.execute("COMMENT ON TABLE " + schemaName + ".projects IS 'Projects'");
                statement.execute("COMMENT ON COLUMN " + schemaName + ".projects.name IS 'Project name'");
                statement.execute("CREATE INDEX tasks_project_idx ON " + schemaName + ".tasks(project_id)");
                statement.execute("CREATE VIEW " + schemaName
                        + ".open_projects AS SELECT id, name FROM " + schemaName + ".projects WHERE status = 'OPEN'");
                statement.execute("CREATE MATERIALIZED VIEW " + schemaName
                        + ".project_counts AS SELECT project_id, count(*) AS task_count FROM " + schemaName
                        + ".tasks GROUP BY project_id");
                statement.execute("CREATE FUNCTION " + schemaName + ".touch_project() RETURNS trigger LANGUAGE plpgsql AS $$"
                        + " BEGIN NEW.name = NEW.name; RETURN NEW; END $$");
                statement.execute("CREATE TRIGGER touch_project BEFORE UPDATE ON " + schemaName
                        + ".projects FOR EACH ROW EXECUTE FUNCTION " + schemaName + ".touch_project()");
                statement.execute("ALTER TABLE " + schemaName + ".projects ENABLE ROW LEVEL SECURITY");
                statement.execute("CREATE POLICY visible_projects ON " + schemaName
                        + ".projects FOR SELECT USING (id > 0)");

                PostgresSchemaSnapshot snapshot = new JdbcPostgresSchemaAnalyzer().capture(
                        connection, new PostgresCaptureOptions(Set.of(schemaName), Set.of()));

                assertEquals(1, snapshot.schemas().size());
                PostgresSchema schema = snapshot.schemas().getFirst();
                assertTrue(schema.relations().stream().anyMatch(relation -> relation.name().equals("projects")
                        && relation.columns().size() == 3
                        && relation.rowSecurityEnabled()
                        && !relation.policies().isEmpty()
                        && !relation.triggers().isEmpty()));
                assertTrue(schema.relations().stream().anyMatch(relation -> relation.kind() == RelationKind.VIEW));
                assertTrue(schema.relations().stream()
                        .anyMatch(relation -> relation.kind() == RelationKind.MATERIALIZED_VIEW));
                assertTrue(schema.enums().stream().anyMatch(value -> value.name().equals("task_status")));
                assertTrue(schema.domains().stream().anyMatch(value -> value.name().equals("non_empty_text")));
                assertTrue(schema.functions().stream().anyMatch(value -> value.name().equals("touch_project")));
            } finally {
                statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
            }
        }
    }
}
