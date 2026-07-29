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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErDiagramRendererTest {
    @Test
    void rendersAccessibleResponsiveHtmlWithLinkedColumnsAndRelationships() {
        Node projects = table("table:public.projects", "public.projects",
                List.of(Map.of("type", "PRIMARY_KEY", "columns", List.of("id"))));
        Node tasks = table("table:public.tasks", "public.tasks",
                List.of(
                        Map.of("type", "PRIMARY_KEY", "columns", List.of("id")),
                        Map.of(
                                "name", "tasks_project_id_fkey",
                                "type", "FOREIGN_KEY",
                                "columns", List.of("project_id"),
                                "referencedSchema", "public",
                                "referencedTable", "projects",
                                "referencedColumns", List.of("id"))));
        Node projectId = column("column:public.projects.id", "id", "bigint", 1);
        Node taskId = column("column:public.tasks.id", "id", "bigint", 1);
        Node taskProjectId = column("column:public.tasks.project_id", "project_id", "bigint", 2);
        Node taskName = column("column:public.tasks.name", "name", "character varying(120)", 3);
        Edge foreignKey = Edge.of("edge:tasks-projects", EdgeType.FK_TO, tasks.id(), projects.id());
        DocumentationGraph graph = DocumentationGraph.of(
                "sample",
                "commit",
                Instant.EPOCH,
                List.of(projects, tasks, projectId, taskId, taskProjectId, taskName),
                List.of(foreignKey));

        String html = new ErDiagramRenderer().render(graph, List.of(projects, tasks));

        assertTrue(html.startsWith("<section class=\"er-diagram\""));
        assertTrue(html.contains("data-er-diagram data-er-notation=\"idef1x\""));
        assertTrue(html.contains("data-er-notation-select"));
        assertTrue(html.contains("<option value=\"idef1x\" selected>IDEF1X</option>"));
        assertTrue(html.contains("<option value=\"ie\">IE (Crow&#39;s Foot)</option>"));
        assertTrue(html.contains("<article class=\"er-table\" data-table=\"table:public.tasks\""
                + " data-er-table=\"table:public.tasks\" data-er-identifier-dependent=\"false\">"));
        assertTrue(html.contains("<table class=\"er-column-table\">"));
        assertTrue(html.contains("data-er-column=\"column:public.tasks.project_id\""));
        assertTrue(html.contains("<a href=\"../" + PagePaths.forNode(taskProjectId) + "\">project_id</a>"));
        assertTrue(html.contains("<span class=\"er-key-badge\">FK</span>"));
        assertFalse(html.contains(PagePaths.forNode(taskName)),
                "Non-key Columns belong to the individual Table page, not the ER overview");
        assertTrue(html.contains("data-i18n=\"er.openTableColumns\""));
        assertTrue(html.contains("data-er-from-column=\"column:public.tasks.project_id\""));
        assertTrue(html.contains("data-er-to-column=\"column:public.projects.id\""));
        assertTrue(html.contains("data-er-from-cardinality=\"0..*\""));
        assertTrue(html.contains("data-er-to-cardinality=\"1\""));
        assertTrue(html.contains("data-er-identifying=\"false\""));
        assertTrue(html.contains("tasks_project_id_fkey"));
        assertTrue(html.contains("../" + PagePaths.forNode(projects)));
        assertTrue(html.contains("data-i18n-aria-label=\"er.diagram\""));
        assertTrue(html.contains("<svg class=\"er-relation-layer\" data-er-connectors"));
        assertFalse(html.contains("viewBox"),
                "The relation overlay receives a responsive viewBox from the client, not a fixed render size");
    }

    @Test
    void derivesIdef1xIdentifyingRelationshipsFromForeignKeysInTheChildPrimaryKey() {
        Node projects = table("table:public.projects", "public.projects",
                List.of(Map.of("type", "PRIMARY_KEY", "columns", List.of("id"))));
        Node projectTasks = table("table:public.project_tasks", "public.project_tasks",
                List.of(
                        Map.of("type", "PRIMARY_KEY", "columns", List.of("project_id", "sequence")),
                        Map.of(
                                "name", "project_tasks_project_id_fkey",
                                "type", "FOREIGN_KEY",
                                "columns", List.of("project_id"),
                                "referencedSchema", "public",
                                "referencedTable", "projects",
                                "referencedColumns", List.of("id"))));
        Node projectId = column("column:public.projects.id", "id", "bigint", 1);
        Node childProjectId = column(
                "column:public.project_tasks.project_id", "project_id", "bigint", 1);
        Node sequence = column("column:public.project_tasks.sequence", "sequence", "integer", 2);
        DocumentationGraph graph = DocumentationGraph.of(
                "sample",
                "commit",
                Instant.EPOCH,
                List.of(projects, projectTasks, projectId, childProjectId, sequence),
                List.of(Edge.of(
                        "edge:project-tasks-projects",
                        EdgeType.FK_TO,
                        projectTasks.id(),
                        projects.id())));

        String html = new ErDiagramRenderer().render(graph, List.of(projects, projectTasks));

        assertTrue(html.contains("data-er-table=\"table:public.project_tasks\""
                + " data-er-identifier-dependent=\"true\""));
        assertTrue(html.contains("data-er-identifying=\"true\""));
        assertTrue(html.contains("data-er-primary=\"true\" class=\"er-primary-boundary\""));
    }

    @Test
    void preservesSourceTermsAndRendersAnAccessibleEmptyState() {
        Node sourceTable = table(
                "table:public.quoted_terms",
                "原文 Table “Quoted”",
                List.of());
        DocumentationGraph graph = DocumentationGraph.of(
                "sample", "commit", Instant.EPOCH, List.of(sourceTable), List.of());

        String html = new ErDiagramRenderer().render(graph, List.of(sourceTable));
        String empty = new ErDiagramRenderer().render(graph, List.of());

        assertTrue(html.contains("原文 Table “Quoted”"));
        assertFalse(html.contains("data-i18n=\"原文 Table “Quoted”\""));
        assertTrue(empty.contains("data-i18n=\"empty.erTables\""));
    }

    @Test
    void placesAHighlyReferencedHubBetweenRelationshipCards() {
        Node users = table("table:public.users", "public.users",
                List.of(Map.of("type", "PRIMARY_KEY", "columns", List.of("id"))));
        Node auditLogs = table("table:public.audit_logs", "public.audit_logs",
                List.of(Map.of(
                        "type", "FOREIGN_KEY",
                        "columns", List.of("user_id"),
                        "referencedSchema", "public",
                        "referencedTable", "users",
                        "referencedColumns", List.of("id"))));
        Node projects = table("table:public.projects", "public.projects",
                List.of(Map.of(
                        "type", "FOREIGN_KEY",
                        "columns", List.of("owner_id"),
                        "referencedSchema", "public",
                        "referencedTable", "users",
                        "referencedColumns", List.of("id"))));
        Node userId = column("column:public.users.id", "id", "bigint", 1);
        Node auditUserId = column("column:public.audit_logs.user_id", "user_id", "bigint", 1);
        Node projectOwnerId = column("column:public.projects.owner_id", "owner_id", "bigint", 1);
        DocumentationGraph graph = DocumentationGraph.of(
                "sample", "commit", Instant.EPOCH,
                List.of(users, auditLogs, projects, userId, auditUserId, projectOwnerId),
                List.of(
                        Edge.of("edge:audit-users", EdgeType.FK_TO, auditLogs.id(), users.id()),
                        Edge.of("edge:projects-users", EdgeType.FK_TO, projects.id(), users.id())));

        String html = new ErDiagramRenderer().render(graph, List.of(users, auditLogs, projects));

        int usersPosition = html.indexOf("data-er-table=\"table:public.users\"");
        int auditPosition = html.indexOf("data-er-table=\"table:public.audit_logs\"");
        int projectsPosition = html.indexOf("data-er-table=\"table:public.projects\"");
        assertTrue(usersPosition > Math.min(auditPosition, projectsPosition));
        assertTrue(usersPosition < Math.max(auditPosition, projectsPosition));
    }

    private Node table(String id, String name, List<Map<String, Object>> constraints) {
        return Node.builder(StableId.of(id), NodeType.DB_TABLE, name)
                .attributes(Map.of("constraints", constraints))
                .build();
    }

    private Node column(String id, String name, String type, int ordinal) {
        return Node.builder(StableId.of(id), NodeType.DB_COLUMN, name)
                .attributes(Map.of("column", name, "type", type, "ordinal", ordinal))
                .build();
    }
}
