package io.github.mandala.sbdp.renderer;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDefinitionRendererTest {
    @Test
    void rendersCatalogBackedDefinitionWithColumnsConstraintsIndexesAndDatabaseObjects() {
        List<Map<String, Object>> constraints = List.of(
                Map.of(
                        "name", "projects_pkey",
                        "type", "PRIMARY_KEY",
                        "columns", List.of("id"),
                        "definition", "PRIMARY KEY (id)"),
                Map.of(
                        "name", "projects_owner_id_fkey",
                        "type", "FOREIGN_KEY",
                        "columns", List.of("owner_id"),
                        "referencedSchema", "public",
                        "referencedTable", "users",
                        "referencedColumns", List.of("id"),
                        "definition", "FOREIGN KEY (owner_id) REFERENCES public.users(id)"),
                Map.of(
                        "name", "projects_name_check",
                        "type", "CHECK",
                        "columns", List.of("name"),
                        "definition", "CHECK (length(name) > 0)"),
                Map.of(
                        "name", "projects_name_key",
                        "type", "UNIQUE",
                        "columns", List.of("name"),
                        "definition", "UNIQUE (name)"));
        List<Map<String, Object>> indexes = List.of(
                Map.of(
                        "name", "projects_pkey",
                        "columns", List.of("id"),
                        "primary", true,
                        "unique", true,
                        "accessMethod", "btree",
                        "definition", "CREATE UNIQUE INDEX projects_pkey ON public.projects USING btree (id)"),
                Map.of(
                        "name", "projects_owner_id_idx",
                        "columns", List.of("owner_id"),
                        "primary", false,
                        "unique", false,
                        "accessMethod", "btree",
                        "definition", "CREATE INDEX projects_owner_id_idx ON public.projects USING btree (owner_id)"),
                Map.of(
                        "name", "projects_name_key",
                        "columns", List.of("name"),
                        "primary", false,
                        "unique", true,
                        "accessMethod", "btree",
                        "definition", "CREATE UNIQUE INDEX projects_name_key ON public.projects USING btree (name)"));
        Node projects = Node.builder(StableId.of("table:public.projects"), NodeType.DB_TABLE, "public.projects")
                .description("Projects from PostgreSQL COMMENT")
                .attributes(Map.of(
                        "schema", "public",
                        "table", "projects",
                        "kind", "TABLE",
                        "owner", "sample_app",
                        "rowSecurityEnabled", true,
                        "constraints", constraints,
                        "indexes", indexes))
                .build();
        Node users = Node.builder(StableId.of("table:public.users"), NodeType.DB_TABLE, "public.users").build();
        Node tasks = Node.builder(StableId.of("table:public.tasks"), NodeType.DB_TABLE, "public.tasks").build();
        Node id = column("column:public.projects.id", "id", "bigint", 1, false, "", "d", "Project identifier");
        Node ownerId = column("column:public.projects.owner_id", "owner_id", "bigint", 2, false, "", "", "Project owner");
        Node name = column("column:public.projects.name", "name", "character varying(120)", 3,
                false, "'untitled'::character varying", "", "解析元のカラム説明");
        Node trigger = Node.builder(StableId.of("trigger:public.projects.audit_project"), NodeType.DB_TRIGGER, "audit_project").build();
        Node policy = Node.builder(StableId.of("policy:public.projects.project_owner"), NodeType.DB_POLICY, "project_owner").build();
        Node function = Node.builder(StableId.of("function:public.audit_project"), NodeType.DB_FUNCTION, "public.audit_project()").build();
        Node sql = Node.builder(StableId.of("sql:ProjectDao/insert.sql"), NodeType.SQL_STATEMENT, "ProjectDao/insert.sql").build();
        Node dao = Node.builder(StableId.of("dao:sample.ProjectDao"), NodeType.DOMA_DAO, "ProjectDao").build();
        Node daoMethod = Node.builder(
                StableId.of("dao:sample.ProjectDao#insert(ProjectEntity)"),
                NodeType.DOMA_DAO_METHOD,
                "ProjectDao#insert").build();
        Node service = Node.builder(
                StableId.of("java:sample.ProjectService#create"),
                NodeType.APPLICATION_SERVICE,
                "ProjectService#create").build();
        List<Edge> edges = List.of(
                Edge.of("edge:projects-trigger", EdgeType.FIRES_TRIGGER, projects.id(), trigger.id()),
                Edge.of("edge:projects-policy", EdgeType.CONTAINS, projects.id(), policy.id()),
                Edge.of("edge:trigger-function", EdgeType.CALLS_FUNCTION, trigger.id(), function.id()),
                Edge.of("edge:tasks-projects", EdgeType.FK_TO, tasks.id(), projects.id()),
                Edge.of("edge:sql-projects", EdgeType.CREATES, sql.id(), projects.id()),
                Edge.of("edge:method-sql", EdgeType.EXECUTES_SQL, daoMethod.id(), sql.id()),
                Edge.of("edge:dao-method", EdgeType.CONTAINS, dao.id(), daoMethod.id()),
                Edge.of("edge:service-method", EdgeType.CALLS, service.id(), daoMethod.id()));
        DocumentationGraph graph = DocumentationGraph.of(
                "sample",
                "commit",
                Instant.EPOCH,
                List.of(projects, users, tasks, id, ownerId, name, trigger, policy, function,
                        sql, dao, daoMethod, service),
                edges);

        String html = new TableDefinitionRenderer().render(graph, projects);

        assertTrue(html.startsWith("<section class=\"panel table-definition\">"));
        assertTrue(html.contains("<table class=\"table-definition-table\">"));
        assertTrue(html.contains("data-i18n=\"table.definition\""));
        assertTrue(html.contains("data-i18n=\"table.tableComment\""));
        assertTrue(html.contains("Projects from PostgreSQL COMMENT"));
        assertTrue(html.contains("data-i18n=\"table.tableName\""));
        assertFalse(html.contains("data-i18n=\"table.relatedE2e\""));
        assertTrue(html.contains("<th scope=\"row\"><a href=\"../" + PagePaths.forNode(ownerId) + "\">owner_id</a></th>"));
        assertTrue(html.contains("<code>character varying(120)</code>"));
        assertTrue(html.contains("NOT NULL"));
        assertTrue(html.contains("&#39;untitled&#39;::character varying"));
        assertTrue(html.contains(">PK</span>"));
        assertTrue(html.contains(">FK</span>"));
        assertTrue(html.contains(">UQ</span>"));
        assertTrue(html.contains(">UQ IDX</span>"));
        assertTrue(html.contains(">CHECK</span>"));
        assertTrue(html.contains(">IDX</span>"));
        assertTrue(html.contains(">IDENTITY</span>"));
        assertTrue(html.contains("../" + PagePaths.forNode(users)));
        assertTrue(html.contains("public.users.id"));
        assertTrue(html.contains("projects_owner_id_fkey"));
        assertTrue(html.contains("projects_owner_id_idx"));
        assertTrue(html.contains("../" + PagePaths.forNode(trigger)));
        assertTrue(html.contains("../" + PagePaths.forNode(policy)));
        assertTrue(html.contains("../" + PagePaths.forNode(function)));
        assertTrue(html.contains("../" + PagePaths.forNode(tasks)));
        assertTrue(html.contains("../" + PagePaths.forNode(sql)));
        assertTrue(html.contains("../" + PagePaths.forNode(dao)));
        assertTrue(html.contains("../" + PagePaths.forNode(daoMethod)));
        assertTrue(html.contains("../" + PagePaths.forNode(service)));
        assertTrue(html.contains("data-i18n=\"table.referencedBy\""));
        assertTrue(html.contains("data-i18n=\"table.applicationUsage\""));
        assertTrue(html.contains("data-i18n=\"table.relatedSql\""));
        assertTrue(html.contains("data-i18n=\"table.relatedDaos\""));
        assertTrue(html.contains("data-i18n=\"table.relatedServices\""));
        assertTrue(html.contains("data-i18n=\"table.enabled\""));
        assertTrue(html.contains("解析元のカラム説明"));
        assertFalse(html.contains("data-i18n=\"解析元のカラム説明\""),
                "PostgreSQL comments and source terminology must remain verbatim");
    }

    @Test
    void ignoresNonTableNodes() {
        Node column = Node.of("column:public.projects.id", NodeType.DB_COLUMN, "id");
        DocumentationGraph graph = DocumentationGraph.of(
                "sample", "commit", Instant.EPOCH, List.of(column), List.of());

        assertEquals("", new TableDefinitionRenderer().render(graph, column));
    }

    private Node column(String id, String name, String type, int ordinal, boolean nullable,
                        String defaultValue, String identity, String description) {
        return Node.builder(StableId.of(id), NodeType.DB_COLUMN, name)
                .description(description)
                .attributes(Map.of(
                        "column", name,
                        "type", type,
                        "ordinal", ordinal,
                        "nullable", nullable,
                        "default", defaultValue,
                        "identity", identity,
                        "generated", ""))
                .build();
    }
}
