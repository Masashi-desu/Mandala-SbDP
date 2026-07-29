package io.github.mandala.sbdp.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders a conventional, source-linked table definition from PostgreSQL catalog nodes. */
final class TableDefinitionRenderer {
    private static final Set<EdgeType> USAGE_PATH = Set.of(
            EdgeType.CONTAINS,
            EdgeType.CALLS,
            EdgeType.EXECUTES,
            EdgeType.EXECUTES_SQL,
            EdgeType.READS,
            EdgeType.CREATES,
            EdgeType.UPDATES,
            EdgeType.DELETES);
    private final ObjectMapper mapper = new ObjectMapper();

    String render(DocumentationGraph graph, Node table) {
        if (table.type() != NodeType.DB_TABLE) return "";

        List<Map<?, ?>> constraints = records(table.attributes().get("constraints"));
        List<Map<?, ?>> indexes = records(table.attributes().get("indexes"));
        List<Node> columns = columns(graph, table);
        List<Node> triggers = targets(graph, table, EdgeType.FIRES_TRIGGER, Set.of(NodeType.DB_TRIGGER));
        List<Node> policies = targets(graph, table, EdgeType.CONTAINS, Set.of(NodeType.DB_POLICY));
        List<Node> functions = triggers.stream()
                .flatMap(trigger -> targets(graph, trigger, EdgeType.CALLS_FUNCTION, Set.of(NodeType.DB_FUNCTION)).stream())
                .distinct()
                .sorted()
                .toList();
        List<Node> referencedBy = sources(graph, table, EdgeType.FK_TO, Set.of(NodeType.DB_TABLE));
        List<Node> usage = usageNodes(graph, table);
        List<Node> relatedSql = usage.stream().filter(node -> node.type() == NodeType.SQL_STATEMENT).toList();
        List<Node> relatedDaos = usage.stream()
                .filter(node -> node.type() == NodeType.DOMA_DAO || node.type() == NodeType.DOMA_DAO_METHOD)
                .toList();
        List<Node> relatedServices = usage.stream()
                .filter(node -> node.type() == NodeType.APPLICATION_SERVICE)
                .toList();

        StringBuilder html = new StringBuilder(
                "<section class=\"panel table-definition\"><div class=\"section-label\">POSTGRESQL CATALOG</div>"
                        + "<div class=\"table-definition-heading\"><h2>"
                        + i18n("table.definition", "テーブル定義")
                        + "</h2><span class=\"table-definition-count\"><strong>")
                .append(columns.size())
                .append("</strong> ")
                .append(i18n("table.columns", "カラム"))
                .append("</span></div>")
                .append("<div class=\"table-definition-comment\"><strong>")
                .append(i18n("table.tableComment", "テーブルコメント"))
                .append("</strong><p>")
                .append(Html.escape(table.description().isBlank() ? "—" : table.description()))
                .append("</p></div>")
                .append("<dl class=\"table-definition-facts\">")
                .append(fact("table.schema", "Schema", value(table, "schema", "—"), true))
                .append(fact("table.tableName", "テーブル名", value(table, "table", table.displayName()), true))
                .append(fact("table.owner", "所有者", value(table, "owner", "—"), true))
                .append("<div><dt>").append(i18n("table.rls", "行レベルセキュリティ")).append("</dt><dd>")
                .append(status(booleanValue(table.attributes().get("rowSecurityEnabled"))))
                .append("</dd></div></dl>")
                .append("<div class=\"table-definition-wrap\"><table class=\"table-definition-table\"><caption class=\"visually-hidden\">")
                .append(i18n("table.definition", "テーブル定義")).append(" · ").append(Html.escape(table.displayName()))
                .append("</caption><thead><tr><th scope=\"col\">#</th><th scope=\"col\">")
                .append(i18n("table.column", "カラム名")).append("</th><th scope=\"col\">")
                .append(i18n("table.dataType", "データ型")).append("</th><th scope=\"col\">")
                .append(i18n("table.nullable", "NULL許可")).append("</th><th scope=\"col\">")
                .append(i18n("table.default", "デフォルト値")).append("</th><th scope=\"col\">")
                .append(i18n("table.keysIndexes", "キー / インデックス")).append("</th><th scope=\"col\">")
                .append(i18n("table.comment", "コメント")).append("</th></tr></thead><tbody>");
        for (Node column : columns) appendColumn(html, graph, column, constraints, indexes);
        if (columns.isEmpty()) {
            html.append("<tr><td colspan=\"7\">").append(i18n("empty.columns", "Columnはありません")).append("</td></tr>");
        }
        html.append("</tbody></table></div><div class=\"table-definition-details\">")
                .append(definitionList("table.constraints", "制約", constraints, graph, true))
                .append(definitionList("table.indexes", "インデックス", indexes, graph, false))
                .append(databaseObjects(referencedBy, triggers, policies, functions))
                .append(applicationUsage(relatedSql, relatedDaos, relatedServices))
                .append("</div></section>");
        return html.toString();
    }

    private void appendColumn(StringBuilder html, DocumentationGraph graph, Node column,
                              List<Map<?, ?>> constraints, List<Map<?, ?>> indexes) {
        String name = value(column, "column", column.displayName());
        boolean nullable = booleanValue(column.attributes().get("nullable"));
        String defaultValue = value(column, "default", "");
        String comment = column.description().isBlank() ? "—" : column.description();
        List<String> badges = badges(name, column, constraints, indexes);
        List<Reference> references = references(graph, name, constraints);

        html.append("<tr><td class=\"definition-ordinal\">")
                .append(Html.escape(column.attributes().getOrDefault("ordinal", "—")))
                .append("</td><th scope=\"row\"><a href=\"../")
                .append(Html.attribute(PagePaths.forNode(column)))
                .append("\">").append(Html.escape(name)).append("</a></th><td><code>")
                .append(Html.escape(value(column, "type", "unknown")))
                .append("</code></td><td><span class=\"definition-nullability ")
                .append(nullable ? "is-nullable" : "is-required")
                .append("\">").append(nullable ? "NULL" : "NOT NULL")
                .append("</span></td><td><code class=\"definition-default\">")
                .append(Html.escape(defaultValue.isBlank() ? "—" : defaultValue))
                .append("</code></td><td><div class=\"definition-badges\">");
        if (badges.isEmpty()) html.append("<span class=\"definition-no-value\">—</span>");
        for (String badge : badges) {
            html.append("<span class=\"definition-badge definition-badge-")
                    .append(Html.attribute(badge.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "")))
                    .append("\">").append(Html.escape(badge)).append("</span>");
        }
        html.append("</div>");
        for (Reference reference : references) {
            html.append("<a class=\"definition-reference\" href=\"../")
                    .append(Html.attribute(PagePaths.forNode(reference.table())))
                    .append("\">→ ").append(Html.escape(reference.label())).append("</a>");
        }
        html.append("</td><td class=\"definition-comment\">").append(Html.escape(comment)).append("</td></tr>");
    }

    private String definitionList(String key, String japanese, List<Map<?, ?>> records,
                                  DocumentationGraph graph, boolean constraints) {
        StringBuilder html = new StringBuilder("<section class=\"definition-detail\"><h3>")
                .append(i18n(key, japanese)).append("</h3><div class=\"definition-list\">");
        if (records.isEmpty()) {
            html.append("<div class=\"definition-no-value\">—</div>");
        }
        for (Map<?, ?> record : records) {
            String type = constraints
                    ? value(record, "type", "UNKNOWN")
                    : indexType(record);
            html.append("<article class=\"definition-item\"><header><strong>")
                    .append(Html.escape(value(record, "name", "—")))
                    .append("</strong><span class=\"definition-badge\">")
                    .append(Html.escape(type)).append("</span></header><p>")
                    .append(Html.escape(join(record.get("columns")))).append("</p>");
            if (constraints) {
                String referencedTable = value(record, "referencedTable", "");
                if (!referencedTable.isBlank()) {
                    String schema = value(record, "referencedSchema", "public");
                    Node target = graph.node(StableId.of("table:" + schema + "." + referencedTable)).orElse(null);
                    String label = schema + "." + referencedTable + " (" + join(record.get("referencedColumns")) + ")";
                    if (target == null) html.append("<span class=\"definition-reference\">→ ").append(Html.escape(label)).append("</span>");
                    else html.append("<a class=\"definition-reference\" href=\"../")
                            .append(Html.attribute(PagePaths.forNode(target))).append("\">→ ")
                            .append(Html.escape(label)).append("</a>");
                }
            }
            String definition = value(record, "definition", "");
            if (!definition.isBlank()) html.append("<code>").append(Html.escape(definition)).append("</code>");
            html.append("</article>");
        }
        return html.append("</div></section>").toString();
    }

    private String databaseObjects(List<Node> referencedBy, List<Node> triggers, List<Node> policies,
                                   List<Node> functions) {
        return "<section class=\"definition-detail definition-database-objects\"><h3>"
                + i18n("table.databaseObjects", "DBオブジェクト") + "</h3>"
                + objectGroup("table.referencedBy", "参照元テーブル", referencedBy)
                + objectGroup("table.triggers", "トリガー", triggers)
                + objectGroup("table.policies", "ポリシー", policies)
                + objectGroup("table.functions", "関数", functions)
                + "</section>";
    }

    private String applicationUsage(List<Node> sql, List<Node> daos, List<Node> services) {
        return "<section class=\"definition-detail definition-application-usage\"><h3>"
                + i18n("table.applicationUsage", "アプリケーション利用") + "</h3>"
                + objectGroup("table.relatedSql", "関連SQL", sql)
                + objectGroup("table.relatedDaos", "関連DAO", daos)
                + objectGroup("table.relatedServices", "関連Application Service", services)
                + "</section>";
    }

    private String objectGroup(String key, String japanese, List<Node> nodes) {
        String links = nodes.stream()
                .map(node -> "<a href=\"../" + Html.attribute(PagePaths.forNode(node)) + "\">"
                        + Html.escape(node.displayName()) + "</a>")
                .reduce((left, right) -> left + right)
                .orElse("<span class=\"definition-no-value\">—</span>");
        return "<div class=\"definition-object-group\"><strong>" + i18n(key, japanese) + "</strong><div>" + links + "</div></div>";
    }

    private List<String> badges(String column, Node node, List<Map<?, ?>> constraints, List<Map<?, ?>> indexes) {
        Set<String> badges = new LinkedHashSet<>();
        for (Map<?, ?> constraint : constraints) {
            if (!values(constraint.get("columns")).contains(column)) continue;
            String type = value(constraint, "type", "");
            switch (type) {
                case "PRIMARY_KEY" -> badges.add("PK");
                case "FOREIGN_KEY" -> badges.add("FK");
                case "UNIQUE" -> badges.add("UQ");
                case "CHECK" -> badges.add("CHECK");
                case "EXCLUSION" -> badges.add("EXCLUDE");
                default -> { if (!type.isBlank()) badges.add(type); }
            }
        }
        for (Map<?, ?> index : indexes) {
            if (!values(index.get("columns")).contains(column) || booleanValue(index.get("primary"))) continue;
            badges.add(booleanValue(index.get("unique")) ? "UQ IDX" : "IDX");
        }
        if (!value(node, "identity", "").isBlank()) badges.add("IDENTITY");
        if (!value(node, "generated", "").isBlank()) badges.add("GENERATED");
        return List.copyOf(badges);
    }

    private List<Reference> references(DocumentationGraph graph, String column, List<Map<?, ?>> constraints) {
        List<Reference> result = new ArrayList<>();
        for (Map<?, ?> constraint : constraints) {
            if (!"FOREIGN_KEY".equals(value(constraint, "type", ""))
                    || !values(constraint.get("columns")).contains(column)) continue;
            String schema = value(constraint, "referencedSchema", "public");
            String table = value(constraint, "referencedTable", "");
            if (table.isBlank()) continue;
            graph.node(StableId.of("table:" + schema + "." + table)).ifPresent(target ->
                    result.add(new Reference(target, schema + "." + table + "." + join(constraint.get("referencedColumns")))));
        }
        return result;
    }

    private List<Node> columns(DocumentationGraph graph, Node table) {
        String prefix = table.id().value().replace("table:", "column:") + ".";
        return graph.nodes().stream()
                .filter(node -> node.type() == NodeType.DB_COLUMN && node.id().value().startsWith(prefix))
                .sorted(Comparator.comparingInt(node -> integer(node.attributes().get("ordinal"))))
                .toList();
    }

    private List<Node> targets(DocumentationGraph graph, Node from, EdgeType edgeType, Set<NodeType> types) {
        return graph.edges().stream()
                .filter(edge -> edge.type() == edgeType && edge.from().equals(from.id()))
                .map(Edge::to)
                .map(graph::node)
                .flatMap(java.util.Optional::stream)
                .filter(node -> types.contains(node.type()))
                .distinct()
                .sorted()
                .toList();
    }

    private List<Node> sources(DocumentationGraph graph, Node to, EdgeType edgeType, Set<NodeType> types) {
        return graph.edges().stream()
                .filter(edge -> edge.type() == edgeType && edge.to().equals(to.id()))
                .map(Edge::from)
                .map(graph::node)
                .flatMap(java.util.Optional::stream)
                .filter(node -> types.contains(node.type()))
                .distinct()
                .sorted()
                .toList();
    }

    private List<Node> usageNodes(DocumentationGraph graph, Node table) {
        Map<String, List<Edge>> incoming = graph.edges().stream()
                .filter(edge -> USAGE_PATH.contains(edge.type()))
                .collect(Collectors.groupingBy(edge -> edge.to().value()));
        Set<String> seen = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(table.id().value());
        while (!queue.isEmpty() && seen.size() < 5_000) {
            String id = queue.remove();
            if (!seen.add(id)) continue;
            incoming.getOrDefault(id, List.of()).stream()
                    .map(edge -> edge.from().value())
                    .forEach(queue::add);
        }
        return seen.stream()
                .map(StableId::of)
                .map(graph::node)
                .flatMap(java.util.Optional::stream)
                .filter(node -> !node.id().equals(table.id()))
                .sorted()
                .toList();
    }

    private List<Map<?, ?>> records(Object raw) {
        if (!(raw instanceof Iterable<?> values)) return List.of();
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object value : values) {
            result.add(value instanceof Map<?, ?> map ? map : mapper.convertValue(value, Map.class));
        }
        return result;
    }

    private String fact(String key, String japanese, String value, boolean code) {
        return "<div><dt>" + i18n(key, japanese) + "</dt><dd>"
                + (code ? "<code>" + Html.escape(value) + "</code>" : Html.escape(value)) + "</dd></div>";
    }

    private String status(boolean enabled) {
        return "<span class=\"definition-status " + (enabled ? "is-enabled" : "is-disabled") + "\">"
                + i18n(enabled ? "table.enabled" : "table.disabled", enabled ? "有効" : "無効") + "</span>";
    }

    private String indexType(Map<?, ?> index) {
        if (booleanValue(index.get("primary"))) return "PRIMARY";
        if (booleanValue(index.get("unique"))) return "UNIQUE";
        return value(index, "accessMethod", "INDEX").toUpperCase();
    }

    private String value(Node node, String key, String fallback) {
        Object value = node.attributes().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private List<String> values(Object raw) {
        if (!(raw instanceof Iterable<?> values)) return List.of();
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(String.valueOf(value)));
        return result;
    }

    private String join(Object raw) {
        return String.join(", ", values(raw));
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String i18n(String key, String japanese) {
        return "<span data-i18n=\"" + Html.attribute(key) + "\">" + Html.escape(japanese) + "</span>";
    }

    private record Reference(Node table, String label) {}
}
