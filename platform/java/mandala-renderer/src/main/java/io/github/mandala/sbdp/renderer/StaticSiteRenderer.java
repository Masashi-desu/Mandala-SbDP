package io.github.mandala.sbdp.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class StaticSiteRenderer {
    private static final String GENERATED_MANIFEST = ".mandala-generated-files";
    private static final Set<EdgeType> CRUD = Set.of(EdgeType.CREATES, EdgeType.READS, EdgeType.UPDATES, EdgeType.DELETES);
    private static final Set<EdgeType> EXECUTION_PATH = Set.of(
            EdgeType.CONTAINS, EdgeType.HAS_STATE, EdgeType.HAS_ACTION, EdgeType.PERFORMED_ON,
            EdgeType.TRANSITIONS_TO, EdgeType.NAVIGATES_TO,
            EdgeType.CAPTURED_AS, EdgeType.CALLS_HTTP, EdgeType.MATCHES_OPERATION, EdgeType.ROUTES_TO,
            EdgeType.ACCEPTS, EdgeType.RETURNS, EdgeType.CALLS, EdgeType.EXECUTES, EdgeType.EXECUTES_SQL,
            EdgeType.READS, EdgeType.CREATES, EdgeType.UPDATES, EdgeType.DELETES,
            EdgeType.FIRES_TRIGGER, EdgeType.CALLS_FUNCTION);
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public RenderResult render(DocumentationGraph graph, Path output, Path customRoot, RenderOptions options) throws IOException {
        return render(graph, output, customRoot, options, null);
    }

    public RenderResult render(DocumentationGraph graph, Path output, Path customRoot, RenderOptions options,
                               Diff diff) throws IOException {
        return render(graph, output, customRoot, options, diff, null);
    }

    /** Renders a self-contained site, copying referenced screenshot assets from the repository root. */
    public RenderResult render(DocumentationGraph graph, Path output, Path customRoot, RenderOptions options,
                               Diff diff, Path repositoryRoot) throws IOException {
        ManagedOutput managedOutput = prepareManagedOutput(output);
        Files.createDirectories(output);
        for (String directory : Set.of("e2e", "screens", "endpoints", "symbols", "daos", "sql", "tables", "traces", "custom", "er", "crud", "reports", "assets", "screenshots")) {
            Files.createDirectories(output.resolve(directory));
        }
        copyScreenshotAssets(graph, output, repositoryRoot);
        GraphNavigator navigator = new GraphNavigator(graph);
        CustomHtmlIntegrator custom = new CustomHtmlIntegrator(customRoot, options.allowCustomJavaScript(), graph);
        Map<String, String> paths = graph.nodes().stream().collect(Collectors.toMap(node -> node.id().value(), PagePaths::forNode));
        for (Node node : graph.nodes()) write(output.resolve(PagePaths.forNode(node)), nodePage(graph, node, navigator, custom.sectionsFor(node), options));

        for (String directory : List.of("e2e", "screens", "endpoints", "symbols", "daos", "sql", "tables", "traces", "custom")) {
            write(output.resolve(directory + "/index.html"), collectionIndex(graph, directory, options));
        }

        write(output.resolve("index.html"), home(graph, options, diff));
        write(output.resolve("screens/transitions.html"), screenTransitionsPage(graph, options));
        write(output.resolve("crud/index.html"), crudMatrix(graph, options));
        write(output.resolve("reports/evidence.html"), evidenceReport(graph, options));
        write(output.resolve("reports/stale.html"), stateReport(graph, options, true));
        write(output.resolve("reports/conflicts.html"), stateReport(graph, options, false));
        write(output.resolve("reports/diff.html"), diffReport(graph, diff, options));
        write(output.resolve("er/index.html"), erPage(graph, options));
        write(output.resolve("assets/mandala.css"), stylesheet());
        write(output.resolve("assets/custom.css"), custom.stylesheet());
        write(output.resolve("assets/mandala.js"), javascript());
        write(output.resolve("assets/favicon.svg"), favicon());
        mapper.writeValue(output.resolve("search-index.json").toFile(), graph.nodes().stream().map(node -> Map.of(
                "id", node.id().value(), "type", node.type().name(), "title", node.displayName(), "description", node.description(), "url", PagePaths.forNode(node))).toList());
        mapper.writeValue(output.resolve("page-map.json").toFile(), new TreeMap<>(paths));

        pruneStaleGeneratedFiles(graph, output, managedOutput);
        List<String> links = new LinkVerifier().verify(output);
        return new RenderResult(graph.nodes().size() + 17, links);
    }

    /**
     * Removes files emitted by an older graph after the new site has been written successfully.
     * The output directory is a generated artifact boundary; custom source content lives outside it.
     */
    private void pruneStaleGeneratedFiles(DocumentationGraph graph, Path output,
                                          ManagedOutput managedOutput) throws IOException {
        Set<Path> expected = new LinkedHashSet<>();
        graph.nodes().stream().map(PagePaths::forNode).map(Path::of).forEach(expected::add);
        for (String directory : List.of("e2e", "screens", "endpoints", "symbols", "daos", "sql", "tables", "traces", "custom")) {
            expected.add(Path.of(directory, "index.html"));
        }
        for (String path : List.of(
                "index.html", "screens/transitions.html", "crud/index.html", "reports/evidence.html", "reports/stale.html",
                "reports/conflicts.html", "reports/diff.html", "er/index.html",
                "assets/mandala.css", "assets/custom.css", "assets/mandala.js", "assets/favicon.svg",
                "search-index.json", "page-map.json")) {
            expected.add(Path.of(path));
        }
        expected.add(Path.of(GENERATED_MANIFEST));
        for (Node node : graph.nodes()) {
            if (node.type() != NodeType.SCREENSHOT) continue;
            String raw = String.valueOf(node.attributes().getOrDefault("path", ""));
            if (raw.isBlank()) continue;
            try {
                expected.add(Path.of("screenshots").resolve(Path.of(raw).getFileName()));
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid screenshot path for " + node.id() + ": " + raw, invalid);
            }
        }

        Set<Path> previous = new LinkedHashSet<>();
        Path manifest = output.resolve(GENERATED_MANIFEST);
        if (Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                Path relative;
                try { relative = Path.of(line).normalize(); }
                catch (RuntimeException invalid) { throw new IOException("Invalid generated-file manifest entry: " + line, invalid); }
                if (relative.isAbsolute() || relative.startsWith("..")) {
                    throw new IOException("Generated-file manifest path escapes output: " + line);
                }
                previous.add(relative);
            }
        } else if (managedOutput.legacy()) {
            try (var files = Files.walk(output)) {
                files.filter(path -> !path.equals(output))
                        .filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                        .map(output::relativize).map(Path::normalize).forEach(previous::add);
            }
        }
        for (Path relative : previous.stream().filter(path -> !expected.contains(path))
                .sorted(Comparator.reverseOrder()).toList()) {
            Path stale = output.resolve(relative).normalize();
            if (!stale.startsWith(output.normalize())) throw new IOException("Refusing to prune outside generated output: " + relative);
            Files.deleteIfExists(stale);
        }
        String entries = expected.stream().filter(path -> !path.equals(Path.of(GENERATED_MANIFEST)))
                .map(path -> path.toString().replace('\\', '/')).sorted().collect(Collectors.joining("\n"));
        Files.writeString(manifest, entries + "\n", StandardCharsets.UTF_8);
    }

    private ManagedOutput prepareManagedOutput(Path output) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        if (normalized.getParent() == null) throw new IOException("Refusing to render into a filesystem root: " + output);
        if (Files.isSymbolicLink(normalized)) throw new IOException("Generated site output may not be a symbolic link: " + output);
        if (!Files.exists(normalized)) return new ManagedOutput(false);
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generated site output is not a directory: " + output);
        }
        boolean empty;
        try (var entries = Files.list(normalized)) { empty = entries.findAny().isEmpty(); }
        if (empty || Files.isRegularFile(normalized.resolve(GENERATED_MANIFEST), LinkOption.NOFOLLOW_LINKS)) {
            return new ManagedOutput(false);
        }
        boolean legacy = Files.isRegularFile(normalized.resolve("index.html"))
                && Files.isRegularFile(normalized.resolve("page-map.json"))
                && Files.isRegularFile(normalized.resolve("assets/mandala.css"));
        if (!legacy) {
            throw new IOException("Refusing to render into a non-empty directory not managed by Mandala: " + output);
        }
        return new ManagedOutput(true);
    }

    private String home(DocumentationGraph graph, RenderOptions options, Diff diff) {
        Map<NodeType, Long> counts = graph.nodes().stream().collect(Collectors.groupingBy(Node::type, () -> new EnumMap<>(NodeType.class), Collectors.counting()));
        long stale = graph.nodes().stream().filter(node -> node.metadata().stale().stale()).count();
        long conflicts = graph.nodes().stream().filter(node -> node.metadata().conflicted()).count();
        long warnings = graph.nodes().stream().mapToLong(node -> node.metadata().warnings().size()).sum();
        String metrics = metric("metrics.e2e", "E2Eフロー", counts.getOrDefault(NodeType.E2E_FLOW, 0L), "e2e/index.html")
                + metric("metrics.screens", "画面", counts.getOrDefault(NodeType.SCREEN, 0L), "screens/index.html")
                + metric("metrics.endpoints", "Endpoint", counts.getOrDefault(NodeType.HTTP_ENDPOINT, 0L), "endpoints/index.html")
                + metric("metrics.symbols", "Javaシンボル", counts.getOrDefault(NodeType.JAVA_CLASS, 0L) + counts.getOrDefault(NodeType.JAVA_METHOD, 0L), "symbols/index.html")
                + metric("metrics.sql", "SQL", counts.getOrDefault(NodeType.SQL_STATEMENT, 0L), "sql/index.html")
                + metric("metrics.tables", "Table", counts.getOrDefault(NodeType.DB_TABLE, 0L), "tables/index.html")
                + metric("metrics.warnings", "警告", warnings, "reports/evidence.html")
                + metric("metrics.stale", "Stale", stale, "reports/stale.html")
                + metric("metrics.conflicts", "Conflict", conflicts, "reports/conflicts.html");
        String featured = graph.nodes().stream().filter(node -> node.type() == NodeType.E2E_FLOW).limit(8).map(node -> card(node, "")).collect(Collectors.joining());
        return shell(options.title(), "", "<section class=\"hero\"><div class=\"kicker\">LIVE DOCUMENTATION GRAPH</div><h1>" + Html.escape(graph.projectId()) + "</h1><p>"
                + i18n("home.description", "画面、実行経路、Java、SQL、PostgreSQLを根拠とともに双方向接続します。")
                + "</p><dl class=\"analysis-meta\"><dt>Target commit</dt><dd><code>" + Html.escape(graph.targetCommit())
                + "</code></dd><dt>Analyzed at</dt><dd>" + formatInstant(graph) + "</dd><dt>Schema</dt><dd>"
                + Html.escape(graph.schemaVersion()) + "</dd></dl></section><section class=\"metrics\">" + metrics
                + "</section>" + homeDiff(diff) + "<section><div class=\"section-title\"><div><span>ENTRY POINTS</span><h2>"
                + i18n("home.e2e", "E2Eフロー") + "</h2></div><a href=\"crud/index.html\">CRUD matrix →</a></div><div class=\"cards\">"
                + (featured.isBlank() ? empty("empty.e2e", "E2Eフローはまだ発見されていません") : featured) + "</div></section>");
    }

    private String homeDiff(Diff diff) {
        if (diff == null || diff.isEmpty()) {
            return "<section class=\"panel\"><div class=\"section-label\">SEMANTIC DIFF</div><h2>"
                    + i18n("diff.majorChanges", "前回からの主要変更") + "</h2><p>"
                    + i18n("diff.noChanges", "意味のある変更はありません。") + "</p><a href=\"reports/diff.html\">"
                    + i18n("diff.openReport", "差分レポートを見る") + " →</a></section>";
        }
        List<String> changes = new ArrayList<>();
        diff.addedNodes().stream().limit(4).forEach(node -> changes.add("ADDED · " + node.type() + " · " + node.displayName()));
        diff.removedNodes().stream().limit(4).forEach(node -> changes.add("REMOVED · " + node.type() + " · " + node.displayName()));
        diff.modifiedNodes().stream().limit(4).forEach(change -> changes.add("MODIFIED · " + change.after().type() + " · " + change.after().displayName()));
        String items = changes.stream().limit(8).map(value -> "<li>" + Html.escape(value) + "</li>").collect(Collectors.joining());
        return "<section class=\"panel\"><div class=\"section-label\">SEMANTIC DIFF</div><h2>"
                + i18n("diff.majorChanges", "前回からの主要変更") + "</h2><p>"
                + i18n("diff.added", "追加") + " " + diff.addedNodes().size() + " · "
                + i18n("diff.removed", "削除") + " " + diff.removedNodes().size() + " · "
                + i18n("diff.modified", "変更") + " " + diff.modifiedNodes().size()
                + "</p><ul>" + items + "</ul><a href=\"reports/diff.html\">"
                + i18n("diff.openReport", "差分レポートを見る") + " →</a></section>";
    }

    private String collectionIndex(DocumentationGraph graph, String directory, RenderOptions options) {
        List<Node> nodes = graph.nodes().stream().filter(node -> PagePaths.directory(node.type()).equals(directory)).toList();
        String cards = nodes.stream().map(node -> card(node, "../")).collect(Collectors.joining());
        return shell(options.title() + " · " + directory, "../", "<nav class=\"breadcrumbs\"><a href=\"../index.html\">Mandala</a><span>/</span>"
                + Html.escape(directory) + "</nav><div class=\"kicker\">DOCUMENTATION GRAPH INDEX</div><h1>"
                + Html.escape(directory) + "</h1><p class=\"lead\">" + nodes.size() + " "
                + i18n("collection.summary", "nodes · Stable ID順の再生成可能な一覧")
                + "</p><div class=\"cards\">" + (cards.isBlank() ? empty("empty.category", "このカテゴリにNodeはありません") : cards) + "</div>");
    }

    private String nodePage(DocumentationGraph graph, Node node, GraphNavigator nav, String custom, RenderOptions options) {
        String attributes = node.attributes().isEmpty() ? empty("empty.attributes", "構造化属性はありません") : "<dl class=\"property-grid\">" + node.attributes().entrySet().stream().map(entry -> "<dt>" + Html.escape(entry.getKey()) + "</dt><dd><code>" + Html.escape(formatValue(entry.getValue())) + "</code></dd>").collect(Collectors.joining()) + "</dl>";
        String outgoing = relations(nav, nav.outgoing(node), true);
        String incoming = relations(nav, nav.incoming(node), false);
        String evidence = node.metadata().evidence().isEmpty() ? empty("empty.evidence", "Evidenceはありません") : node.metadata().evidence().stream().map(item -> "<article class=\"evidence\"><span class=\"badge badge-" + item.type().name().toLowerCase() + "\">" + item.type() + "</span><div><strong>" + Html.escape(item.description()) + "</strong><p>" + Html.escape(item.details()) + "</p><code>" + Html.escape(item.source()) + "</code></div></article>").collect(Collectors.joining());
        String warnings = node.metadata().warnings().isEmpty() ? "" : "<div class=\"warning\"><strong>" + i18n("node.warnings", "警告") + "</strong><ul>" + node.metadata().warnings().stream().map(warning -> "<li>" + Html.escape(warning) + "</li>").collect(Collectors.joining()) + "</ul></div>";
        String conflict = node.metadata().conflicts().isEmpty() ? "" : "<div class=\"conflict\"><strong>" + i18n("node.conflicts", "レビューが必要なConflict") + "</strong><ul>" + node.metadata().conflicts().stream().map(item -> "<li>" + Html.escape(item.type()) + ": " + Html.escape(item.description()) + "</li>").collect(Collectors.joining()) + "</ul></div>";
        String special = specialSection(graph, node);
        return shell(options.title() + " · " + node.displayName(), "../", "<nav class=\"breadcrumbs\"><a href=\"../index.html\">Mandala</a><span>/</span><span>" + node.type() + "</span></nav><header class=\"node-header\"><div><div class=\"kicker\">" + node.type() + "</div><h1>" + Html.escape(node.displayName()) + "</h1><code class=\"stable-id\">" + Html.escape(node.id()) + "</code><p class=\"lead\">" + Html.escape(node.description()) + "</p></div><div class=\"confidence confidence-" + node.metadata().confidence().name().toLowerCase() + "\"><span>CONFIDENCE</span><strong>" + node.metadata().confidence() + "</strong><small>" + node.metadata().reviewState() + "</small></div></header>" + warnings + conflict + special + "<section class=\"panel\"><div class=\"section-label\">STRUCTURED DATA</div><h2>"
                + i18n("node.specification", "仕様") + "</h2>" + attributes
                + "</section><div class=\"relation-columns\"><section class=\"panel\"><div class=\"section-label\">FORWARD</div><h2>"
                + i18n("node.forward", "この項目から辿る") + "</h2>" + outgoing
                + "</section><section class=\"panel\"><div class=\"section-label\">REVERSE INDEX</div><h2>"
                + i18n("node.reverse", "この項目を利用する") + "</h2>" + incoming
                + "</section></div><section class=\"panel\"><div class=\"section-label\">PROVENANCE</div><h2>Evidence</h2>" + evidence + "</section>" + custom);
    }

    private String specialSection(DocumentationGraph graph, Node node) {
        if (node.type() == NodeType.E2E_FLOW) {
            List<Node> related = relatedTables(graph, node);
            String crud = related.stream().map(table -> "<a class=\"table-chip\" href=\"../" + PagePaths.forNode(table) + "\"><strong>" + Html.escape(table.displayName()) + "</strong><span>" + crudFor(graph, node, table) + "</span></a>").collect(Collectors.joining());
            String screenshots = screenshotGallery(screenshotsForFlow(graph, node));
            List<Node> traces = runtimeForFlow(graph, node);
            String runtime = traces.stream().map(item -> "<a class=\"relation\" href=\"../" + PagePaths.forNode(item)
                    + "\"><span><small>" + item.type() + "</small>" + Html.escape(item.displayName())
                    + "</span><strong>" + Html.escape(item.attributes().getOrDefault("boundary", "TRACE")) + " →</strong></a>")
                    .collect(Collectors.joining());
            return screenshots
                    + "<section class=\"panel\"><div class=\"section-label\">RUNTIME PATH</div><h2>"
                    + i18n("flow.runtime", "観測された実行経路") + "</h2>"
                    + (runtime.isBlank() ? empty("empty.runtime", "このシナリオのRuntime Traceは観測されていません") : runtime)
                    + "</section><section class=\"panel\"><div class=\"section-label\">E2E DATABASE SCOPE</div><h2>"
                    + i18n("flow.crudEr", "CRUDと部分ER") + "</h2><div class=\"table-chips\">"
                    + (crud.isBlank() ? empty("empty.tables", "関連Tableはありません") : crud) + "</div><div class=\"er-inline\">"
                    + new ErDiagramRenderer().render(graph, related) + "</div></section>";
        }
        if (node.type() == NodeType.SCREEN) return screenDocumentation(graph, node);
        if (node.type() == NodeType.SCREEN_STATE) return screenshotGallery(screenshotsForState(graph, node));
        if (node.type() == NodeType.DB_TABLE) {
            return new TableDefinitionRenderer().render(graph, node) + relatedE2eSection(graph, node);
        }
        if (node.type() == NodeType.DB_COLUMN) return relatedE2eSection(graph, node);
        return "";
    }

    private String relatedE2eSection(DocumentationGraph graph, Node node) {
        String reverse = flowsUsing(graph, node).stream()
                .map(flow -> "<a class=\"relation\" href=\"../" + PagePaths.forNode(flow) + "\"><span>"
                        + Html.escape(flow.displayName()) + "</span><strong>"
                        + crudFor(graph, flow, node) + "</strong></a>")
                .collect(Collectors.joining());
        return "<section class=\"panel related-e2e\"><div class=\"section-label\">CRUD REVERSE LOOKUP</div><h2>"
                + i18n("table.relatedE2e", "関連E2E") + "</h2>"
                + (reverse.isBlank() ? empty("empty.relatedE2e", "関連E2Eフローはありません") : reverse)
                + "</section>";
    }

    private String screenshotGallery(List<Node> screenshots) {
        if (screenshots.isEmpty()) return "";
        String figures = screenshots.stream().map(node -> {
            String raw = String.valueOf(node.attributes().getOrDefault("path", ""));
            String fileName;
            try { fileName = Path.of(raw).getFileName().toString(); } catch (RuntimeException invalid) { fileName = ""; }
            if (fileName.isBlank()) return "";
            return "<figure><img src=\"../screenshots/" + Html.attribute(fileName) + "\" alt=\""
                    + Html.attribute(node.displayName()) + "\" loading=\"lazy\"><figcaption>"
                    + Html.escape(node.displayName()) + "</figcaption></figure>";
        }).collect(Collectors.joining());
        return figures.isBlank() ? "" : "<section class=\"panel\"><div class=\"section-label\">PLAYWRIGHT OBSERVATION</div><h2>"
                + i18n("screenshots.title", "画面キャプチャ") + "</h2><div class=\"screenshot-grid\">"
                + figures + "</div></section>";
    }

    private String screenDocumentation(DocumentationGraph graph, Node screen) {
        List<Node> screenshots = representativeScreenCaptures(graph, screen).stream()
                .map(ScreenCapture::screenshot).toList();
        String transitionRows = screenTransitionDetails(graph, screen);
        String actionRows = actionTransitions(graph, screen);
        return screenshotGallery(screenshots)
                + "<section class=\"panel screen-transition-details\"><div class=\"section-label\">SCREEN → SCREEN</div><h2>"
                + i18n("transitions.screenDetailTitle", "この画面の遷移") + "</h2><p>"
                + i18n("transitions.screenDetailDescription",
                "この画面を開始または終了とする、E2Eで観測済みの1対1の画面遷移です。")
                + "</p><div class=\"screen-transition-diagram\" role=\"list\">"
                + (transitionRows.isBlank()
                ? empty("empty.screenTransitions", "観測済みの画面間遷移はありません") : transitionRows)
                + "</div></section><section class=\"panel screen-state-details\"><div class=\"section-label\">STATE → ACTION → STATE</div><h2>"
                + i18n("transitions.screenActionTitle", "この画面の状態・操作・条件分岐") + "</h2><p>"
                + i18n("transitions.actionDescription",
                "各操作の開始状態、終了状態、順序、role、feature flag、結果、関連HTTPを表示します。")
                + "</p><div class=\"action-transition-diagram\" role=\"list\">"
                + (actionRows.isBlank()
                ? empty("empty.actionTransitions", "操作単位の状態遷移はまだ観測されていません") : actionRows)
                + "</div></section>";
    }

    private List<ScreenCapture> representativeScreenCaptures(DocumentationGraph graph, Node screen) {
        Map<String, ScreenCapture> byState = new LinkedHashMap<>();
        screenCaptures(graph, screen).forEach(capture ->
                byState.putIfAbsent(textAttribute(capture.state(), "state"), capture));
        return new ArrayList<>(byState.values());
    }

    private List<ScreenCapture> screenCaptures(DocumentationGraph graph, Node screen) {
        Map<String, Node> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(node -> node.id().value(), node -> node));
        Set<String> stateIds = graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.HAS_STATE && edge.from().equals(screen.id()))
                .map(edge -> edge.to().value()).collect(Collectors.toCollection(LinkedHashSet::new));
        return graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.CAPTURED_AS)
                .filter(edge -> stateIds.contains(edge.from().value()))
                .map(edge -> {
                    Node state = nodes.get(edge.from().value());
                    Node screenshot = nodes.get(edge.to().value());
                    return state == null || screenshot == null || screenshot.type() != NodeType.SCREENSHOT
                            ? null : new ScreenCapture(state, screenshot);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt((ScreenCapture capture) ->
                                screenStatePriority(textAttribute(capture.state(), "state")))
                        .thenComparing(capture -> textAttribute(capture.state(), "scenarioId"))
                        .thenComparingInt(capture -> intAttribute(capture.screenshot(), "pointIndex"))
                        .thenComparing(capture -> capture.screenshot().id().value()))
                .distinct().toList();
    }

    private List<Node> screenshotsForState(DocumentationGraph graph, Node state) {
        Map<String, Node> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(node -> node.id().value(), node -> node));
        return graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.CAPTURED_AS && edge.from().equals(state.id()))
                .map(edge -> nodes.get(edge.to().value())).filter(java.util.Objects::nonNull)
                .filter(node -> node.type() == NodeType.SCREENSHOT)
                .distinct().sorted(Comparator.comparing(node -> node.id().value())).toList();
    }

    private int screenStatePriority(String state) {
        return switch (state) {
            case "normal" -> 0;
            case "success" -> 1;
            case "empty" -> 2;
            case "loading" -> 3;
            case "validation-error" -> 4;
            case "forbidden" -> 5;
            case "api-error" -> 6;
            case "not-found" -> 7;
            default -> 8;
        };
    }

    private String screenCaptureTitle(DocumentationGraph graph, ScreenCapture capture) {
        String screenName = textAttribute(capture.screenshot(), "screenName");
        if (!screenName.isBlank()) return screenName;
        String scenario = textAttribute(capture.state(), "scenarioId");
        if (!scenario.isBlank()) {
            String flowTitle = graph.nodes().stream().filter(node -> node.type() == NodeType.E2E_FLOW)
                    .filter(node -> scenario.equals(textAttribute(node, "scenarioId")))
                    .map(Node::displayName).findFirst().orElse("");
            if (!flowTitle.isBlank()) return flowTitle;
        }
        return capture.screenshot().displayName().replaceFirst("\\s+screens?hot$", "");
    }

    private String screenshotFileName(Node screenshot) {
        String raw = textAttribute(screenshot, "path");
        try { return Path.of(raw).getFileName().toString(); }
        catch (RuntimeException invalid) { return ""; }
    }

    private String relations(GraphNavigator nav, List<Edge> edges, boolean forward) {
        if (edges.isEmpty()) return empty("empty.relationships", "関連項目はありません");
        return edges.stream().map(edge -> {
            Node target = nav.node(forward ? edge.to() : edge.from());
            if (target == null) return "<div class=\"broken-ref\">Missing node: " + Html.escape(forward ? edge.to() : edge.from()) + "</div>";
            String provenance = edge.metadata().evidence().stream().map(item -> item.type().name())
                    .distinct().sorted().collect(Collectors.joining(" + "));
            String edgeLabel = edge.type() + (provenance.isBlank() ? "" : " · "
                    + edge.metadata().confidence() + " · " + provenance);
            return "<a class=\"relation\" href=\"../" + PagePaths.forNode(target) + "\"><span><small>"
                    + Html.escape(edgeLabel) + "</small>" + Html.escape(target.displayName())
                    + "</span><strong>" + target.type() + " →</strong></a>";
        }).collect(Collectors.joining());
    }

    private String crudMatrix(DocumentationGraph graph, RenderOptions options) {
        List<Node> flows = graph.nodes().stream().filter(node -> node.type() == NodeType.E2E_FLOW).toList();
        List<Node> tables = tables(graph);
        String header = tables.stream().map(table -> "<th><a href=\"../" + PagePaths.forNode(table) + "\">" + Html.escape(table.displayName()) + "</a></th>").collect(Collectors.joining());
        String rows = flows.stream().map(flow -> "<tr><th><a href=\"../" + PagePaths.forNode(flow) + "\">" + Html.escape(flow.displayName()) + "</a></th>" + tables.stream().map(table -> {
            String crud = crudFor(graph, flow, table);
            if (crud.equals("—")) return "<td class=\"crud-cell empty-cell\">—</td>";
            List<Node> sql = relatedSql(graph, flow, table);
            List<Node> endpoints = relatedByType(graph, flow, Set.of(NodeType.HTTP_ENDPOINT));
            List<Node> services = relatedByType(graph, flow, Set.of(NodeType.APPLICATION_SERVICE));
            List<Node> daos = relatedByType(graph, flow, Set.of(NodeType.DOMA_DAO, NodeType.DOMA_DAO_METHOD));
            List<Node> columns = relatedColumns(graph, flow, table);
            List<Node> traces = runtimeForFlow(graph, flow).stream().filter(node -> node.type() == NodeType.TRACE).toList();
            String links = matrixLinks("Endpoint", endpoints, 3) + matrixLinks("Service", services, 3)
                    + matrixLinks("DAO", daos, 3) + matrixLinks("SQL", sql, 4)
                    + matrixLinks("Column", columns, 5) + matrixLinks("Trace", traces, 2);
            return "<td class=\"crud-cell\"><a href=\"../" + PagePaths.forNode(table) + "\"><strong>" + crud + "</strong></a><span class=\"crud-meta\">" + crudProvenance(graph, flow, table) + "</span>" + links + "</td>";
        }).collect(Collectors.joining()) + "</tr>").collect(Collectors.joining());
        return shell(options.title() + " · CRUD matrix", "../", "<nav class=\"breadcrumbs\"><a href=\"../index.html\">Mandala</a><span>/</span>CRUD</nav><div class=\"kicker\">SQL + RUNTIME OBSERVATION</div><h1>"
                + i18n("crud.title", "CRUDマトリクス") + "</h1><p class=\"lead\">"
                + i18n("crud.description", "セルからE2E、Endpoint、Service、DAO、SQL、Table、Column、Traceへ双方向に移動できます。HTTP MethodではなくSQLと観測結果から分類します。")
                + "</p><div class=\"matrix-wrap\"><table class=\"matrix\"><thead><tr><th>E2E / Table</th>" + header + "</tr></thead><tbody>" + rows + "</tbody></table></div>");
    }

    private String matrixLinks(String label, List<Node> nodes, int limit) {
        if (nodes.isEmpty()) return "";
        String links = nodes.stream().distinct().limit(limit).map(node -> "<a title=\"" + Html.attribute(node.displayName())
                + "\" href=\"../" + PagePaths.forNode(node) + "\">" + Html.escape(label) + "</a>")
                .collect(Collectors.joining(" · "));
        int remainder = Math.max(0, nodes.size() - limit);
        return "<small>" + links + (remainder == 0 ? "" : " · +" + remainder) + "</small>";
    }

    private String crudProvenance(DocumentationGraph graph, Node flow, Node table) {
        List<Edge> edges = crudEdgesFor(graph, flow, table);
        String scenario = String.valueOf(flow.attributes().getOrDefault("scenarioId", "")).replace('-', '.');
        boolean observed = edges.stream().anyMatch(edge -> values(edge.attributes().get("scenarios")).contains(scenario)
                || edge.metadata().relatedScenarios().contains(scenario));
        boolean direct = edges.stream().anyMatch(edge -> Boolean.TRUE.equals(edge.attributes().get("direct")));
        boolean indirect = edges.stream().anyMatch(edge -> Boolean.FALSE.equals(edge.attributes().get("direct")));
        boolean async = edges.stream().anyMatch(edge -> Boolean.TRUE.equals(edge.attributes().get("async")));
        return (observed ? "OBSERVED" : "INFERRED") + " · " + (async ? "ASYNC" : indirect && !direct ? "INDIRECT" : "DIRECT");
    }

    private Set<String> values(Object raw) {
        if (!(raw instanceof Collection<?> values)) return Set.of();
        return values.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    private String screenTransitionsPage(DocumentationGraph graph, RenderOptions options) {
        List<Edge> transitions = observedScreenEdges(graph);
        String map = screenMap(graph, transitions);
        return shell(options.title() + " · Screen transitions", "../",
                "<nav class=\"breadcrumbs\"><a href=\"../index.html\">Mandala</a><span>/</span>"
                        + i18n("transitions.nav", "画面遷移") + "</nav><div class=\"kicker\">PLAYWRIGHT OBSERVATION</div><h1>"
                        + i18n("transitions.title", "観測済み画面遷移図") + "</h1><p class=\"lead\">"
                        + i18n("transitions.description",
                        "E2Eで観測した画面をスクリーンショット付きのNodeとして配置し、NAVIGATES_TOを線で結んだ全体俯瞰図です。個々の遷移と画面内状態は各画面の資料で確認できます。")
                        + "</p><section class=\"panel screen-map-panel\"><div class=\"section-label\">SCREEN MAP</div><h2>"
                        + i18n("transitions.overview", "画面接続マップ") + "</h2><p>"
                        + i18n("transitions.overviewDescription",
                        "画面を選択すると、1対1の遷移、状態、操作、条件分岐、関連HTTPを確認できます。")
                        + "</p>" + (map.isBlank()
                        ? empty("empty.screenTransitions", "観測済みの画面間遷移はありません") : map)
                        + "</section>");
    }

    private List<Edge> observedScreenEdges(DocumentationGraph graph) {
        Map<String, Node> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(node -> node.id().value(), node -> node));
        return graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.NAVIGATES_TO)
                .filter(edge -> edge.metadata().confidence() == Confidence.OBSERVED)
                .filter(edge -> {
                    Node from = nodes.get(edge.from().value());
                    Node to = nodes.get(edge.to().value());
                    return from != null && to != null
                            && from.type() == NodeType.SCREEN && to.type() == NodeType.SCREEN;
                })
                .sorted(Comparator.comparing((Edge edge) -> edge.from().value())
                        .thenComparing(edge -> edge.to().value()))
                .toList();
    }

    private String screenMap(DocumentationGraph graph, List<Edge> transitions) {
        List<Node> screens = graph.nodes().stream().filter(node -> node.type() == NodeType.SCREEN)
                .sorted(Comparator.comparing(node -> textAttribute(node, "route"))).toList();
        if (screens.isEmpty()) return "";
        String nodes = screens.stream().map(screen -> screenMapNode(graph, screen))
                .collect(Collectors.joining());
        String edgeData = transitions.stream().map(edge -> "<template data-screen-edge data-from=\""
                        + Html.attribute(edge.from()) + "\" data-to=\"" + Html.attribute(edge.to())
                        + "\"></template>")
                .collect(Collectors.joining());
        String accessibleEdges = transitions.stream().map(edge -> {
            Node from = graph.node(edge.from()).orElse(null);
            Node to = graph.node(edge.to()).orElse(null);
            if (from == null || to == null) return "";
            return "<li>" + Html.escape(from.displayName()) + " → " + Html.escape(to.displayName()) + "</li>";
        }).collect(Collectors.joining());
        return "<div class=\"screen-map\" data-screen-map><svg class=\"screen-map-connectors\" "
                + "data-screen-connectors aria-hidden=\"true\"><defs><marker id=\"screen-map-arrow\" "
                + "markerWidth=\"8\" markerHeight=\"8\" refX=\"7\" refY=\"4\" orient=\"auto\" "
                + "markerUnits=\"strokeWidth\"><path class=\"screen-map-arrow\" d=\"M0,0 L8,4 L0,8 z\">"
                + "</path></marker></defs></svg><div class=\"screen-map-grid\">" + nodes + "</div>"
                + edgeData + "<ul class=\"visually-hidden\">" + accessibleEdges + "</ul></div>";
    }

    private String screenMapNode(DocumentationGraph graph, Node screen) {
        List<ScreenCapture> captures = representativeScreenCaptures(graph, screen);
        ScreenCapture primary = captures.isEmpty() ? null : captures.get(0);
        String title = primary == null ? screen.displayName() : screenCaptureTitle(graph, primary);
        String media;
        if (primary == null) {
            media = "<span class=\"screen-map-placeholder\">"
                    + i18n("transitions.noScreenshot", "スクリーンショット未観測") + "</span>";
        } else {
            String fileName = screenshotFileName(primary.screenshot());
            media = fileName.isBlank()
                    ? "<span class=\"screen-map-placeholder\">"
                    + i18n("transitions.noScreenshot", "スクリーンショット未観測") + "</span>"
                    : "<img src=\"../screenshots/" + Html.attribute(fileName) + "\" alt=\""
                    + Html.attribute(title) + "\" loading=\"lazy\">";
        }
        return "<a class=\"screen-map-node\" data-screen-node=\"" + Html.attribute(screen.id())
                + "\" href=\"../" + PagePaths.forNode(screen) + "\"><span class=\"screen-map-media\">"
                + media + "</span><span class=\"screen-map-copy\"><strong>" + Html.escape(title)
                + "</strong><code>" + Html.escape(textAttribute(screen, "route")) + "</code><small>"
                + i18nTemplate("transitions.stateCount", "{0}状態", captures.size())
                + "</small></span></a>";
    }

    private String screenTransitionDetails(DocumentationGraph graph, Node screen) {
        Map<String, Node> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(node -> node.id().value(), node -> node));
        return observedScreenEdges(graph).stream()
                .filter(edge -> edge.from().equals(screen.id()) || edge.to().equals(screen.id()))
                .map(edge -> {
                    Node from = nodes.get(edge.from().value());
                    Node to = nodes.get(edge.to().value());
                    String scenarios = scenarioLinks(graph, edge.metadata().relatedScenarios());
                    return "<article class=\"screen-transition\" role=\"listitem\"><a class=\"transition-state\" href=\"../"
                            + PagePaths.forNode(from) + "\"><small>" + i18n("transitions.from", "開始")
                            + "</small><strong>" + Html.escape(from.displayName()) + "</strong><code>"
                            + Html.escape(from.id()) + "</code></a><div class=\"transition-link\"><span class=\"badge\">OBSERVED</span><span class=\"transition-arrow\" aria-hidden=\"true\">→</span>"
                            + (scenarios.isBlank() ? "" : "<span class=\"transition-scenarios\">" + scenarios + "</span>")
                            + "</div><a class=\"transition-state\" href=\"../" + PagePaths.forNode(to)
                            + "\"><small>" + i18n("transitions.to", "終了") + "</small><strong>"
                            + Html.escape(to.displayName()) + "</strong><code>" + Html.escape(to.id())
                            + "</code></a></article>";
                }).collect(Collectors.joining());
    }

    private String scenarioLinks(DocumentationGraph graph, Collection<String> scenarioIds) {
        return scenarioIds.stream().sorted().map(scenarioId -> graph.nodes().stream()
                .filter(node -> node.type() == NodeType.E2E_FLOW)
                .filter(node -> scenarioId.equals(String.valueOf(node.attributes().getOrDefault("scenarioId", ""))))
                .findFirst()
                .map(flow -> "<a href=\"../" + PagePaths.forNode(flow) + "\">"
                        + Html.escape(flow.displayName()) + "</a>")
                .orElse(Html.escape(scenarioId))).collect(Collectors.joining(" · "));
    }

    private String actionTransitions(DocumentationGraph graph, Node screen) {
        Map<String, Node> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(node -> node.id().value(), node -> node));
        Map<String, Edge> performedByAction = graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.PERFORMED_ON)
                .collect(Collectors.toMap(edge -> edge.to().value(), edge -> edge, (first, ignored) -> first));
        List<ActionTransition> transitions = graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.TRANSITIONS_TO)
                .map(edge -> {
                    Edge performed = performedByAction.get(edge.from().value());
                    Node action = nodes.get(edge.from().value());
                    Node from = performed == null ? null : nodes.get(performed.from().value());
                    Node to = nodes.get(edge.to().value());
                    return action == null || from == null || to == null ? null
                            : new ActionTransition(action, from, to);
                })
                .filter(java.util.Objects::nonNull)
                .filter(transition -> textAttribute(transition.from(), "route")
                        .equals(textAttribute(screen, "route")))
                .sorted(Comparator.comparing((ActionTransition transition) -> textAttribute(transition.action(), "scenarioId"))
                        .thenComparingInt(transition -> intAttribute(transition.action(), "sequence")))
                .toList();
        Map<String, Set<String>> distinctBranches = new LinkedHashMap<>();
        transitions.forEach(transition -> distinctBranches
                .computeIfAbsent(branchKey(transition), ignored -> new LinkedHashSet<>())
                .add(branchOutcomeKey(transition)));
        Map<String, Long> branches = distinctBranches.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> (long) entry.getValue().size(),
                (first, ignored) -> first, LinkedHashMap::new));
        return transitions.stream().map(transition -> actionTransitionRow(
                graph, transition, branches.getOrDefault(branchKey(transition), 1L))).collect(Collectors.joining());
    }

    private String branchKey(ActionTransition transition) {
        return textAttribute(transition.from(), "route") + "\u0000"
                + textAttribute(transition.from(), "state") + "\u0000"
                + textAttribute(transition.action(), "kind") + "\u0000"
                + transition.action().displayName();
    }

    private String branchOutcomeKey(ActionTransition transition) {
        return textAttribute(transition.to(), "route") + "\u0000"
                + textAttribute(transition.to(), "state") + "\u0000"
                + textAttribute(transition.action(), "role") + "\u0000"
                + String.valueOf(transition.action().attributes().getOrDefault("featureFlags", Map.of()))
                + "\u0000" + textAttribute(transition.action(), "outcome");
    }

    private String actionTransitionRow(DocumentationGraph graph, ActionTransition transition, long branchCount) {
        Node action = transition.action();
        Node from = transition.from();
        Node to = transition.to();
        String role = textAttribute(action, "role");
        String outcome = textAttribute(action, "outcome");
        String conditions = (role.isBlank() ? "" : "<span><strong>role</strong> " + Html.escape(role) + "</span>")
                + featureFlagChips(action.attributes().get("featureFlags"))
                + (outcome.isBlank() ? "" : "<span><strong>outcome</strong> " + Html.escape(outcome) + "</span>")
                + (branchCount > 1 ? "<span class=\"branch-count\">"
                + i18nTemplate("transitions.branchCount", "{0}分岐", branchCount) + "</span>" : "");
        String http = relatedHttp(graph, action);
        String scenario = textAttribute(action, "scenarioId");
        String scenarioLink = scenarioLinks(graph, scenario.isBlank() ? List.of() : List.of(scenario));
        return "<article class=\"action-transition\" role=\"listitem\"><a class=\"transition-state\" href=\"../"
                + PagePaths.forNode(from) + "\"><small>" + i18n("transitions.from", "開始")
                + "</small><strong>" + Html.escape(textAttribute(from, "route")) + "</strong><span>"
                + Html.escape(textAttribute(from, "state")) + "</span></a><div class=\"transition-action\"><header><span class=\"transition-sequence\">#"
                + intAttribute(action, "sequence") + "</span><a href=\"../" + PagePaths.forNode(action) + "\">"
                + Html.escape(action.displayName()) + "</a><code>" + Html.escape(textAttribute(action, "kind"))
                + "</code></header><div class=\"transition-conditions\">" + conditions + "</div>"
                + (scenarioLink.isBlank() ? "" : "<div class=\"transition-scenarios\">" + scenarioLink + "</div>")
                + (http.isBlank() ? "" : "<div class=\"transition-http-list\"><small>"
                + i18n("transitions.relatedHttp", "関連HTTP") + "</small>" + http + "</div>")
                + "</div><span class=\"transition-arrow\" aria-hidden=\"true\">→</span><a class=\"transition-state\" href=\"../"
                + PagePaths.forNode(to) + "\"><small>" + i18n("transitions.to", "終了")
                + "</small><strong>" + Html.escape(textAttribute(to, "route")) + "</strong><span>"
                + Html.escape(textAttribute(to, "state")) + "</span></a></article>";
    }

    private String relatedHttp(DocumentationGraph graph, Node action) {
        Map<String, Node> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(node -> node.id().value(), node -> node));
        return graph.edges().stream()
                .filter(edge -> edge.type() == EdgeType.CALLS_HTTP && edge.from().equals(action.id()))
                .sorted(Comparator.comparingInt(edge -> intAttribute(edge.attributes(), "sequence")))
                .map(edge -> {
                    Node call = nodes.get(edge.to().value());
                    if (call == null) return "";
                    String status = String.valueOf(edge.attributes().getOrDefault("status", ""));
                    return "<a class=\"transition-http\" href=\"../" + PagePaths.forNode(call) + "\"><code>"
                            + Html.escape(call.displayName()) + "</code>"
                            + (status.isBlank() ? "" : "<span>HTTP " + Html.escape(status) + "</span>") + "</a>";
                }).collect(Collectors.joining());
    }

    private String featureFlagChips(Object raw) {
        if (!(raw instanceof Map<?, ?> flags) || flags.isEmpty()) return "";
        return flags.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .map(entry -> "<span><strong>flag</strong> " + Html.escape(entry.getKey()) + "="
                        + Html.escape(entry.getValue()) + "</span>").collect(Collectors.joining());
    }

    private String textAttribute(Node node, String key) {
        return String.valueOf(node.attributes().getOrDefault(key, ""));
    }

    private int intAttribute(Node node, String key) {
        return intAttribute(node.attributes(), key);
    }

    private int intAttribute(Map<String, Object> attributes, String key) {
        Object raw = attributes.get(key);
        if (raw instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private String erPage(DocumentationGraph graph, RenderOptions options) {
        return shell(options.title() + " · ER diagram", "../", "<nav class=\"breadcrumbs\"><a href=\"../index.html\">Mandala</a><span>/</span>ER</nav><div class=\"kicker\">POSTGRESQL INTROSPECTION</div><h1>"
                + i18n("er.title", "ER図") + "</h1><label class=\"search-label\" for=\"table-filter\">"
                + i18n("er.search", "Table検索") + "</label><input id=\"table-filter\" data-table-filter placeholder=\"public.projects\"/><div class=\"er-frame\">"
                + new ErDiagramRenderer().render(graph, tables(graph)) + "</div>");
    }

    private String evidenceReport(DocumentationGraph graph, RenderOptions options) {
        String rows = graph.nodes().stream().flatMap(node -> node.metadata().evidence().stream().map(evidence -> "<tr><td><a href=\"../" + PagePaths.forNode(node) + "\">" + Html.escape(node.displayName()) + "</a></td><td>" + evidence.type() + "</td><td>" + node.metadata().confidence() + "</td><td>" + Html.escape(evidence.description()) + "</td><td><code>" + Html.escape(evidence.source()) + "</code></td></tr>")).collect(Collectors.joining());
        return reportShell("Evidence", null,
                i18n("report.evidenceDescription", "各情報の根拠、情報源、確度を一覧化します。"), rows, options);
    }

    private String stateReport(DocumentationGraph graph, RenderOptions options, boolean stale) {
        String title = stale ? "Stale" : "Conflict";
        String rows = graph.nodes().stream().filter(node -> stale ? node.metadata().stale().stale() : node.metadata().conflicted()).map(node -> "<tr><td><a href=\"../" + PagePaths.forNode(node) + "\">" + Html.escape(node.displayName()) + "</a></td><td>" + node.type() + "</td><td>" + (stale ? Html.escape(node.metadata().stale().reason()) : Html.escape(node.metadata().conflicts().stream().map(conflict -> conflict.description()).collect(Collectors.joining("; ")))) + "</td><td>" + node.metadata().confidence() + "</td><td><code>" + Html.escape(node.id()) + "</code></td></tr>").collect(Collectors.joining());
        return reportShell(title, null, i18n(
                stale ? "report.staleDescription" : "report.conflictDescription",
                stale ? "元実装の変更後に再確認が必要な説明です。" : "情報源間の矛盾で人間またはAgentのレビューが必要です。"),
                rows, options);
    }

    private String diffReport(DocumentationGraph graph, Diff diff, RenderOptions options) {
        if (diff == null || diff.isEmpty()) {
            return reportShell("前回解析との差分", "report.diffTitle",
                    i18n("report.diffEmpty", "生成時刻やJSON順序を除外したsemantic diff。意味のある変更はありません。"),
                    "", options);
        }
        StringBuilder rows = new StringBuilder();
        diff.addedNodes().forEach(node -> rows.append(diffRow("ADDED", node, "diff.newItem", "新規項目")));
        diff.removedNodes().forEach(node -> rows.append("<tr><td>REMOVED</td><td>")
                .append(Html.escape(node.displayName())).append("</td><td>").append(node.type())
                .append("</td><td>").append(i18n("diff.deleted", "削除")).append("</td><td><code>")
                .append(Html.escape(node.id())).append("</code></td></tr>"));
        diff.modifiedNodes().forEach(change -> rows.append(diffRow("MODIFIED", change.after(), null,
                String.join(", ", change.changedFields()))));
        Set<String> directlyChanged = new LinkedHashSet<>();
        diff.addedNodes().forEach(node -> directlyChanged.add(node.id().value()));
        diff.removedNodes().forEach(node -> directlyChanged.add(node.id().value()));
        diff.modifiedNodes().forEach(change -> directlyChanged.add(change.id().value()));
        diff.impactedNodes().stream().filter(id -> !directlyChanged.contains(id.value())).map(graph::node)
                .flatMap(java.util.Optional::stream).forEach(node -> rows.append(diffRow("IMPACTED", node,
                        "diff.impact", "逆引き影響範囲")));
        String summary = i18nTemplate("report.diffDescription",
                "生成時刻やJSON順序を除外したsemantic diff。node +{0} / -{1} / ~{2}、edge +{3} / -{4} / ~{5}、影響候補 {6}。",
                diff.addedNodes().size(), diff.removedNodes().size(), diff.modifiedNodes().size(),
                diff.addedEdges().size(), diff.removedEdges().size(), diff.modifiedEdges().size(),
                diff.impactedNodes().size());
        return reportShell("前回解析との差分", "report.diffTitle", summary, rows.toString(), options);
    }

    private String diffRow(String state, Node node, String detailKey, String detail) {
        String visibleDetail = detailKey == null ? Html.escape(detail) : i18n(detailKey, detail);
        return "<tr><td>" + Html.escape(state) + "</td><td><a href=\"../" + PagePaths.forNode(node) + "\">"
                + Html.escape(node.displayName()) + "</a></td><td>" + node.type() + "</td><td>"
                + visibleDetail + "</td><td><code>" + Html.escape(node.id()) + "</code></td></tr>";
    }

    private String reportShell(String title, String titleKey, String descriptionHtml,
                               String rows, RenderOptions options) {
        String visibleTitle = titleKey == null ? Html.escape(title) : i18n(titleKey, title);
        return shell(options.title() + " · " + title, "../", "<nav class=\"breadcrumbs\"><a href=\"../index.html\">Mandala</a><span>/</span>Reports</nav><div class=\"kicker\">RECONCILIATION REPORT</div><h1>"
                + visibleTitle + "</h1><p class=\"lead\">" + descriptionHtml
                + "</p><div class=\"matrix-wrap\"><table class=\"matrix\"><thead><tr><th>"
                + i18n("report.item", "項目") + "</th><th>"
                + i18n("report.type", "種別") + "</th><th>"
                + i18n("report.stateEvidence", "状態 / Evidence") + "</th><th>Confidence</th><th>"
                + i18n("report.sourceId", "情報源 / Stable ID")
                + "</th></tr></thead><tbody>" + (rows.isBlank() ? "<tr><td colspan=\"5\">"
                + i18n("empty.report", "該当項目はありません。") + "</td></tr>" : rows) + "</tbody></table></div>");
    }

    private String shell(String title, String prefix, String content) {
        return "<!doctype html><html lang=\"ja\" data-locale=\"ja\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta name=\"color-scheme\" content=\"light dark\"><title>"
                + Html.escape(title) + "</title><link rel=\"icon\" href=\"" + prefix
                + "assets/favicon.svg\" type=\"image/svg+xml\"><link rel=\"stylesheet\" href=\"" + prefix
                + "assets/mandala.css\"><link rel=\"stylesheet\" href=\"" + prefix
                + "assets/custom.css\"></head><body><header class=\"site-nav\"><a href=\"" + prefix
                + "index.html\" class=\"logo\">Mandala <span>SbDP</span></a><div class=\"site-actions\"><nav><a href=\"" + prefix
                + "screens/transitions.html\">" + i18n("transitions.nav", "画面遷移") + "</a><a href=\"" + prefix
                + "crud/index.html\">CRUD</a><a href=\"" + prefix + "er/index.html\">ER</a><a href=\"" + prefix
                + "reports/evidence.html\">Evidence</a><button data-search-open data-i18n-aria-label=\"search.open\" aria-label=\"検索を開く\">⌕</button></nav>"
                + "<div class=\"display-controls\"><label><span>" + i18n("controls.language", "言語")
                + "</span><select data-language data-i18n-aria-label=\"controls.language\" aria-label=\"言語\"><option value=\"ja\">日本語</option><option value=\"en\">English</option></select></label>"
                + "<label><span>" + i18n("controls.theme", "テーマ")
                + "</span><select data-theme-select data-i18n-aria-label=\"controls.theme\" aria-label=\"テーマ\"><option value=\"system\" data-i18n=\"theme.system\">システム</option>"
                + "<option value=\"light\" data-i18n=\"theme.light\">ライト</option><option value=\"dark\" data-i18n=\"theme.dark\">ダーク</option></select></label></div></div></header><main>"
                + content + "</main><dialog data-search><form method=\"dialog\"><button data-i18n-aria-label=\"search.close\" aria-label=\"閉じる\">×</button></form><label for=\"mandala-search\">"
                + i18n("search.label", "Documentation Graphを検索") + "</label><input id=\"mandala-search\" autocomplete=\"off\" data-i18n-placeholder=\"search.placeholder\" placeholder=\"Endpoint, Table, Stable ID…\"><div data-search-results></div></dialog><footer><span>Generated by Mandala SbDP</span><span>Evidence-aware · Bidirectional · Reproducible</span></footer><script src=\""
                + prefix + "assets/mandala.js\" defer></script></body></html>";
    }

    private String metric(String key, String label, long value, String href) { return "<a class=\"metric\" href=\"" + href + "\"><strong>" + value + "</strong><span>" + i18n(key, label) + "</span></a>"; }
    private String card(Node node, String prefix) { return "<a class=\"card\" href=\"" + prefix + PagePaths.forNode(node) + "\"><span>" + node.type() + "</span><h3>" + Html.escape(node.displayName()) + "</h3><p>" + Html.escape(node.description()) + "</p><code>" + Html.escape(node.id()) + "</code></a>"; }
    private String empty(String key, String message) { return "<div class=\"empty\">" + i18n(key, message) + "</div>"; }
    private String i18n(String key, String japanese) {
        return "<span data-i18n=\"" + Html.attribute(key) + "\">" + Html.escape(japanese) + "</span>";
    }
    private String i18nTemplate(String key, String japanese, long... values) {
        String joined = java.util.Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining(","));
        String rendered = japanese;
        for (int index = 0; index < values.length; index++) {
            rendered = rendered.replace("{" + index + "}", String.valueOf(values[index]));
        }
        return "<span data-i18n-template=\"" + Html.attribute(key) + "\" data-i18n-values=\""
                + Html.attribute(joined) + "\">" + Html.escape(rendered) + "</span>";
    }
    private String formatValue(Object value) { return value instanceof Map<?, ?> || value instanceof List<?> ? value.toString() : String.valueOf(value); }
    private String formatInstant(DocumentationGraph graph) { return graph.analyzedAt() == null ? "unknown" : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(graph.analyzedAt().atOffset(ZoneOffset.UTC)); }
    private List<Node> tables(DocumentationGraph graph) { return graph.nodes().stream().filter(node -> node.type() == NodeType.DB_TABLE).toList(); }

    private List<Node> relatedTables(DocumentationGraph graph, Node flow) {
        Set<String> reachable = reachable(graph, flow).stream().map(node -> node.id().value()).collect(Collectors.toSet());
        Set<String> tableIds = graph.edges().stream()
                .filter(edge -> CRUD.contains(edge.type()) && reachable.contains(edge.from().value()))
                .map(Edge::to).map(Object::toString).collect(Collectors.toCollection(LinkedHashSet::new));
        graph.nodes().stream().filter(node -> node.type() == NodeType.DB_COLUMN && tableIds.contains(node.id().value()))
                .map(node -> node.id().value().substring("column:".length(), node.id().value().lastIndexOf('.')))
                .map(value -> "table:" + value).forEach(tableIds::add);
        return graph.nodes().stream().filter(node -> node.type() == NodeType.DB_TABLE)
                .filter(node -> tableIds.contains(node.id().value())).toList();
    }

    private List<Node> screenshotsForFlow(DocumentationGraph graph, Node flow) {
        String scenario = String.valueOf(flow.attributes().getOrDefault("scenarioId", ""));
        Set<String> states = graph.nodes().stream().filter(node -> node.type() == NodeType.SCREEN_STATE)
                .filter(node -> scenario.equals(String.valueOf(node.attributes().getOrDefault("scenarioId", ""))))
                .map(node -> node.id().value()).collect(Collectors.toSet());
        Set<String> screenshotIds = graph.edges().stream().filter(edge -> edge.type() == EdgeType.CAPTURED_AS)
                .filter(edge -> states.contains(edge.from().value())).map(edge -> edge.to().value())
                .collect(Collectors.toSet());
        return graph.nodes().stream().filter(node -> node.type() == NodeType.SCREENSHOT)
                .filter(node -> screenshotIds.contains(node.id().value())).toList();
    }

    private List<Node> runtimeForFlow(DocumentationGraph graph, Node flow) {
        Map<String, Node> nodes = graph.nodes().stream().collect(Collectors.toMap(node -> node.id().value(), node -> node));
        Map<String, List<Edge>> outgoing = graph.edges().stream().collect(Collectors.groupingBy(edge -> edge.from().value()));
        Set<String> traceIds = graph.edges().stream().filter(edge -> edge.type() == EdgeType.OBSERVED_IN && edge.from().equals(flow.id()))
                .map(edge -> nodes.get(edge.to().value())).filter(java.util.Objects::nonNull)
                .filter(node -> node.type() == NodeType.TRACE).map(node -> node.id().value()).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> included = new LinkedHashSet<>(); ArrayDeque<String> queue = new ArrayDeque<>(traceIds);
        while (!queue.isEmpty()) {
            String id = queue.remove(); if (!included.add(id)) continue;
            for (Edge edge : outgoing.getOrDefault(id, List.of())) {
                if (edge.type() == EdgeType.CONTAINS || edge.type() == EdgeType.CALLS) queue.add(edge.to().value());
            }
        }
        return included.stream().map(nodes::get).filter(java.util.Objects::nonNull).toList();
    }
    private List<Node> flowsUsing(DocumentationGraph graph, Node target) {
        return graph.nodes().stream().filter(node -> node.type() == NodeType.E2E_FLOW).filter(flow -> reachable(graph, flow).stream().anyMatch(candidate -> candidate.id().equals(target.id()) || (target.type() == NodeType.DB_COLUMN && isColumnOf(target, candidate)))).toList();
    }
    private boolean isColumnOf(Node column, Node table) { return table.type() == NodeType.DB_TABLE && column.id().value().startsWith(table.id().value().replace("table:", "column:") + "."); }
    private Set<Node> reachable(DocumentationGraph graph, Node start) {
        Map<String, Node> nodes = graph.nodes().stream().collect(Collectors.toMap(node -> node.id().value(), node -> node));
        Map<String, List<Edge>> outgoing = graph.edges().stream().collect(Collectors.groupingBy(edge -> edge.from().value()));
        Set<String> seen = new LinkedHashSet<>(); ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(start.id().value());
        while (!queue.isEmpty() && seen.size() < 5000) {
            String id = queue.remove(); if (!seen.add(id)) continue;
            for (Edge edge : outgoing.getOrDefault(id, List.of())) if (EXECUTION_PATH.contains(edge.type())) queue.add(edge.to().value());
        }
        return seen.stream().map(nodes::get).filter(java.util.Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }
    private String crudFor(DocumentationGraph graph, Node flow, Node tableOrColumn) {
        Set<String> reachable = reachable(graph, flow).stream().map(node -> node.id().value()).collect(Collectors.toSet());
        Set<String> targetIds = new LinkedHashSet<>(); targetIds.add(tableOrColumn.id().value());
        if (tableOrColumn.type() == NodeType.DB_TABLE) graph.nodes().stream().filter(node -> isColumnOf(node, tableOrColumn)).map(node -> node.id().value()).forEach(targetIds::add);
        String values = graph.edges().stream().filter(edge -> CRUD.contains(edge.type()) && reachable.contains(edge.from().value()) && targetIds.contains(edge.to().value())).map(edge -> edge.type().name().replace("CREATES", "CREATE").replace("READS", "READ").replace("UPDATES", "UPDATE").replace("DELETES", "DELETE")).distinct().sorted().collect(Collectors.joining(" / "));
        return values.isBlank() ? "—" : values;
    }
    private List<Node> relatedSql(DocumentationGraph graph, Node flow, Node table) {
        Set<String> sql = crudEdgesFor(graph, flow, table).stream().map(edge -> edge.from().value()).collect(Collectors.toSet());
        return graph.nodes().stream().filter(node -> node.type() == NodeType.SQL_STATEMENT && sql.contains(node.id().value())).toList();
    }

    private List<Node> relatedColumns(DocumentationGraph graph, Node flow, Node table) {
        Set<String> columns = crudEdgesFor(graph, flow, table).stream().map(Edge::to).map(Object::toString)
                .filter(id -> id.startsWith("column:")).collect(Collectors.toSet());
        return graph.nodes().stream().filter(node -> node.type() == NodeType.DB_COLUMN && columns.contains(node.id().value())).toList();
    }

    private List<Edge> crudEdgesFor(DocumentationGraph graph, Node flow, Node table) {
        Set<String> reachable = reachable(graph, flow).stream().map(node -> node.id().value()).collect(Collectors.toSet());
        String columnPrefix = table.id().value().replace("table:", "column:") + ".";
        return graph.edges().stream().filter(edge -> CRUD.contains(edge.type()) && reachable.contains(edge.from().value()))
                .filter(edge -> edge.to().equals(table.id()) || edge.to().value().startsWith(columnPrefix)).toList();
    }

    private List<Node> relatedByType(DocumentationGraph graph, Node flow, Set<NodeType> types) {
        return reachable(graph, flow).stream().filter(node -> types.contains(node.type())).toList();
    }

    private void write(Path path, String content) throws IOException { Files.createDirectories(path.getParent()); Files.writeString(path, content, StandardCharsets.UTF_8); }

    private void copyScreenshotAssets(DocumentationGraph graph, Path output, Path repositoryRoot) throws IOException {
        Path normalizedRoot = repositoryRoot == null ? null : repositoryRoot.toAbsolutePath().normalize();
        for (Node node : graph.nodes()) {
            if (node.type() != NodeType.SCREENSHOT) continue;
            String raw = String.valueOf(node.attributes().getOrDefault("path", ""));
            if (raw.isBlank()) continue;
            Path configured;
            try { configured = Path.of(raw); } catch (RuntimeException invalid) { throw new IOException("Invalid screenshot path for " + node.id() + ": " + raw, invalid); }
            Path source;
            if (configured.isAbsolute()) source = configured.normalize();
            else if (normalizedRoot != null) source = normalizedRoot.resolve(configured).normalize();
            else source = output.getParent().resolve("screenshots").resolve(configured.getFileName()).normalize();
            if (normalizedRoot != null && !source.startsWith(normalizedRoot)) {
                throw new IOException("Screenshot path escapes repository root for " + node.id() + ": " + raw);
            }
            if (!Files.isRegularFile(source)) throw new IOException("Screenshot asset is missing for " + node.id() + ": " + source);
            Path target = output.resolve("screenshots").resolve(configured.getFileName().toString());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String stylesheet() { return """
:root{color-scheme:light dark;font-family:Inter,ui-sans-serif,system-ui,-apple-system,sans-serif;--canvas:#f3f2eb;--ink:#17221d;--paper:#fffef8;--line:#d8dbd4;--green:#255f4e;--lime:#d8ff73;--muted:#647068;--red:#a33d3d;--diagram-surface:#f5f8f4;--diagram-box:#fffef9;--diagram-stroke:#86928b;--diagram-title:#17221d;--diagram-meta:#526158;--diagram-column:#33453d;--diagram-key:#255f4e;--diagram-rel:#6e8f82;color:var(--ink);background:var(--canvas)}*{box-sizing:border-box}body{margin:0;background:var(--canvas);color:var(--ink)}a{color:var(--green);text-underline-offset:3px}.site-nav{min-height:66px;padding:.55rem max(22px,4vw);display:flex;align-items:center;justify-content:space-between;gap:1rem;background:rgba(255,254,248,.95);border-bottom:1px solid var(--line);position:sticky;top:0;z-index:5}.logo{color:var(--ink);font-weight:900;text-decoration:none;letter-spacing:-.05em;font-size:1.25rem;flex:none}.logo span{color:var(--green)}.site-actions,.site-nav nav,.display-controls,.display-controls label{display:flex;gap:1rem;align-items:center}.site-actions{justify-content:flex-end}.display-controls{gap:.55rem}.display-controls label{gap:.3rem;color:var(--muted);font-size:.68rem;font-weight:800}.display-controls select{appearance:none;-webkit-appearance:none;border:1px solid var(--line);border-radius:999px;background-color:var(--paper);background-image:linear-gradient(45deg,transparent 50%,var(--muted) 50%),linear-gradient(135deg,var(--muted) 50%,transparent 50%);background-position:calc(100% - 16px) calc(50% - 2px),calc(100% - 12px) calc(50% - 2px);background-repeat:no-repeat;background-size:4px 4px;color:var(--ink);padding:.48rem 2.2rem .48rem .75rem;font:inherit}.site-nav button{border:0;background:var(--ink);color:var(--paper);width:34px;height:34px;border-radius:50%;font-size:1.3rem}main{width:min(1180px,calc(100% - 40px));margin:4.5rem auto 7rem}.hero{padding:2rem 0 3rem}.hero h1,.node-header h1,main>h1{font-size:clamp(2.6rem,7vw,6.5rem);letter-spacing:-.065em;line-height:.92;margin:.5rem 0 1.5rem}.hero p,.lead{font-size:1.2rem;color:var(--muted);max-width:760px;line-height:1.6}.kicker,.section-label,.card>span,.confidence>span{font-size:.68rem;font-weight:900;letter-spacing:.16em;color:var(--green)}.analysis-meta,.property-grid{display:grid;grid-template-columns:max-content minmax(0,1fr);gap:.5rem 1.4rem}.analysis-meta dt,.property-grid dt{color:var(--muted)}.analysis-meta dd,.property-grid dd{min-width:0;margin:0;overflow-wrap:anywhere}code{font-family:ui-monospace,SFMono-Regular,monospace;font-size:.82em}.metrics{display:flex;flex-wrap:wrap;gap:1px;background:var(--line);border:1px solid var(--line);border-radius:18px;overflow:hidden;margin-bottom:5rem}.metric{background:var(--paper);padding:1.25rem;color:var(--ink);text-decoration:none;display:flex;flex:1 1 210px;min-width:0;flex-direction:column}.metric strong{font-size:2rem}.metric span{font-size:.75rem;color:var(--muted)}.section-title{display:flex;justify-content:space-between;align-items:end}.section-title h2,.panel h2{font-size:2rem;letter-spacing:-.04em;margin:.25rem 0 1.5rem}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:1rem}.card,.panel,.custom-section{background:var(--paper);border:1px solid var(--line);border-radius:18px;padding:1.5rem}.card{text-decoration:none;color:var(--ink);min-height:220px}.card h3{font-size:1.5rem}.card p{color:var(--muted)}.card code{display:block;margin-top:2rem;color:var(--green);overflow-wrap:anywhere}.breadcrumbs{display:flex;gap:.65rem;color:var(--muted);margin-bottom:3rem}.node-header{display:grid;grid-template-columns:1fr auto;gap:3rem;align-items:start;margin-bottom:3rem}.stable-id{padding:.4rem .7rem;background:#e7e9e3;border-radius:6px;color:var(--ink)}.confidence{min-width:180px;border:1px solid var(--line);border-radius:15px;padding:1rem;display:flex;flex-direction:column;background:var(--paper);color:var(--ink)}.confidence strong{font-size:1.2rem;margin:.4rem 0}.confidence-observed{border-color:#63a97e}.confidence-conflicted{border-color:#d27373}.confidence-inferred{border-style:dashed}.panel,.custom-section{margin:1rem 0}.relation-columns{display:grid;grid-template-columns:1fr 1fr;gap:1rem}.relation{border-top:1px solid var(--line);padding:.85rem 0;display:flex;justify-content:space-between;gap:1rem;text-decoration:none}.relation span{display:flex;flex-direction:column;color:var(--ink)}.relation small{color:var(--green);font-size:.65rem}.relation strong{font-size:.68rem;color:var(--muted)}.empty{border:1px dashed #a7aea9;border-radius:12px;padding:1.5rem;color:var(--muted)}.evidence{display:grid;grid-template-columns:180px 1fr;gap:1rem;border-top:1px solid var(--line);padding:1rem 0}.badge{font-size:.65rem;font-weight:900;color:var(--green)}.warning,.conflict{padding:1rem 1.2rem;border-radius:12px;margin:1rem 0}.warning{background:#fff4c8}.conflict{background:#ffe2e2;color:#702323}.custom-section{border-color:#80a797}.custom-html{border-top:1px solid var(--line);padding-top:1rem}.broken-ref{color:var(--red);font-weight:700}.table-chips{display:flex;gap:.6rem;flex-wrap:wrap}.table-chip{display:flex;gap:1rem;border:1px solid var(--line);border-radius:999px;padding:.55rem .9rem;text-decoration:none}.table-chip span{color:var(--muted)}.screenshot-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:1rem}.screenshot-grid figure{margin:0}.screenshot-grid img{display:block;width:100%;height:auto;border:1px solid var(--line);border-radius:12px}.screenshot-grid figcaption{margin-top:.5rem;color:var(--muted);font-size:.8rem}.er-inline,.er-frame{background:var(--diagram-surface);border:1px solid var(--line);border-radius:12px;margin-top:1.5rem;padding:clamp(.75rem,2vw,1.25rem)}.er-diagram{display:block}.er-table-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,280px),1fr));gap:1rem}.er-table{min-width:0;overflow:hidden;background:var(--diagram-box);border:1px solid var(--diagram-stroke);border-radius:12px;color:var(--diagram-title)}.er-table-header{padding:.9rem 1rem;border-bottom:1px solid var(--diagram-stroke)}.er-table-header h3{font-size:1.05rem;line-height:1.3;margin:0}.er-table-header h3 a{color:var(--diagram-title);overflow-wrap:anywhere}.er-table-header code{display:block;margin-top:.35rem;color:var(--diagram-meta);overflow-wrap:anywhere}.er-column-scroll{overflow:auto}.er-column-table{width:100%;border-collapse:collapse;font-size:.78rem}.er-column-table th,.er-column-table td{padding:.55rem .65rem;border-bottom:1px solid var(--line);text-align:left;vertical-align:middle}.er-column-table thead th{color:var(--diagram-meta);font-size:.67rem;letter-spacing:.05em;text-transform:uppercase}.er-column-table tbody tr:last-child th,.er-column-table tbody tr:last-child td{border-bottom:0}.er-column-table tbody th{font-weight:650}.er-column-table tbody th a{color:var(--diagram-column);white-space:nowrap}.er-column-table code{color:var(--diagram-column);white-space:normal;overflow-wrap:anywhere}.er-key-badge{display:inline-flex;margin:.1rem .18rem .1rem 0;padding:.12rem .32rem;border:1px solid var(--diagram-key);border-radius:999px;color:var(--diagram-key);font-size:.61rem;font-weight:850;line-height:1.2}.er-no-key{color:var(--diagram-meta)}.er-table-empty{padding:1rem;color:var(--diagram-meta)}.er-relationships{margin-top:1rem;padding:1rem;background:var(--diagram-box);border:1px solid var(--diagram-stroke);border-radius:12px}.er-relationships h3{margin:0 0 .75rem;font-size:.95rem}.er-relation-list{list-style:none;margin:0;padding:0;display:grid;gap:.5rem}.er-relation-list li{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:.7rem;align-items:center;padding:.55rem .65rem;background:var(--diagram-surface);border-radius:8px}.er-relation-list a{overflow-wrap:anywhere}.er-relation-kind{display:flex;gap:.35rem;align-items:center;color:var(--diagram-rel)}.er-relation-kind code{font-size:.65rem}.er-inline [data-table].is-filtered,.er-frame [data-table].is-filtered{display:none}.table-definition-heading{display:flex;justify-content:space-between;gap:1rem;align-items:end}.table-definition-heading h2{margin-bottom:1rem}.table-definition-count{display:inline-flex;align-items:baseline;gap:.35rem;margin-bottom:1rem;padding:.35rem .65rem;border:1px solid var(--line);border-radius:999px;color:var(--muted);font-size:.72rem}.table-definition-count strong{color:var(--ink);font-size:1rem}.table-definition-facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin:0 0 1.25rem;border:1px solid var(--line);border-radius:12px;overflow:hidden;background:var(--diagram-surface)}.table-definition-facts>div{min-width:0;padding:.8rem 1rem;border-right:1px solid var(--line)}.table-definition-facts>div:last-child{border-right:0}.table-definition-facts dt{color:var(--diagram-meta);font-size:.67rem;font-weight:850;letter-spacing:.06em;text-transform:uppercase}.table-definition-facts dd{margin:.3rem 0 0;overflow-wrap:anywhere}.definition-status{display:inline-flex;padding:.15rem .45rem;border:1px solid var(--line);border-radius:999px;font-size:.68rem;font-weight:800}.definition-status.is-enabled{border-color:var(--green);color:var(--green)}.definition-status.is-disabled{color:var(--diagram-meta)}.table-definition-wrap{overflow:auto;border:1px solid var(--diagram-stroke);border-radius:12px;background:var(--diagram-box)}.table-definition-table{width:100%;min-width:920px;border-collapse:collapse;font-size:.78rem;color:var(--diagram-title)}.table-definition-table th,.table-definition-table td{padding:.68rem .75rem;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}.table-definition-table thead th{color:var(--diagram-meta);font-size:.67rem;letter-spacing:.05em;text-transform:uppercase;background:var(--diagram-surface)}.table-definition-table tbody tr:last-child th,.table-definition-table tbody tr:last-child td{border-bottom:0}.table-definition-table tbody th a{white-space:nowrap;color:var(--diagram-column)}.table-definition-table code{color:var(--diagram-column);white-space:normal;overflow-wrap:anywhere}.definition-ordinal{width:3rem;color:var(--diagram-meta)}.definition-nullability{display:inline-flex;white-space:nowrap;font-size:.67rem;font-weight:850;color:var(--diagram-meta)}.definition-nullability.is-required{color:var(--diagram-title)}.definition-default{display:block;min-width:8rem;max-width:18rem}.definition-badges{display:flex;flex-wrap:wrap;gap:.25rem}.definition-badge{display:inline-flex;padding:.12rem .36rem;border:1px solid var(--diagram-key);border-radius:999px;color:var(--diagram-key);font-size:.61rem;font-weight:850;line-height:1.25;white-space:nowrap}.definition-reference{display:block;margin-top:.35rem;font-size:.7rem;overflow-wrap:anywhere}.definition-comment{min-width:10rem;color:var(--diagram-meta)}.table-definition-details{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem;margin-top:1rem}.definition-detail{min-width:0;padding:1rem;border:1px solid var(--line);border-radius:12px;background:var(--diagram-surface)}.definition-detail h3{margin:0 0 .75rem;font-size:.95rem}.definition-list{display:grid;gap:.5rem}.definition-item{min-width:0;padding:.75rem;border-radius:8px;background:var(--diagram-box)}.definition-item header{display:flex;justify-content:space-between;gap:.5rem}.definition-item p{margin:.4rem 0;color:var(--diagram-meta);overflow-wrap:anywhere}.definition-item code{display:block;margin-top:.5rem;white-space:normal;overflow-wrap:anywhere;color:var(--diagram-column)}.definition-database-objects{grid-column:1/-1}.definition-object-group{display:grid;grid-template-columns:90px minmax(0,1fr);gap:1rem;padding:.55rem 0;border-top:1px solid var(--line)}.definition-object-group:first-of-type{border-top:0}.definition-object-group>div{display:flex;flex-wrap:wrap;gap:.5rem 1rem;min-width:0}.definition-no-value{color:var(--diagram-meta)}.visually-hidden{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}.matrix-wrap{overflow:auto;border:1px solid var(--line);border-radius:15px;background:var(--paper)}.matrix{border-collapse:collapse;min-width:100%;font-size:.85rem}.matrix th,.matrix td{padding:.8rem;border:1px solid var(--line);text-align:left;vertical-align:top}.matrix thead th{background:#e7e9e3}.crud-cell small{display:block;margin-top:.4rem}.crud-meta{display:block;margin:.35rem 0;color:var(--muted);font-size:.68rem;font-weight:800;letter-spacing:.04em}.empty-cell{color:var(--muted)}.search-label{display:block;font-weight:750;margin-bottom:.5rem}input[data-table-filter],dialog input{width:100%;padding:1rem;border:1px solid var(--line);border-radius:10px;background:var(--paper);color:var(--ink);font:inherit}dialog{width:min(720px,calc(100% - 30px));border:1px solid var(--line);border-radius:20px;padding:1.5rem;background:var(--paper);color:var(--ink)}dialog::backdrop{background:rgba(10,20,15,.48)}dialog form{text-align:right}dialog form button{border:0;background:none;color:var(--ink);font-size:1.5rem}.search-result{display:block;padding:1rem 0;border-top:1px solid var(--line);text-decoration:none}.search-result small{display:block;color:var(--muted)}footer{border-top:1px solid var(--line);padding:2rem max(22px,4vw);display:flex;justify-content:space-between;color:var(--muted);font-size:.75rem}:root[data-theme=dark]{--canvas:#101713;--ink:#edf5ef;--paper:#17211c;--line:#34453d;--green:#8bd8b9;--lime:#d9ff74;--muted:#b6c5bd;--red:#ffaaaa;--diagram-surface:#0f1813;--diagram-box:#18261f;--diagram-stroke:#557567;--diagram-title:#edf5ef;--diagram-meta:#a8bdb2;--diagram-column:#d0ded7;--diagram-key:#8bd8b9;--diagram-rel:#6ba68d;color:var(--ink);background:var(--canvas)}:root[data-theme=dark] .site-nav{background:rgba(23,33,28,.96)}:root[data-theme=dark] .stable-id,:root[data-theme=dark] .matrix thead th{background:#25342d}:root[data-theme=dark] .warning{background:#3f3518;color:#fff0b5}:root[data-theme=dark] .conflict{background:#492525;color:#ffd0d0}@media(prefers-color-scheme:dark){:root[data-theme=system],:root:not([data-theme]){--canvas:#101713;--ink:#edf5ef;--paper:#17211c;--line:#34453d;--green:#8bd8b9;--lime:#d9ff74;--muted:#b6c5bd;--red:#ffaaaa;--diagram-surface:#0f1813;--diagram-box:#18261f;--diagram-stroke:#557567;--diagram-title:#edf5ef;--diagram-meta:#a8bdb2;--diagram-column:#d0ded7;--diagram-key:#8bd8b9;--diagram-rel:#6ba68d;color:var(--ink);background:var(--canvas)}:root[data-theme=system] .site-nav,:root:not([data-theme]) .site-nav{background:rgba(23,33,28,.96)}:root[data-theme=system] .stable-id,:root[data-theme=system] .matrix thead th,:root:not([data-theme]) .stable-id,:root:not([data-theme]) .matrix thead th{background:#25342d}:root[data-theme=system] .warning,:root:not([data-theme]) .warning{background:#3f3518;color:#fff0b5}:root[data-theme=system] .conflict,:root:not([data-theme]) .conflict{background:#492525;color:#ffd0d0}}@media(max-width:900px){.display-controls label>span{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}.site-actions,.site-nav nav{gap:.55rem}.display-controls{gap:.25rem}.display-controls select{max-width:106px}}@media(max-width:760px){.site-nav nav>a{display:none}.node-header,.relation-columns{grid-template-columns:1fr}.confidence{width:100%}.evidence{grid-template-columns:1fr}.hero h1,.node-header h1,main>h1{font-size:3rem}.metric{flex-basis:160px}.table-definition-facts{grid-template-columns:repeat(2,minmax(0,1fr))}.table-definition-facts>div:nth-child(2){border-right:0}.table-definition-facts>div:nth-child(-n+2){border-bottom:1px solid var(--line)}.table-definition-details{grid-template-columns:1fr}.definition-database-objects{grid-column:auto}}@media(max-width:540px){.site-nav{align-items:flex-start}.site-actions{align-items:flex-end;flex-direction:column-reverse}.display-controls select{max-width:98px}.metric{flex-basis:130px}.er-relation-list li{grid-template-columns:1fr}.er-relation-kind{justify-content:flex-start}.table-definition-heading{align-items:flex-start;flex-direction:column}.table-definition-heading h2,.table-definition-count{margin-bottom:.5rem}.definition-object-group{grid-template-columns:1fr;gap:.35rem}}
.er-diagram{display:block}
.er-toolbar{display:flex;align-items:stretch;gap:.65rem;margin:0 0 1rem}
.er-notation-picker{display:flex;align-items:center;gap:.5rem;padding:.55rem .65rem;border:1px solid var(--line);border-radius:10px;background:var(--diagram-box);color:var(--diagram-meta);font-size:.68rem;font-weight:850;white-space:nowrap}
.er-notation-picker select{appearance:none;-webkit-appearance:none;min-width:128px;padding:.4rem 1.8rem .4rem .6rem;border:1px solid var(--diagram-stroke);border-radius:7px;background-color:var(--diagram-surface);background-image:linear-gradient(45deg,transparent 50%,var(--diagram-meta) 50%),linear-gradient(135deg,var(--diagram-meta) 50%,transparent 50%);background-position:calc(100% - 12px) calc(50% - 2px),calc(100% - 8px) calc(50% - 2px);background-repeat:no-repeat;background-size:4px 4px;color:var(--diagram-title);font:inherit;font-weight:850}
.er-legend{display:flex;align-items:center;flex:1;flex-wrap:wrap;gap:.55rem 1rem;padding:.7rem .8rem;border:1px solid var(--line);border-radius:10px;background:var(--diagram-box);color:var(--diagram-meta);font-size:.68rem}
.er-legend-group{display:none;align-items:center;flex-wrap:wrap;gap:.55rem 1rem}
.er-diagram[data-er-notation=idef1x] [data-er-legend-notation=idef1x],.er-diagram[data-er-notation=ie] [data-er-legend-notation=ie]{display:flex}
.er-legend-group>span{display:inline-flex;align-items:center;gap:.35rem}
.er-legend code{color:var(--diagram-title);font-weight:800}
.er-legend-line{display:inline-block;width:2rem;height:0;border-top:2px solid var(--diagram-rel)}
.er-legend-line.is-non-identifying{border-top-style:dashed}
.er-canvas{position:relative;isolation:isolate;padding:.5rem 2rem}
.er-table-grid{position:relative;z-index:1;display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,250px),1fr));column-gap:clamp(4rem,7vw,6.5rem);row-gap:4rem}
.er-table{position:relative;z-index:1;min-width:0;align-self:start;overflow:hidden;background:var(--diagram-box);border:1px solid var(--diagram-stroke);border-radius:12px;color:var(--diagram-title);box-shadow:0 8px 24px color-mix(in srgb,var(--diagram-stroke) 10%,transparent)}
.er-diagram[data-er-notation=idef1x] .er-table{border-radius:0}
.er-diagram[data-er-notation=idef1x] .er-table[data-er-identifier-dependent=true]{border-radius:12px}
.er-table-header{padding:.85rem 1rem;border-bottom:1px solid var(--diagram-stroke)}
.er-table-header h3{font-size:1.05rem;line-height:1.3;margin:0}
.er-table-header h3 a{color:var(--diagram-title);overflow-wrap:anywhere}
.er-table-header code{display:block;margin-top:.3rem;color:var(--diagram-meta);overflow-wrap:anywhere}
.er-column-scroll{overflow:auto}
.er-column-table{width:100%;border-collapse:collapse;font-size:.78rem}
.er-column-table th,.er-column-table td{padding:.55rem .65rem;border-bottom:1px solid var(--line);text-align:left;vertical-align:middle}
.er-column-table thead th{color:var(--diagram-meta);font-size:.67rem;letter-spacing:.05em;text-transform:uppercase}
.er-column-table tbody tr:last-child th,.er-column-table tbody tr:last-child td{border-bottom:0}
.er-column-table tbody tr[data-er-column]{position:relative;background:color-mix(in srgb,var(--diagram-rel) 5%,transparent)}
.er-diagram[data-er-notation=idef1x] .er-column-table tbody tr.er-primary-boundary th,.er-diagram[data-er-notation=idef1x] .er-column-table tbody tr.er-primary-boundary td{border-bottom:2px solid var(--diagram-stroke)}
.er-column-table tbody th{font-weight:650}
.er-column-table tbody th a{color:var(--diagram-column);white-space:nowrap}
.er-column-table code{color:var(--diagram-column);white-space:normal;overflow-wrap:anywhere}
.er-key-badge{display:inline-flex;margin:.1rem .18rem .1rem 0;padding:.12rem .32rem;border:1px solid var(--diagram-key);border-radius:999px;color:var(--diagram-key);font-size:.61rem;font-weight:850;line-height:1.2}
.er-key-badge-reference{border-style:dashed;color:var(--diagram-meta)}
.er-table-empty{padding:1rem;color:var(--diagram-meta)}
.er-table-footer{padding:.7rem .8rem;display:flex;align-items:center;justify-content:space-between;gap:.6rem;border-top:1px solid var(--line);background:var(--diagram-surface);font-size:.68rem}
.er-table-footer a{font-weight:750;text-align:right}
.er-column-count{color:var(--diagram-meta);white-space:nowrap}
.er-relation-layer{position:absolute;z-index:2;inset:0;width:100%;height:100%;overflow:visible;pointer-events:none}
.er-connector-halo{fill:none;stroke:var(--diagram-surface);stroke-width:7;stroke-linecap:round;stroke-linejoin:round}
.er-connector-line{fill:none;stroke:var(--diagram-rel);stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.er-connector-line.is-non-identifying{stroke-dasharray:7 5}
.er-idef-child{fill:var(--diagram-rel);stroke:var(--diagram-surface);stroke-width:3}
.er-idef-optional-parent{fill:var(--diagram-surface);stroke:var(--diagram-rel);stroke-width:2}
.er-idef-cardinality{fill:var(--diagram-title);stroke:var(--diagram-surface);stroke-width:5;paint-order:stroke fill;font-family:ui-monospace,SFMono-Regular,monospace;font-size:11px;font-weight:900}
.er-ie-marker{fill:none;stroke:var(--diagram-rel);stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.er-ie-zero{fill:var(--diagram-surface);stroke:var(--diagram-rel);stroke-width:2}
.er-inline [data-table].is-filtered,.er-frame [data-table].is-filtered{display:none}
@media(max-width:760px){.er-toolbar{align-items:stretch;flex-direction:column}.er-notation-picker{justify-content:space-between}.er-canvas{padding:.5rem 1.25rem}.er-table-grid{grid-template-columns:1fr;row-gap:3.5rem}.er-table-footer{align-items:flex-start;flex-direction:column}.er-table-footer a{text-align:left}}
@media(max-width:540px){.er-frame{padding:.65rem}.er-canvas{padding:.25rem 1.5rem}.er-legend,.er-legend-group{align-items:flex-start;flex-direction:column}.er-table-grid{row-gap:3rem}}
.table-definition-comment{display:grid;grid-template-columns:140px minmax(0,1fr);gap:1rem;margin:0 0 1rem;padding:.75rem 1rem;border-left:3px solid var(--green);background:var(--diagram-surface)}
.table-definition-comment strong{font-size:.72rem;color:var(--diagram-meta)}
.table-definition-comment p{margin:0;overflow-wrap:anywhere}
.definition-application-usage{grid-column:1/-1}
@media(max-width:760px){.definition-application-usage{grid-column:auto}}
@media(max-width:540px){.table-definition-comment{grid-template-columns:1fr;gap:.3rem}}
.screen-map-panel{overflow:hidden}
.screen-map{position:relative;isolation:isolate;min-height:260px;padding:1.25rem;border:1px solid var(--line);border-radius:14px;background:var(--diagram-surface);overflow:hidden}
.screen-map-grid{position:relative;z-index:1;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:5rem 4rem}
.screen-map-node{display:flex;min-width:0;overflow:hidden;flex-direction:column;border:1px solid var(--diagram-stroke);border-radius:12px;background:var(--diagram-box);color:var(--diagram-title);text-decoration:none;box-shadow:0 10px 28px color-mix(in srgb,var(--diagram-stroke) 14%,transparent)}
.screen-map-node:hover{border-color:var(--accent)}
.screen-map-media{display:flex;aspect-ratio:1.44;align-items:center;justify-content:center;overflow:hidden;border-bottom:1px solid var(--line);background:var(--raised)}
.screen-map-media img{display:block;width:100%;height:100%;object-fit:cover;object-position:top}
.screen-map-placeholder{padding:1rem;color:var(--diagram-meta);font-size:.72rem;text-align:center}
.screen-map-copy{display:flex;min-width:0;flex-direction:column;gap:.3rem;padding:.85rem 1rem}
.screen-map-copy strong,.screen-map-copy code{overflow-wrap:anywhere}
.screen-map-copy strong{font-size:1rem}
.screen-map-copy code,.screen-map-copy small{color:var(--diagram-meta);font-size:.68rem}
.screen-map-connectors{position:absolute;z-index:0;inset:0;width:100%;height:100%;overflow:visible;pointer-events:none}
.screen-map-connector-halo{fill:none;stroke:var(--diagram-surface);stroke-width:9;stroke-linecap:round}
.screen-map-connector-line{fill:none;stroke:var(--diagram-rel);stroke-width:2.5;stroke-linecap:round;marker-end:url(#screen-map-arrow)}
.screen-map-arrow{fill:var(--diagram-rel)}
.screen-transition-diagram,.action-transition-diagram{display:grid;gap:.85rem}
.screen-transition,.action-transition{display:grid;align-items:stretch;gap:.85rem;padding:.85rem;border:1px solid var(--line);border-radius:14px;background:var(--diagram-surface)}
.screen-transition{grid-template-columns:minmax(0,1fr) minmax(130px,.45fr) minmax(0,1fr)}
.action-transition{grid-template-columns:minmax(160px,.8fr) minmax(260px,1.5fr) 36px minmax(160px,.8fr)}
.transition-state{display:flex;min-width:0;flex-direction:column;gap:.35rem;padding:.9rem;border:1px solid var(--diagram-stroke);border-radius:10px;background:var(--diagram-box);color:var(--diagram-title);text-decoration:none}
.transition-state small{color:var(--diagram-meta);font-size:.65rem;font-weight:850;letter-spacing:.08em;text-transform:uppercase}
.transition-state strong,.transition-state code,.transition-state span{overflow-wrap:anywhere}
.transition-state strong{font-size:1rem}
.transition-state code,.transition-state span{color:var(--diagram-meta);font-size:.72rem}
.transition-link{display:flex;min-width:0;align-items:center;justify-content:center;flex-direction:column;gap:.4rem;color:var(--diagram-rel);text-align:center}
.transition-arrow{display:flex;align-items:center;justify-content:center;color:var(--diagram-rel);font-size:1.8rem;font-weight:900}
.transition-scenarios{font-size:.68rem;line-height:1.35}
.transition-scenarios a{overflow-wrap:anywhere}
.transition-action{display:flex;min-width:0;flex-direction:column;justify-content:center;gap:.55rem;padding:.85rem 1rem;border-left:3px solid var(--diagram-rel);background:var(--diagram-box)}
.transition-action header{display:flex;min-width:0;align-items:baseline;flex-wrap:wrap;gap:.45rem}
.transition-action header>a{font-weight:850;overflow-wrap:anywhere}
.transition-action header>code{color:var(--diagram-meta)}
.transition-sequence{display:inline-flex;padding:.15rem .42rem;border-radius:999px;background:var(--raised);color:var(--diagram-title);font-size:.65rem;font-weight:900}
.transition-conditions{display:flex;flex-wrap:wrap;gap:.35rem}
.transition-conditions>span{display:inline-flex;gap:.25rem;padding:.2rem .45rem;border:1px solid var(--line);border-radius:999px;color:var(--diagram-meta);font-size:.65rem}
.transition-conditions>span strong{color:var(--diagram-title)}
.transition-conditions .branch-count{border-color:var(--diagram-rel);color:var(--diagram-title)}
.transition-http-list{display:grid;gap:.35rem}
.transition-http-list>small{color:var(--diagram-meta);font-size:.65rem;font-weight:850}
.transition-http{display:flex;min-width:0;justify-content:space-between;gap:.65rem;padding:.4rem .55rem;border:1px solid var(--line);border-radius:7px;text-decoration:none}
.transition-http code{overflow-wrap:anywhere}
.transition-http span{color:var(--diagram-meta);font-size:.68rem;white-space:nowrap}
@media(max-width:1000px){.screen-map-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:820px){.screen-transition,.action-transition{grid-template-columns:1fr}.transition-link,.action-transition>.transition-arrow{min-height:30px}.transition-action{border-left:0;border-top:3px solid var(--diagram-rel)}}
@media(max-width:620px){.screen-map{padding:.5rem}.screen-map-grid{grid-template-columns:1fr;gap:4rem}.screen-map-node{max-width:260px;width:100%;justify-self:center}}
:root{
--canvas:var(--mandala-light-page,#f1eadc);
--paper:var(--mandala-light-paper,#fbf7ed);
--ink:var(--mandala-light-ink,#1d1c22);
--body:var(--mandala-light-body,#46413c);
--muted:var(--mandala-light-muted,#70685e);
--line:var(--mandala-light-line,#d2c7b5);
--accent:var(--mandala-light-accent,#a33e32);
--accent-strong:var(--mandala-light-accent-strong,#853029);
--gold:var(--mandala-light-gold,#b78332);
--indigo:var(--mandala-light-indigo,#191d35);
--jade:var(--mandala-light-jade,#4e685e);
--green:var(--accent);
--lime:var(--gold);
--red:var(--mandala-light-danger,#a33d3d);
--header-bg:var(--mandala-light-header-bg,rgba(251,247,237,.94));
--raised:var(--mandala-light-raised,#e7dfd0);
--warning-bg:var(--mandala-light-warning-bg,#eee2ce);
--warning-ink:var(--mandala-light-warning-ink,#5d302b);
--conflict-bg:var(--mandala-light-conflict-bg,#f1d5d0);
--conflict-ink:var(--mandala-light-conflict-ink,#702323);
--diagram-surface:var(--mandala-light-diagram-surface,#f3ecdf);
--diagram-box:var(--mandala-light-diagram-box,#fbf7ed);
--diagram-stroke:var(--mandala-light-diagram-stroke,#9c9284);
--diagram-title:var(--ink);
--diagram-meta:var(--muted);
--diagram-column:var(--body);
--diagram-key:var(--accent);
--diagram-rel:var(--jade)
}
.site-nav{background:var(--header-bg);backdrop-filter:blur(16px)}
.stable-id,.matrix thead th{background:var(--raised)}
.warning{background:var(--warning-bg);color:var(--warning-ink)}
.conflict{background:var(--conflict-bg);color:var(--conflict-ink)}
.custom-section{border-color:var(--accent)}
.empty{border-color:var(--line)}
dialog::backdrop{background:color-mix(in srgb,var(--indigo) 68%,transparent)}
:root[data-theme=dark]{
--canvas:var(--mandala-dark-page,#0e0f17);
--paper:var(--mandala-dark-paper,#171922);
--ink:var(--mandala-dark-ink,#f3ead9);
--body:var(--mandala-dark-body,#d4c9ba);
--muted:var(--mandala-dark-muted,#aaa294);
--line:var(--mandala-dark-line,#3a3b45);
--accent:var(--mandala-dark-accent,#df7867);
--accent-strong:var(--mandala-dark-accent-strong,#8d352e);
--gold:var(--mandala-dark-gold,#d8aa55);
--indigo:var(--mandala-dark-indigo,#171a2d);
--jade:var(--mandala-dark-jade,#90a99e);
--green:var(--accent);
--lime:var(--gold);
--red:var(--mandala-dark-danger,#ffaaaa);
--header-bg:var(--mandala-dark-header-bg,rgba(23,25,34,.94));
--raised:var(--mandala-dark-raised,#282934);
--warning-bg:var(--mandala-dark-warning-bg,#2c2519);
--warning-ink:var(--mandala-dark-warning-ink,#f1d796);
--conflict-bg:var(--mandala-dark-conflict-bg,#3a2022);
--conflict-ink:var(--mandala-dark-conflict-ink,#ffc4bd);
--diagram-surface:var(--mandala-dark-diagram-surface,#11131d);
--diagram-box:var(--mandala-dark-diagram-box,#171922);
--diagram-stroke:var(--mandala-dark-diagram-stroke,#555660);
--diagram-title:var(--ink);
--diagram-meta:var(--muted);
--diagram-column:var(--body);
--diagram-key:var(--accent);
--diagram-rel:var(--jade)
}
:root[data-theme=dark] .site-nav{background:var(--header-bg)}
:root[data-theme=dark] .stable-id,:root[data-theme=dark] .matrix thead th{background:var(--raised)}
:root[data-theme=dark] .warning{background:var(--warning-bg);color:var(--warning-ink)}
:root[data-theme=dark] .conflict{background:var(--conflict-bg);color:var(--conflict-ink)}
@media(prefers-color-scheme:dark){
:root[data-theme=system],:root:not([data-theme]){
--canvas:var(--mandala-dark-page,#0e0f17);
--paper:var(--mandala-dark-paper,#171922);
--ink:var(--mandala-dark-ink,#f3ead9);
--body:var(--mandala-dark-body,#d4c9ba);
--muted:var(--mandala-dark-muted,#aaa294);
--line:var(--mandala-dark-line,#3a3b45);
--accent:var(--mandala-dark-accent,#df7867);
--accent-strong:var(--mandala-dark-accent-strong,#8d352e);
--gold:var(--mandala-dark-gold,#d8aa55);
--indigo:var(--mandala-dark-indigo,#171a2d);
--jade:var(--mandala-dark-jade,#90a99e);
--green:var(--accent);
--lime:var(--gold);
--red:var(--mandala-dark-danger,#ffaaaa);
--header-bg:var(--mandala-dark-header-bg,rgba(23,25,34,.94));
--raised:var(--mandala-dark-raised,#282934);
--warning-bg:var(--mandala-dark-warning-bg,#2c2519);
--warning-ink:var(--mandala-dark-warning-ink,#f1d796);
--conflict-bg:var(--mandala-dark-conflict-bg,#3a2022);
--conflict-ink:var(--mandala-dark-conflict-ink,#ffc4bd);
--diagram-surface:var(--mandala-dark-diagram-surface,#11131d);
--diagram-box:var(--mandala-dark-diagram-box,#171922);
--diagram-stroke:var(--mandala-dark-diagram-stroke,#555660);
--diagram-title:var(--ink);
--diagram-meta:var(--muted);
--diagram-column:var(--body);
--diagram-key:var(--accent);
--diagram-rel:var(--jade)
}
:root[data-theme=system] .site-nav,:root:not([data-theme]) .site-nav{background:var(--header-bg)}
:root[data-theme=system] .stable-id,:root[data-theme=system] .matrix thead th,:root:not([data-theme]) .stable-id,:root:not([data-theme]) .matrix thead th{background:var(--raised)}
:root[data-theme=system] .warning,:root:not([data-theme]) .warning{background:var(--warning-bg);color:var(--warning-ink)}
:root[data-theme=system] .conflict,:root:not([data-theme]) .conflict{background:var(--conflict-bg);color:var(--conflict-ink)}
}
"""; }

    private String javascript() { return """
(() => {
  const root = document.documentElement;
  const storage = {
    get(key, fallback) {
      try { return localStorage.getItem(key) || fallback; } catch { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem(key, value); } catch { /* Storage can be disabled. */ }
    }
  };
  const english = {
    'controls.language': 'Language',
    'controls.theme': 'Theme',
    'theme.system': 'System',
    'theme.light': 'Light',
    'theme.dark': 'Dark',
    'search.open': 'Open search',
    'search.close': 'Close',
    'search.label': 'Search the Documentation Graph',
    'search.placeholder': 'Endpoint, Table, Stable ID…',
    'home.description': 'Bidirectionally connects screens, execution paths, Java, SQL, and PostgreSQL with supporting Evidence.',
    'home.e2e': 'E2E flows',
    'metrics.e2e': 'E2E flows',
    'metrics.screens': 'Screens',
    'metrics.endpoints': 'Endpoints',
    'metrics.symbols': 'Java symbols',
    'metrics.sql': 'SQL',
    'metrics.tables': 'Tables',
    'metrics.warnings': 'Warnings',
    'metrics.stale': 'Stale',
    'metrics.conflicts': 'Conflicts',
    'diff.majorChanges': 'Major changes since the previous analysis',
    'diff.noChanges': 'There are no semantic changes.',
    'diff.openReport': 'Open the diff report',
    'diff.added': 'Added',
    'diff.removed': 'Removed',
    'diff.modified': 'Modified',
    'diff.newItem': 'New item',
    'diff.deleted': 'Deleted',
    'diff.impact': 'Reverse-index impact',
    'empty.e2e': 'No E2E flows have been discovered.',
    'empty.category': 'There are no Nodes in this category.',
    'empty.attributes': 'There are no structured attributes.',
    'empty.evidence': 'Evidence is not available.',
    'empty.runtime': 'No Runtime Trace was observed for this scenario.',
    'empty.tables': 'There are no related Tables.',
    'empty.erTables': 'There are no Tables available for this ER diagram.',
    'empty.columns': 'There are no Columns.',
    'empty.relatedE2e': 'There are no related E2E flows.',
    'empty.relationships': 'There are no relationships.',
    'empty.screenTransitions': 'No observed screen-to-screen transitions are available.',
    'empty.actionTransitions': 'No action-level state transitions have been observed yet.',
    'empty.report': 'There are no matching items.',
    'collection.summary': 'nodes · reproducible list ordered by Stable ID',
    'node.warnings': 'Warnings',
    'node.conflicts': 'Conflicts requiring review',
    'node.specification': 'Specification',
    'node.forward': 'Follow from this item',
    'node.reverse': 'Items that use this item',
    'flow.runtime': 'Observed execution path',
    'flow.crudEr': 'CRUD and partial ER',
    'table.definition': 'Table definition',
    'table.columns': 'Columns',
    'table.tableComment': 'Table comment',
    'table.schema': 'Schema',
    'table.tableName': 'Table name',
    'table.owner': 'Owner',
    'table.rls': 'Row-level security',
    'table.enabled': 'Enabled',
    'table.disabled': 'Disabled',
    'table.column': 'Column',
    'table.dataType': 'Data type',
    'table.nullable': 'Nullable',
    'table.default': 'Default',
    'table.keysIndexes': 'Keys / indexes',
    'table.comment': 'Comment',
    'table.constraints': 'Constraints',
    'table.indexes': 'Indexes',
    'table.databaseObjects': 'Database objects',
    'table.referencedBy': 'Referenced by',
    'table.triggers': 'Triggers',
    'table.policies': 'Policies',
    'table.functions': 'Functions',
    'table.applicationUsage': 'Application usage',
    'table.relatedSql': 'Related SQL',
    'table.relatedDaos': 'Related DAOs',
    'table.relatedServices': 'Related Application Services',
    'table.relatedE2e': 'Related E2E flows',
    'screenshots.title': 'Screen captures',
    'transitions.nav': 'Screen transitions',
    'transitions.title': 'Observed screen transition diagram',
    'transitions.description': 'An overview that places E2E-observed screens as screenshot-backed Nodes and connects NAVIGATES_TO relationships with lines. Open a Screen for individual transitions and internal states.',
    'transitions.overview': 'Screen connection map',
    'transitions.overviewDescription': 'Select a Screen to inspect its one-to-one transitions, states, actions, conditional outcomes, and related HTTP calls.',
    'transitions.noScreenshot': 'No screenshot observed',
    'transitions.stateCount': '{0} states',
    'transitions.screenDetailTitle': 'Transitions for this Screen',
    'transitions.screenDetailDescription': 'One-to-one screen transitions observed by E2E scenarios where this Screen is the source or destination.',
    'transitions.screenActionTitle': 'States, actions, and conditional outcomes for this Screen',
    'transitions.actionDescription': 'Shows each action’s source state, target state, sequence, role, feature flags, outcome, and related HTTP calls.',
    'transitions.from': 'From',
    'transitions.to': 'To',
    'transitions.relatedHttp': 'Related HTTP',
    'transitions.branchCount': '{0} outcomes',
    'crud.title': 'CRUD matrix',
    'crud.description': 'Navigate bidirectionally from each cell to E2E, Endpoint, Service, DAO, SQL, Table, Column, and Trace pages. Classification uses SQL and observations rather than HTTP Method.',
    'er.title': 'ER diagram',
    'er.search': 'Search Tables',
    'er.diagram': 'Entity relationship diagram',
    'er.columns': 'Columns',
    'er.column': 'Column',
    'er.keys': 'Key',
    'er.dataType': 'Data type',
    'er.noKey': 'No key',
    'er.relationships': 'Relationships',
    'er.notation': 'Notation',
    'er.notationAria': 'ER relationship notation',
    'er.identifying': 'Identifying',
    'er.nonIdentifying': 'Non-identifying',
    'er.idefEndpoints': 'child (default 0..*) / optional parent',
    'er.idefCardinality': 'zero or one / one or more',
    'er.many': 'zero or more',
    'er.optionalOne': 'zero or one',
    'er.exactlyOne': 'exactly one',
    'er.keyColumns': 'Relationship keys',
    'er.noRelationshipColumns': 'No relationship keys',
    'er.openTableColumns': 'Open the Table page for all Columns',
    'report.evidenceDescription': 'Lists the Evidence, source, and Confidence for each item.',
    'report.staleDescription': 'Explanations that require confirmation after their source implementation changed.',
    'report.conflictDescription': 'Source conflicts that require human or Agent review.',
    'report.diffTitle': 'Changes since the previous analysis',
    'report.diffEmpty': 'Semantic diff excludes timestamps and JSON ordering. There are no semantic changes.',
    'report.diffDescription': 'Semantic diff excludes timestamps and JSON ordering. node +{0} / -{1} / ~{2}, edge +{3} / -{4} / ~{5}, impacted candidates {6}.',
    'report.item': 'Item',
    'report.type': 'Type',
    'report.stateEvidence': 'State / Evidence',
    'report.sourceId': 'Source / Stable ID'
  };
  const originalText = new Map();
  const originalAttributes = new Map();

  document.querySelectorAll('[data-i18n],[data-i18n-template]').forEach((element) => {
    originalText.set(element, element.textContent);
  });
  for (const attribute of ['aria-label', 'placeholder']) {
    document.querySelectorAll(`[data-i18n-${attribute}]`).forEach((element) => {
      originalAttributes.set(`${attribute}:${originalAttributes.size}`, { element, attribute, value: element.getAttribute(attribute) || '' });
    });
  }

  function translateTemplate(template, element) {
    const values = (element.dataset.i18nValues || '').split(',');
    return values.reduce((text, value, position) => text.replaceAll(`{${position}}`, value), template);
  }

  function applyLanguage(locale) {
    const language = locale === 'en' ? 'en' : 'ja';
    root.lang = language;
    root.dataset.locale = language;
    document.querySelectorAll('[data-i18n]').forEach((element) => {
      const translated = english[element.dataset.i18n];
      element.textContent = language === 'en' && translated ? translated : originalText.get(element);
    });
    document.querySelectorAll('[data-i18n-template]').forEach((element) => {
      const translated = english[element.dataset.i18nTemplate];
      element.textContent = language === 'en' && translated
        ? translateTemplate(translated, element)
        : originalText.get(element);
    });
    originalAttributes.forEach(({ element, attribute, value }) => {
      const key = element.dataset[`i18n${attribute === 'aria-label' ? 'AriaLabel' : 'Placeholder'}`];
      element.setAttribute(attribute, language === 'en' && english[key] ? english[key] : value);
    });
    const selector = document.querySelector('[data-language]');
    if (selector) selector.value = language;
    storage.set('mandala.language', language);
  }

  const themeSelect = document.querySelector('[data-theme-select]');
  const theme = storage.get('mandala.theme', 'system');
  root.dataset.theme = ['system', 'light', 'dark'].includes(theme) ? theme : 'system';
  if (themeSelect) themeSelect.value = root.dataset.theme;
  themeSelect?.addEventListener('change', () => {
    root.dataset.theme = themeSelect.value;
    storage.set('mandala.theme', themeSelect.value);
  });

  const languageSelect = document.querySelector('[data-language]');
  applyLanguage(storage.get('mandala.language', 'ja'));
  languageSelect?.addEventListener('change', () => applyLanguage(languageSelect.value));

  const open = document.querySelector('[data-search-open]');
  const dialog = document.querySelector('[data-search]');
  const input = document.querySelector('#mandala-search');
  const results = document.querySelector('[data-search-results]');
  let index = [];
  const stylesheet = document.querySelector('link[href$="assets/mandala.css"]');
  const prefix = stylesheet ? stylesheet.getAttribute('href').replace('assets/mandala.css', '') : '';
  open?.addEventListener('click', async () => {
    if (!index.length) index = await fetch(prefix + 'search-index.json').then((response) => response.json());
    dialog.showModal();
    input.focus();
  });
  input?.addEventListener('input', () => {
    const query = input.value.toLowerCase().trim();
    results.innerHTML = query
      ? index.filter((entry) => `${entry.title} ${entry.id} ${entry.type} ${entry.description}`.toLowerCase().includes(query))
        .slice(0, 30)
        .map((entry) => `<a class="search-result" href="${prefix}${escapeHtml(entry.url)}"><strong>${escapeHtml(entry.title)}</strong><small>${escapeHtml(entry.type)} · ${escapeHtml(entry.id)}</small></a>`)
        .join('')
      : '';
  });
  const svgNamespace = 'http://www.w3.org/2000/svg';
  const erDiagrams = Array.from(document.querySelectorAll('[data-er-diagram]'));
  const screenMaps = Array.from(document.querySelectorAll('[data-screen-map]'));
  let erAnimationFrame = 0;
  let screenMapAnimationFrame = 0;
  function svgElement(name, attributes, text) {
    const element = document.createElementNS(svgNamespace, name);
    Object.entries(attributes).forEach(([key, value]) => element.setAttribute(key, String(value)));
    if (text !== undefined) element.textContent = text;
    return element;
  }
  function drawScreenMap(map) {
    const svg = map.querySelector('[data-screen-connectors]');
    if (!svg) return;
    const mapBounds = map.getBoundingClientRect();
    const width = Math.max(1, map.clientWidth);
    const height = Math.max(1, map.clientHeight);
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    svg.querySelectorAll('.screen-map-connector').forEach((connector) => connector.remove());
    const nodes = new Map(Array.from(map.querySelectorAll('[data-screen-node]'))
      .map((node) => [node.dataset.screenNode, node]));
    map.querySelectorAll('[data-screen-edge]').forEach((edge, edgeIndex) => {
      const from = nodes.get(edge.dataset.from);
      const to = nodes.get(edge.dataset.to);
      if (!from || !to || from === to) return;
      const fromBounds = from.getBoundingClientRect();
      const toBounds = to.getBoundingClientRect();
      const fromCenterX = fromBounds.left - mapBounds.left + fromBounds.width / 2;
      const fromCenterY = fromBounds.top - mapBounds.top + fromBounds.height / 2;
      const toCenterX = toBounds.left - mapBounds.left + toBounds.width / 2;
      const toCenterY = toBounds.top - mapBounds.top + toBounds.height / 2;
      const deltaX = toCenterX - fromCenterX;
      const deltaY = toCenterY - fromCenterY;
      let fromX;
      let fromY;
      let toX;
      let toY;
      let path;
      const useMobileGutter = width <= 620 && Math.abs(deltaX) < fromBounds.width / 2;
      if (useMobileGutter) {
        const direction = edgeIndex % 2 === 0 ? -1 : 1;
        fromX = fromCenterX + direction * fromBounds.width / 2;
        fromY = fromCenterY;
        toX = toCenterX + direction * toBounds.width / 2;
        toY = toCenterY;
        const routeX = direction < 0 ? 8 : width - 8;
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} H ${routeX.toFixed(1)} V ${toY.toFixed(1)} H ${toX.toFixed(1)}`;
      } else if (Math.abs(deltaX) >= Math.abs(deltaY)) {
        const direction = deltaX >= 0 ? 1 : -1;
        fromX = fromCenterX + direction * fromBounds.width / 2;
        fromY = fromCenterY;
        toX = toCenterX - direction * toBounds.width / 2;
        toY = toCenterY;
        const control = Math.max(36, Math.abs(toX - fromX) * .45);
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} C ${(fromX + direction * control).toFixed(1)} ${fromY.toFixed(1)}, ${(toX - direction * control).toFixed(1)} ${toY.toFixed(1)}, ${toX.toFixed(1)} ${toY.toFixed(1)}`;
      } else {
        const direction = deltaY >= 0 ? 1 : -1;
        fromX = fromCenterX;
        fromY = fromCenterY + direction * fromBounds.height / 2;
        toX = toCenterX;
        toY = toCenterY - direction * toBounds.height / 2;
        const control = Math.max(36, Math.abs(toY - fromY) * .45);
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} C ${fromX.toFixed(1)} ${(fromY + direction * control).toFixed(1)}, ${toX.toFixed(1)} ${(toY - direction * control).toFixed(1)}, ${toX.toFixed(1)} ${toY.toFixed(1)}`;
      }
      const group = svgElement('g', { class: 'screen-map-connector' });
      group.append(
        svgElement('path', { class: 'screen-map-connector-halo', d: path }),
        svgElement('path', { class: 'screen-map-connector-line', d: path })
      );
      svg.append(group);
    });
  }
  function queueScreenMapDraw() {
    if (screenMapAnimationFrame) cancelAnimationFrame(screenMapAnimationFrame);
    screenMapAnimationFrame = requestAnimationFrame(() => {
      screenMapAnimationFrame = 0;
      screenMaps.forEach(drawScreenMap);
    });
  }
  if (screenMaps.length) {
    queueScreenMapDraw();
    window.addEventListener('resize', queueScreenMapDraw, { passive: true });
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(queueScreenMapDraw);
      screenMaps.forEach((map) => observer.observe(map));
    }
    document.fonts?.ready.then(queueScreenMapDraw);
    screenMaps.forEach((map) => map.querySelectorAll('img')
      .forEach((image) => image.addEventListener('load', queueScreenMapDraw, { once: true })));
  }
  function appendIdef1xMarkers(group, relation, fromX, fromY, fromDirection, toX, toY, toDirection) {
    group.append(svgElement('circle', {
      class: 'er-idef-child',
      cx: fromX.toFixed(1),
      cy: fromY.toFixed(1),
      r: 4
    }));
    const cardinality = relation.dataset.erFromCardinality;
    const cardinalityCode = cardinality === '0..*' ? ''
      : cardinality === '0..1' ? 'Z'
        : cardinality === '1..*' ? 'P'
          : cardinality;
    if (cardinalityCode) {
      group.append(svgElement('text', {
        class: 'er-idef-cardinality',
        x: (fromX + fromDirection * 9).toFixed(1),
        y: (fromY - 7).toFixed(1),
        'text-anchor': fromDirection > 0 ? 'start' : 'end'
      }, cardinalityCode));
    }
    if (relation.dataset.erToCardinality === '0..1') {
      const centerX = toX + toDirection * 4;
      group.append(svgElement('polygon', {
        class: 'er-idef-optional-parent',
        points: `${centerX.toFixed(1)},${(toY - 5).toFixed(1)} ${(centerX + toDirection * 5).toFixed(1)},${toY.toFixed(1)} ${centerX.toFixed(1)},${(toY + 5).toFixed(1)} ${(centerX - toDirection * 5).toFixed(1)},${toY.toFixed(1)}`
      }));
    }
  }
  function appendIeMarker(group, cardinality, x, y, direction) {
    const many = cardinality.endsWith('*');
    const required = cardinality.startsWith('1');
    if (many) {
      const junctionX = x + direction * 10;
      group.append(
        svgElement('line', {
          class: 'er-ie-marker',
          x1: x.toFixed(1), y1: (y - 6).toFixed(1),
          x2: junctionX.toFixed(1), y2: y.toFixed(1)
        }),
        svgElement('line', {
          class: 'er-ie-marker',
          x1: x.toFixed(1), y1: y.toFixed(1),
          x2: junctionX.toFixed(1), y2: y.toFixed(1)
        }),
        svgElement('line', {
          class: 'er-ie-marker',
          x1: x.toFixed(1), y1: (y + 6).toFixed(1),
          x2: junctionX.toFixed(1), y2: y.toFixed(1)
        })
      );
    } else {
      const maximumX = x + direction * 4;
      group.append(svgElement('line', {
        class: 'er-ie-marker',
        x1: maximumX.toFixed(1), y1: (y - 6).toFixed(1),
        x2: maximumX.toFixed(1), y2: (y + 6).toFixed(1)
      }));
    }
    const minimumX = x + direction * 18;
    if (required) {
      group.append(svgElement('line', {
        class: 'er-ie-marker',
        x1: minimumX.toFixed(1), y1: (y - 6).toFixed(1),
        x2: minimumX.toFixed(1), y2: (y + 6).toFixed(1)
      }));
    } else {
      group.append(svgElement('circle', {
        class: 'er-ie-zero',
        cx: minimumX.toFixed(1),
        cy: y.toFixed(1),
        r: 4
      }));
    }
  }
  function drawRelationshipDiagram(diagram) {
    const canvas = diagram.querySelector('[data-er-canvas]');
    const svg = diagram.querySelector('[data-er-connectors]');
    if (!canvas || !svg) return;
    const canvasBounds = canvas.getBoundingClientRect();
    const width = Math.max(1, canvas.clientWidth);
    const height = Math.max(1, canvas.clientHeight);
    const notation = diagram.dataset.erNotation === 'ie' ? 'ie' : 'idef1x';
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    svg.replaceChildren();

    const tables = new Map(Array.from(diagram.querySelectorAll('[data-er-table]'))
      .map((table) => [table.dataset.erTable, table]));
    const columns = new Map(Array.from(diagram.querySelectorAll('[data-er-column]'))
      .map((column) => [column.dataset.erColumn, column]));
    const relations = Array.from(diagram.querySelectorAll('[data-er-relation]'));
    const endpointRelations = new Map();
    relations.forEach((relation) => {
      for (const side of ['From', 'To']) {
        const table = relation.dataset[`er${side}Table`];
        const column = relation.dataset[`er${side}Column`] || '@table';
        const key = `${table}\u0000${column}`;
        if (!endpointRelations.has(key)) endpointRelations.set(key, []);
        endpointRelations.get(key).push(relation);
      }
    });
    function endpointOffset(relation, side) {
      const table = relation.dataset[`er${side}Table`];
      const column = relation.dataset[`er${side}Column`] || '@table';
      const related = endpointRelations.get(`${table}\u0000${column}`) || [relation];
      const spacing = Math.min(13, 30 / Math.max(1, related.length - 1));
      return (related.indexOf(relation) - (related.length - 1) / 2) * spacing;
    }

    relations.forEach((relation) => {
      const fromTable = tables.get(relation.dataset.erFromTable);
      const toTable = tables.get(relation.dataset.erToTable);
      if (!fromTable || !toTable || fromTable.classList.contains('is-filtered')
          || toTable.classList.contains('is-filtered')) return;
      const fromEndpoint = columns.get(relation.dataset.erFromColumn)
        || fromTable.querySelector('[data-er-table-anchor]');
      const toEndpoint = columns.get(relation.dataset.erToColumn)
        || toTable.querySelector('[data-er-table-anchor]');
      if (!fromEndpoint || !toEndpoint) return;

      const fromCardBounds = fromTable.getBoundingClientRect();
      const toCardBounds = toTable.getBoundingClientRect();
      const fromEndpointBounds = fromEndpoint.getBoundingClientRect();
      const toEndpointBounds = toEndpoint.getBoundingClientRect();
      if (!fromCardBounds.width || !toCardBounds.width) return;

      const fromCenter = fromCardBounds.left + fromCardBounds.width / 2;
      const toCenter = toCardBounds.left + toCardBounds.width / 2;
      const fromY = fromEndpointBounds.top + fromEndpointBounds.height / 2 - canvasBounds.top
        + endpointOffset(relation, 'From');
      const toY = toEndpointBounds.top + toEndpointBounds.height / 2 - canvasBounds.top
        + endpointOffset(relation, 'To');
      const separatedHorizontally = fromCardBounds.right + 8 < toCardBounds.left
        || toCardBounds.right + 8 < fromCardBounds.left;
      let fromX;
      let toX;
      let path;
      let fromDirection;
      let toDirection;

      if (separatedHorizontally) {
        const direction = fromCenter < toCenter ? 1 : -1;
        fromDirection = direction;
        toDirection = -direction;
        fromX = (direction > 0 ? fromCardBounds.right : fromCardBounds.left) - canvasBounds.left;
        toX = (direction > 0 ? toCardBounds.left : toCardBounds.right) - canvasBounds.left;
        const control = Math.max(28, Math.abs(toX - fromX) * .45);
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} C ${(fromX + direction * control).toFixed(1)} ${fromY.toFixed(1)}, ${(toX - direction * control).toFixed(1)} ${toY.toFixed(1)}, ${toX.toFixed(1)} ${toY.toFixed(1)}`;
      } else {
        const rightEdge = Math.max(fromCardBounds.right, toCardBounds.right) - canvasBounds.left;
        const leftEdge = Math.min(fromCardBounds.left, toCardBounds.left) - canvasBounds.left;
        const useRight = rightEdge + 24 <= width;
        fromDirection = useRight ? 1 : -1;
        toDirection = fromDirection;
        const routeX = useRight ? rightEdge + 20 : Math.max(2, leftEdge - 20);
        fromX = (useRight ? fromCardBounds.right : fromCardBounds.left) - canvasBounds.left;
        toX = (useRight ? toCardBounds.right : toCardBounds.left) - canvasBounds.left;
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} H ${routeX.toFixed(1)} V ${toY.toFixed(1)} H ${toX.toFixed(1)}`;
      }

      const group = svgElement('g', { class: 'er-connector' });
      const relationshipClass = relation.dataset.erIdentifying === 'true'
        ? 'is-identifying'
        : 'is-non-identifying';
      group.append(
        svgElement('path', { class: 'er-connector-halo', d: path }),
        svgElement('path', { class: `er-connector-line ${relationshipClass}`, d: path })
      );
      if (notation === 'idef1x') {
        appendIdef1xMarkers(
          group, relation, fromX, fromY, fromDirection, toX, toY, toDirection);
      } else {
        appendIeMarker(group, relation.dataset.erFromCardinality, fromX, fromY, fromDirection);
        appendIeMarker(group, relation.dataset.erToCardinality, toX, toY, toDirection);
      }
      svg.append(group);
    });
  }
  function queueRelationshipDraw() {
    if (erAnimationFrame) cancelAnimationFrame(erAnimationFrame);
    erAnimationFrame = requestAnimationFrame(() => {
      erAnimationFrame = 0;
      erDiagrams.forEach(drawRelationshipDiagram);
    });
  }
  if (erDiagrams.length) {
    erDiagrams.forEach((diagram) => {
      const notationSelect = diagram.querySelector('[data-er-notation-select]');
      if (!notationSelect) return;
      diagram.dataset.erNotation = notationSelect.value === 'ie' ? 'ie' : 'idef1x';
      notationSelect.addEventListener('change', () => {
        diagram.dataset.erNotation = notationSelect.value === 'ie' ? 'ie' : 'idef1x';
        drawRelationshipDiagram(diagram);
      });
    });
    queueRelationshipDraw();
    window.addEventListener('resize', queueRelationshipDraw, { passive: true });
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(queueRelationshipDraw);
      erDiagrams.forEach((diagram) => observer.observe(diagram));
    }
    document.fonts?.ready.then(queueRelationshipDraw);
  }
  const filter = document.querySelector('[data-table-filter]');
  filter?.addEventListener('input', () => {
    const query = filter.value.toLowerCase();
    document.querySelectorAll('[data-table]').forEach((item) => {
      item.classList.toggle('is-filtered', !item.dataset.table.toLowerCase().includes(query));
    });
    queueRelationshipDraw();
  });
  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (character) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
  }
})();
"""; }

    private String favicon() { return """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" role="img" aria-label="Mandala SbDP">
  <rect width="64" height="64" rx="14" fill="#15211c"/>
  <path d="M14 45V19h7l11 15 11-15h7v26h-7V30L32 44 21 30v15z" fill="#d9ff74"/>
</svg>
"""; }

    public record RenderResult(int pagesWritten, List<String> brokenLinks) {}
    private record ActionTransition(Node action, Node from, Node to) {}
    private record ScreenCapture(Node state, Node screenshot) {}
    private record ManagedOutput(boolean legacy) {}
}
