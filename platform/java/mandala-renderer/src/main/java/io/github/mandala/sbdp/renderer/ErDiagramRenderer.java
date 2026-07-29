package io.github.mandala.sbdp.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders responsive ER cards and the semantic data needed by the client-side relationship layer.
 *
 * <p>The ER overview deliberately previews only PK, FK, unique, and relationship-endpoint
 * columns. Complete column definitions remain on each Table page. Source-derived names and types
 * are left untranslated.</p>
 */
final class ErDiagramRenderer {
    private final ObjectMapper mapper = new ObjectMapper();

    String render(DocumentationGraph graph, List<Node> selectedTables) {
        List<Node> tables = selectedTables.stream()
                .filter(node -> node.type() == NodeType.DB_TABLE)
                .distinct()
                .sorted()
                .toList();
        if (tables.isEmpty()) {
            return "<div class=\"empty\"><span data-i18n=\"empty.erTables\">ER図に表示できるTableはありません</span></div>";
        }

        Map<String, Node> tableById = new LinkedHashMap<>();
        tables.forEach(table -> tableById.put(table.id().value(), table));
        List<RelationshipLine> relationships = relationships(graph, tableById);
        tables = orderByRelationshipImportance(tables, relationships);

        Set<String> relationshipColumns = new HashSet<>();
        Set<String> identifierDependentTables = new HashSet<>();
        relationships.forEach(relationship -> {
            if (!relationship.fromColumnId().isBlank()) relationshipColumns.add(relationship.fromColumnId());
            if (!relationship.toColumnId().isBlank()) relationshipColumns.add(relationship.toColumnId());
            if (relationship.identifying()) identifierDependentTables.add(relationship.fromTableId());
        });

        StringBuilder html = new StringBuilder(
                "<section class=\"er-diagram\" aria-label=\"ER図\" data-i18n-aria-label=\"er.diagram\""
                        + " data-er-diagram data-er-notation=\"idef1x\">"
                        + "<div class=\"er-toolbar\"><label class=\"er-notation-picker\">"
                        + "<span data-i18n=\"er.notation\">記法</span>"
                        + "<select data-er-notation-select aria-label=\"ER図の記法\""
                        + " data-i18n-aria-label=\"er.notationAria\">"
                        + "<option value=\"idef1x\" selected>IDEF1X</option>"
                        + "<option value=\"ie\">IE (Crow&#39;s Foot)</option></select></label>"
                        + "<div class=\"er-legend\">"
                        + "<span class=\"er-legend-group\" data-er-legend-notation=\"idef1x\">"
                        + "<span><i class=\"er-legend-line is-identifying\" aria-hidden=\"true\"></i>"
                        + "<span data-i18n=\"er.identifying\">識別関係</span></span>"
                        + "<span><i class=\"er-legend-line is-non-identifying\" aria-hidden=\"true\"></i>"
                        + "<span data-i18n=\"er.nonIdentifying\">非識別関係</span></span>"
                        + "<span><code>● / ◇</code> <span data-i18n=\"er.idefEndpoints\">子（既定 0..*）/ 任意の親</span></span>"
                        + "<span><code>Z / P</code> <span data-i18n=\"er.idefCardinality\">0..1 / 1..*</span></span></span>"
                        + "<span class=\"er-legend-group\" data-er-legend-notation=\"ie\">"
                        + "<span><i class=\"er-legend-line is-identifying\" aria-hidden=\"true\"></i>"
                        + "<span data-i18n=\"er.identifying\">識別関係</span></span>"
                        + "<span><i class=\"er-legend-line is-non-identifying\" aria-hidden=\"true\"></i>"
                        + "<span data-i18n=\"er.nonIdentifying\">非識別関係</span></span>"
                        + "<span><code>○|</code> <span data-i18n=\"er.optionalOne\">0件または1件</span></span>"
                        + "<span><code>||</code> <span data-i18n=\"er.exactlyOne\">必ず1件</span></span>"
                        + "<span><code>○&lt;</code> <span data-i18n=\"er.many\">0件以上</span></span></span>"
                        + "</div></div>"
                        + "<div class=\"er-canvas\" data-er-canvas>"
                        + "<svg class=\"er-relation-layer\" data-er-connectors aria-hidden=\"true\" focusable=\"false\"></svg>"
                        + "<div class=\"er-table-grid\">");
        for (Node table : tables) {
            appendTable(
                    html,
                    graph,
                    table,
                    relationshipColumns,
                    identifierDependentTables.contains(table.id().value()));
        }
        html.append("</div>");
        appendRelationshipData(html, relationships, tableById);
        return html.append("</div></section>").toString();
    }

    private List<Node> orderByRelationshipImportance(List<Node> tables, List<RelationshipLine> relationships) {
        Map<String, Integer> incoming = new HashMap<>();
        relationships.forEach(relationship -> incoming.merge(relationship.toTableId(), 1, Integer::sum));
        List<Node> ordered = new ArrayList<>(tables.stream()
                .sorted(Comparator
                        .comparingInt((Node table) -> incoming.getOrDefault(table.id().value(), 0))
                        .reversed()
                        .thenComparing(Node::compareTo))
                .toList());
        if (ordered.size() >= 3 && incoming.getOrDefault(ordered.getFirst().id().value(), 0) > 1) {
            Node hub = ordered.removeFirst();
            ordered.add(1, hub);
        }
        return List.copyOf(ordered);
    }

    private void appendTable(
            StringBuilder html,
            DocumentationGraph graph,
            Node table,
            Set<String> relationshipColumns,
            boolean identifierDependent
    ) {
        List<ColumnLine> allColumns = columns(graph, table);
        List<ColumnLine> previewColumns = allColumns.stream()
                .filter(column -> !column.marker().isBlank()
                        || relationshipColumns.contains(column.node().id().value()))
                .toList();
        String tablePage = "../" + PagePaths.forNode(table);
        html.append("<article class=\"er-table\" data-table=\"")
                .append(Html.attribute(table.id()))
                .append("\" data-er-table=\"")
                .append(Html.attribute(table.id()))
                .append("\" data-er-identifier-dependent=\"")
                .append(identifierDependent)
                .append("\"><header class=\"er-table-header\" data-er-table-anchor><h3><a href=\"")
                .append(Html.attribute(tablePage))
                .append("\">")
                .append(Html.escape(table.displayName()))
                .append("</a></h3><code>")
                .append(Html.escape(table.id()))
                .append("</code></header>");

        if (allColumns.isEmpty()) {
            html.append("<div class=\"er-table-empty\"><span data-i18n=\"empty.columns\">Columnはありません</span></div>");
        } else if (previewColumns.isEmpty()) {
            html.append("<div class=\"er-table-empty\"><span data-i18n=\"er.noRelationshipColumns\">関係キーはありません</span></div>");
        } else {
            html.append("<div class=\"er-column-scroll\"><table class=\"er-column-table\"><caption class=\"visually-hidden\">")
                    .append("<span data-i18n=\"er.keyColumns\">関係キー</span> · ")
                    .append(Html.escape(table.displayName()))
                    .append("</caption><thead><tr><th scope=\"col\"><span data-i18n=\"er.column\">Column</span></th>")
                    .append("<th scope=\"col\"><span data-i18n=\"er.keys\">Key</span></th>")
                    .append("<th scope=\"col\"><span data-i18n=\"er.dataType\">データ型</span></th></tr></thead><tbody>");
            for (int index = 0; index < previewColumns.size(); index++) {
                ColumnLine column = previewColumns.get(index);
                boolean primaryBoundary = column.primary()
                        && (index + 1 == previewColumns.size() || !previewColumns.get(index + 1).primary());
                html.append("<tr data-er-column=\"")
                        .append(Html.attribute(column.node().id()))
                        .append("\" data-er-primary=\"")
                        .append(column.primary())
                        .append(primaryBoundary ? "\" class=\"er-primary-boundary" : "")
                        .append("\"><th scope=\"row\"><a href=\"../")
                        .append(Html.attribute(PagePaths.forNode(column.node())))
                        .append("\">")
                        .append(Html.escape(column.name()))
                        .append("</a></th><td>");
                if (column.marker().isBlank()) {
                    html.append("<span class=\"er-key-badge er-key-badge-reference\">REF</span>");
                } else {
                    for (String marker : column.marker().split("/")) {
                        html.append("<span class=\"er-key-badge\">").append(Html.escape(marker)).append("</span>");
                    }
                }
                html.append("</td><td><code>")
                        .append(Html.escape(column.type()))
                        .append("</code></td></tr>");
            }
            html.append("</tbody></table></div>");
        }
        if (!allColumns.isEmpty()) {
            html.append("<footer class=\"er-table-footer\"><span class=\"er-column-count\"><span data-i18n=\"er.keyColumns\">関係キー</span> ")
                    .append(previewColumns.size())
                    .append(" / ")
                    .append(allColumns.size())
                    .append("</span><a href=\"")
                    .append(Html.attribute(tablePage))
                    .append("\"><span data-i18n=\"er.openTableColumns\">全カラムはテーブル詳細へ</span><span aria-hidden=\"true\"> →</span></a></footer>");
        }
        html.append("</article>");
    }

    private void appendRelationshipData(
            StringBuilder html,
            List<RelationshipLine> relationships,
            Map<String, Node> tableById
    ) {
        if (relationships.isEmpty()) return;
        html.append("<ol class=\"er-relationship-data visually-hidden\">");
        for (RelationshipLine relationship : relationships) {
            Node from = tableById.get(relationship.fromTableId());
            Node to = tableById.get(relationship.toTableId());
            String fromLabel = endpointLabel(from, relationship.fromColumnName());
            String toLabel = endpointLabel(to, relationship.toColumnName());
            html.append("<li data-er-relation data-er-from-table=\"")
                    .append(Html.attribute(relationship.fromTableId()))
                    .append("\" data-er-to-table=\"")
                    .append(Html.attribute(relationship.toTableId()))
                    .append("\" data-er-from-column=\"")
                    .append(Html.attribute(relationship.fromColumnId()))
                    .append("\" data-er-to-column=\"")
                    .append(Html.attribute(relationship.toColumnId()))
                    .append("\" data-er-from-cardinality=\"")
                    .append(Html.attribute(relationship.fromCardinality()))
                    .append("\" data-er-to-cardinality=\"")
                    .append(Html.attribute(relationship.toCardinality()))
                    .append("\" data-er-identifying=\"")
                    .append(relationship.identifying())
                    .append("\"><span>")
                    .append(Html.escape(fromLabel))
                    .append(" [")
                    .append(Html.escape(relationship.fromCardinality()))
                    .append("] — ")
                    .append(Html.escape(toLabel))
                    .append(" [")
                    .append(Html.escape(relationship.toCardinality()))
                    .append("] · ")
                    .append(Html.escape(relationship.name()))
                    .append("</span></li>");
        }
        html.append("</ol>");
    }

    private String endpointLabel(Node table, String column) {
        if (table == null) return column;
        return column.isBlank() ? table.displayName() : table.displayName() + "." + column;
    }

    private List<RelationshipLine> relationships(DocumentationGraph graph, Map<String, Node> tableById) {
        List<RelationshipLine> result = new ArrayList<>();
        Set<String> coveredPairs = new HashSet<>();
        for (Node source : tableById.values()) {
            Map<String, ColumnLine> sourceColumns = columnByName(graph, source);
            for (Map<?, ?> constraint : constraints(source)) {
                if (!"FOREIGN_KEY".equals(value(constraint.get("type")))) continue;
                String referencedTable = value(constraint.get("referencedTable"));
                if (referencedTable.isBlank()) continue;
                String sourceSchema = value(source.attributes().get("schema"));
                if (sourceSchema.isBlank()) sourceSchema = schemaFromTableId(source.id().value());
                String referencedSchema = value(constraint.get("referencedSchema"));
                if (referencedSchema.isBlank()) referencedSchema = sourceSchema;
                String targetId = "table:" + referencedSchema + "." + referencedTable;
                Node target = tableById.get(targetId);
                if (target == null) continue;

                List<String> localNames = stringList(constraint.get("columns"));
                List<String> referencedNames = stringList(constraint.get("referencedColumns"));
                if (referencedNames.isEmpty()) {
                    referencedNames = columns(graph, target).stream()
                            .filter(ColumnLine::primary)
                            .map(ColumnLine::name)
                            .toList();
                }
                int pairCount = Math.min(localNames.size(), referencedNames.size());
                if (pairCount == 0) continue;

                boolean unique = uniqueKey(source, localNames);
                boolean identifying = identifyingRelationship(source, localNames);
                boolean nullable = localNames.stream()
                        .map(sourceColumns::get)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(ColumnLine::nullable);
                String name = value(constraint.get("name"));
                if (name.isBlank()) name = "FK_TO";
                Map<String, ColumnLine> targetColumns = columnByName(graph, target);
                for (int index = 0; index < pairCount; index++) {
                    String localName = localNames.get(index);
                    String referencedName = referencedNames.get(index);
                    ColumnLine local = sourceColumns.get(localName);
                    ColumnLine referenced = targetColumns.get(referencedName);
                    result.add(new RelationshipLine(
                            source.id().value(),
                            target.id().value(),
                            local == null ? "" : local.node().id().value(),
                            referenced == null ? "" : referenced.node().id().value(),
                            localName,
                            referencedName,
                            unique ? "0..1" : "0..*",
                            nullable ? "0..1" : "1",
                            identifying,
                            name));
                }
                coveredPairs.add(source.id().value() + "\u0000" + target.id().value());
            }
        }

        graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.FK_TO)
                .filter(edge -> tableById.containsKey(edge.from().value())
                        && tableById.containsKey(edge.to().value()))
                .sorted(Comparator.comparing((Edge edge) -> edge.from().value())
                        .thenComparing(edge -> edge.to().value()))
                .forEach(edge -> appendFallbackRelationship(graph, edge, coveredPairs, result));
        return result.stream()
                .sorted(Comparator.comparing(RelationshipLine::fromTableId)
                        .thenComparing(RelationshipLine::toTableId)
                        .thenComparing(RelationshipLine::fromColumnName))
                .toList();
    }

    private void appendFallbackRelationship(
            DocumentationGraph graph,
            Edge edge,
            Set<String> coveredPairs,
            List<RelationshipLine> result
    ) {
        String pair = edge.from().value() + "\u0000" + edge.to().value();
        if (coveredPairs.contains(pair)) return;
        Node source = graph.node(edge.from()).orElse(null);
        Node target = graph.node(edge.to()).orElse(null);
        if (source == null || target == null) return;
        ColumnLine local = columns(graph, source).stream().filter(ColumnLine::foreign).findFirst().orElse(null);
        ColumnLine referenced = columns(graph, target).stream().filter(ColumnLine::primary).findFirst().orElse(null);
        result.add(new RelationshipLine(
                source.id().value(),
                target.id().value(),
                local == null ? "" : local.node().id().value(),
                referenced == null ? "" : referenced.node().id().value(),
                local == null ? "" : local.name(),
                referenced == null ? "" : referenced.name(),
                local != null && local.unique() ? "0..1" : "0..*",
                local != null && local.nullable() ? "0..1" : "1",
                local != null && local.primary(),
                "FK_TO"));
    }

    private boolean identifyingRelationship(Node table, List<String> foreignKeyColumns) {
        if (foreignKeyColumns.isEmpty()) return false;
        Set<String> primaryKeyColumns = new HashSet<>();
        for (Map<?, ?> constraint : constraints(table)) {
            if ("PRIMARY_KEY".equals(value(constraint.get("type")))) {
                primaryKeyColumns.addAll(stringList(constraint.get("columns")));
            }
        }
        return primaryKeyColumns.containsAll(foreignKeyColumns);
    }

    private boolean uniqueKey(Node table, List<String> names) {
        Set<String> expected = new LinkedHashSet<>(names);
        for (Map<?, ?> constraint : constraints(table)) {
            String type = value(constraint.get("type"));
            if (("PRIMARY_KEY".equals(type) || "UNIQUE".equals(type))
                    && new LinkedHashSet<>(stringList(constraint.get("columns"))).equals(expected)) {
                return true;
            }
        }
        Object rawIndexes = table.attributes().get("indexes");
        if (rawIndexes instanceof Iterable<?> indexes) {
            for (Object rawIndex : indexes) {
                Map<?, ?> index = asMap(rawIndex);
                if (Boolean.parseBoolean(value(index.get("unique")))
                        && new LinkedHashSet<>(stringList(index.get("columns"))).equals(expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, ColumnLine> columnByName(DocumentationGraph graph, Node table) {
        Map<String, ColumnLine> result = new LinkedHashMap<>();
        columns(graph, table).forEach(column -> result.put(column.name(), column));
        return result;
    }

    private List<ColumnLine> columns(DocumentationGraph graph, Node table) {
        Set<String> primary = new HashSet<>();
        Set<String> foreign = new HashSet<>();
        Set<String> unique = new HashSet<>();
        for (Map<?, ?> constraint : constraints(table)) {
            String type = value(constraint.get("type"));
            List<String> names = stringList(constraint.get("columns"));
            if ("PRIMARY_KEY".equals(type)) primary.addAll(names);
            if ("FOREIGN_KEY".equals(type)) foreign.addAll(names);
            if ("UNIQUE".equals(type)) unique.addAll(names);
        }

        String prefix = table.id().value().replace("table:", "column:") + ".";
        List<ColumnLine> result = new ArrayList<>();
        graph.nodes().stream()
                .filter(node -> node.type() == NodeType.DB_COLUMN && node.id().value().startsWith(prefix))
                .forEach(column -> {
                    String name = String.valueOf(column.attributes().getOrDefault("column", column.displayName()));
                    boolean isPrimary = primary.contains(name);
                    boolean isForeign = foreign.contains(name);
                    boolean isUnique = unique.contains(name) && !isPrimary;
                    List<String> markers = new ArrayList<>();
                    if (isPrimary) markers.add("PK");
                    if (isForeign) markers.add("FK");
                    if (isUnique) markers.add("UQ");
                    result.add(new ColumnLine(
                            column,
                            name,
                            String.valueOf(column.attributes().getOrDefault("type", "unknown")),
                            String.join("/", markers),
                            integer(column.attributes().get("ordinal")),
                            isPrimary,
                            isForeign,
                            isUnique || isPrimary,
                            Boolean.TRUE.equals(column.attributes().get("nullable"))));
                });
        result.sort(Comparator.comparing((ColumnLine line) -> line.marker().isBlank())
                .thenComparingInt(ColumnLine::ordinal)
                .thenComparing(ColumnLine::name));
        return result;
    }

    private List<Map<?, ?>> constraints(Node table) {
        Object rawConstraints = table.attributes().get("constraints");
        if (!(rawConstraints instanceof Iterable<?> values)) return List.of();
        List<Map<?, ?>> result = new ArrayList<>();
        values.forEach(value -> result.add(asMap(value)));
        return result;
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : mapper.convertValue(value, Map.class);
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof Collection<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private String value(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private String schemaFromTableId(String id) {
        String qualifiedName = id.startsWith("table:") ? id.substring("table:".length()) : id;
        int separator = qualifiedName.indexOf('.');
        return separator < 0 ? "" : qualifiedName.substring(0, separator);
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
    }

    private record ColumnLine(
            Node node,
            String name,
            String type,
            String marker,
            int ordinal,
            boolean primary,
            boolean foreign,
            boolean unique,
            boolean nullable
    ) {}

    private record RelationshipLine(
            String fromTableId,
            String toTableId,
            String fromColumnId,
            String toColumnId,
            String fromColumnName,
            String toColumnName,
            String fromCardinality,
            String toCardinality,
            boolean identifying,
            String name
    ) {}
}
