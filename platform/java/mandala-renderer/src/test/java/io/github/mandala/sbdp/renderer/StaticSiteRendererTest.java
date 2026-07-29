package io.github.mandala.sbdp.renderer;

import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticSiteRendererTest {
    @TempDir Path temp;

    @Test
    void rendersBidirectionalLinksCrudAndSafeCustomHtml() throws Exception {
        ElementMetadata observed = ElementMetadata.builder()
                .confidence(Confidence.OBSERVED)
                .evidence(List.of(Evidence.of(EvidenceType.RUNTIME_OBSERVATION, "trace.json", "observed SQL")))
                .build();
        Node flow = Node.builder(StableId.of("flow:project.create.success"), NodeType.E2E_FLOW, "Project create")
                .description("解析元の引用 “Project shall be created”").metadata(observed).build();
        Node sql = Node.builder(StableId.of("sql:ProjectDao/insert.sql"), NodeType.SQL_STATEMENT, "insert project").metadata(observed).attributes(Map.of("normalizedSql", "INSERT INTO projects(name) VALUES (?)")).build();
        Node table = Node.builder(StableId.of("table:public.projects"), NodeType.DB_TABLE, "public.projects").metadata(observed).build();
        Edge call = Edge.of("edge:flow-sql", EdgeType.EXECUTES_SQL, flow.id(), sql.id());
        Edge create = Edge.builder(StableId.of("edge:sql-table"), EdgeType.CREATES, sql.id(), table.id())
                .metadata(observed).attributes(Map.of("direct", true, "observed", true)).build();
        DocumentationGraph graph = DocumentationGraph.of("sample", "abc123", Instant.parse("2026-01-01T00:00:00Z"), List.of(flow, sql, table), List.of(call, create));
        Path custom = temp.resolve("custom/entries/project-create-success");
        Files.createDirectories(custom);
        Files.writeString(custom.resolve("overview.html"), "<style>h2{display:none}</style><h2 style=\"color:red\" onmouseover=alert(2)>Intent</h2><a href=javascript:alert(3)>unsafe</a><mandala-table-ref id=\"table:public.projects\"></mandala-table-ref><script>alert(1)</script>");
        Files.writeString(custom.resolve("overview.css"), "h2 { color: rebeccapurple; }");
        Files.writeString(custom.getParent().getParent().resolve("palette.css"),
                ":root { --mandala-light-accent: #654321; --mandala-dark-accent: #fedcba; }");

        Path site = temp.resolve("site");
        Node oldFlow = Node.builder(StableId.of("flow:removed"), NodeType.E2E_FLOW, "Removed flow").metadata(observed).build();
        DocumentationGraph graphWithOldFlow = DocumentationGraph.of("sample", "abc123", Instant.parse("2026-01-01T00:00:00Z"),
                List.of(flow, oldFlow, sql, table), List.of(call, create));
        new StaticSiteRenderer().render(graphWithOldFlow, site, temp.resolve("custom"), RenderOptions.defaults());
        Path removedPage = site.resolve(PagePaths.forNode(oldFlow));
        assertTrue(Files.isRegularFile(removedPage));
        Files.writeString(site.resolve("operator-owned-note.txt"), "preserve");
        StaticSiteRenderer.RenderResult result = new StaticSiteRenderer().render(graph, site, temp.resolve("custom"), RenderOptions.defaults());

        assertTrue(result.brokenLinks().isEmpty(), result.brokenLinks().toString());
        assertFalse(Files.exists(removedPage));
        assertTrue(Files.isRegularFile(site.resolve("operator-owned-note.txt")));
        String flowPage = Files.readString(site.resolve(PagePaths.forNode(flow)));
        String sqlPage = Files.readString(site.resolve(PagePaths.forNode(sql)));
        String tablePage = Files.readString(site.resolve(PagePaths.forNode(table)));
        assertTrue(flowPage.contains("CREATE"));
        assertTrue(flowPage.contains("Intent"));
        assertFalse(flowPage.contains("alert(1)"));
        assertFalse(flowPage.contains("alert(2)"));
        assertFalse(flowPage.contains("alert(3)"));
        assertFalse(flowPage.contains("<style>"));
        assertFalse(flowPage.contains("style="));
        String customStylesheet = Files.readString(site.resolve("assets/custom.css"));
        assertTrue(customStylesheet.contains(
                ":root{--mandala-light-accent:#654321;--mandala-dark-accent:#fedcba;}"));
        assertTrue(customStylesheet.contains(".custom-section .custom-html h2{ color: rebeccapurple; }"));
        assertTrue(tablePage.contains("class=\"panel table-definition\""));
        assertTrue(tablePage.contains("data-i18n=\"table.definition\""));
        assertTrue(tablePage.contains("class=\"panel related-e2e\""));
        assertTrue(tablePage.contains("Project create"));
        assertTrue(tablePage.indexOf("class=\"panel table-definition\"")
                        < tablePage.indexOf("class=\"panel related-e2e\""),
                "The table definition should lead the page while preserving Related E2E as a primary section");
        assertTrue(sqlPage.contains("CREATES · OBSERVED · RUNTIME_OBSERVATION"));
        assertTrue(Files.readString(site.resolve("crud/index.html")).contains(PagePaths.forNode(sql)));
        String erPage = Files.readString(site.resolve("er/index.html"));
        assertFalse(erPage.contains("<style>"),
                "Generated pages must remain compatible with the strict style-src CSP");
        assertTrue(erPage.contains("<section class=\"er-diagram\""));
        assertTrue(erPage.contains("<article class=\"er-table\" data-table=\"table:public.projects\""));
        assertTrue(erPage.contains("<svg class=\"er-relation-layer\" data-er-connectors"),
                "A responsive SVG overlay draws relations while Tables and Columns remain semantic HTML");
        assertFalse(erPage.contains("viewBox="),
                "The generated page must not bake in fixed diagram dimensions");
        assertFalse(Files.exists(site.resolve("er/diagram.svg")),
                "The obsolete image artifact must be pruned from generated output");
        assertFalse(Files.readString(site.resolve("assets/mandala.js")).contains(".style"),
                "Client interactions must not create CSP-blocked inline styles");
        assertTrue(Files.isRegularFile(site.resolve("assets/favicon.svg")));
        assertTrue(flowPage.contains("rel=\"icon\" href=\"../assets/favicon.svg\""));
        assertTrue(flowPage.contains("<h1>Project create</h1>"),
                "Graph-derived terminology must remain verbatim and outside translation markers");
        assertTrue(flowPage.contains("<p class=\"lead\">解析元の引用 “Project shall be created”</p>"),
                "Graph-derived explanations and quotations must remain in their source language");
        assertFalse(flowPage.contains("data-i18n=\"Project create\""));
        assertTrue(flowPage.contains("data-language"));
        assertTrue(flowPage.contains("data-theme-select"));
        assertTrue(flowPage.contains("data-i18n=\"node.specification\""));
        String script = Files.readString(site.resolve("assets/mandala.js"));
        assertTrue(script.contains("mandala.language"));
        assertTrue(script.contains("mandala.theme"));
        assertTrue(script.contains("'node.specification': 'Specification'"));
        assertTrue(script.contains("'table.definition': 'Table definition'"));
        assertTrue(script.contains("'table.relatedE2e': 'Related E2E flows'"));
        assertTrue(script.contains("drawRelationshipDiagram"));
        assertTrue(script.contains("data-er-relation"));
        assertTrue(script.contains("endpointOffset"),
                "Multiple relations sharing one Column must receive separate connector tracks");
        assertTrue(script.contains("appendIdef1xMarkers"));
        assertTrue(script.contains("appendIeMarker"));
        assertTrue(script.contains("data-er-notation-select"));
        assertTrue(script.contains("drawScreenMap"));
        assertTrue(script.contains("data-screen-edge"));
        assertFalse(script.contains("mandala.erNotation"),
                "ER notation is a page-local display choice and must not expand persisted settings");
        String stylesheet = Files.readString(site.resolve("assets/mandala.css"));
        assertTrue(stylesheet.contains(":root[data-theme=dark]"));
        assertTrue(stylesheet.contains("@media(prefers-color-scheme:dark)"));
        assertTrue(stylesheet.contains("--canvas:var(--mandala-light-page,#f1eadc)"),
                "Generated documentation must inherit the landing-page light palette by default");
        assertTrue(stylesheet.contains("--accent:var(--mandala-dark-accent,#df7867)"),
                "Generated documentation must inherit the landing-page dark accent by default");
        assertTrue(stylesheet.contains(".site-nav{background:var(--header-bg);backdrop-filter:blur(16px)}"));
        assertTrue(stylesheet.contains(".display-controls select{appearance:none"),
                "Theme and language controls must reserve space for a consistently positioned arrow");
        assertTrue(stylesheet.contains(".metric{background:var(--paper)"));
        assertTrue(stylesheet.contains("flex:1 1 210px;min-width:0"),
                "Metric cards must distribute incomplete rows without leaving an empty grid track");
        assertTrue(stylesheet.contains("grid-template-columns:max-content minmax(0,1fr)"),
                "Long analysis metadata must not widen a mobile viewport");
        assertTrue(stylesheet.contains(".er-table-grid{display:grid"));
        assertTrue(stylesheet.contains(".er-relation-layer{position:absolute"));
        assertTrue(stylesheet.contains(".er-idef-cardinality{fill:var(--diagram-title)"));
        assertTrue(stylesheet.contains(".er-ie-marker{fill:none"));
        assertTrue(stylesheet.contains(".er-connector-line.is-non-identifying{stroke-dasharray"));
        assertTrue(stylesheet.contains(".table-definition-wrap{overflow:auto"),
                "Wide table definitions must remain usable on narrow viewports");
        assertTrue(stylesheet.contains(".table-definition-details{display:grid"));
        assertFalse(stylesheet.contains(".er-inline svg"),
                "The relation layer must not revert to a fixed-size image layout");

        String goldenName = "project-create-flow.html";
        if (Boolean.getBoolean("mandala.updateGolden")) {
            String configured = System.getProperty("mandala.goldenDir");
            if (configured == null || configured.isBlank()) throw new IllegalStateException("mandala.goldenDir is required");
            Path golden = Path.of(configured).resolve(goldenName);
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, flowPage);
        } else {
            try (var golden = StaticSiteRendererTest.class.getResourceAsStream("/golden/" + goldenName)) {
                if (golden == null) throw new IllegalStateException("Missing renderer Golden file: " + goldenName);
                assertEquals(new String(golden.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), flowPage,
                        "Renderer output changed. Review it and run ./scripts/update-snapshots.sh explicitly.");
            }
        }
    }

    @Test
    void rendersObservedScreenDiagramAndActionStateBranches() throws Exception {
        ElementMetadata successEvidence = ElementMetadata.builder()
                .confidence(Confidence.OBSERVED)
                .relatedScenarios(Set.of("login-success"))
                .evidence(List.of(Evidence.of(EvidenceType.PLAYWRIGHT_OBSERVATION,
                        "login-success.json", "observed login success")))
                .build();
        ElementMetadata failureEvidence = ElementMetadata.builder()
                .confidence(Confidence.OBSERVED)
                .relatedScenarios(Set.of("login-failure"))
                .evidence(List.of(Evidence.of(EvidenceType.PLAYWRIGHT_OBSERVATION,
                        "login-failure.json", "observed login failure")))
                .build();
        Node login = Node.builder(StableId.of("screen:/login"), NodeType.SCREEN, "/login")
                .metadata(successEvidence).attributes(Map.of("route", "/login")).build();
        Node projects = Node.builder(StableId.of("screen:/projects"), NodeType.SCREEN, "/projects")
                .metadata(successEvidence).attributes(Map.of("route", "/projects")).build();
        Node successFrom = state("screen-state:/login:normal.login-success", "Login · normal",
                "/login", "normal", successEvidence);
        Node successTo = state("screen-state:/projects:normal.login-success", "Projects · normal",
                "/projects", "normal", successEvidence);
        Node failureFrom = state("screen-state:/login:normal.login-failure", "Login · normal",
                "/login", "normal", failureEvidence);
        Node failureTo = state("screen-state:/login:validation-error.login-failure",
                "Login · validation-error", "/login", "validation-error", failureEvidence);
        Node successFlow = Node.builder(StableId.of("flow:login.success"), NodeType.E2E_FLOW, "Login success")
                .metadata(successEvidence).attributes(Map.of("scenarioId", "login-success")).build();
        Node failureFlow = Node.builder(StableId.of("flow:login.failure"), NodeType.E2E_FLOW, "Login failure")
                .metadata(failureEvidence).attributes(Map.of("scenarioId", "login-failure")).build();
        Node successAction = action("action:login-success:0", successEvidence, "login-success",
                "/login", "normal", "/projects", "normal", "success");
        Node failureAction = action("action:login-failure:0", failureEvidence, "login-failure",
                "/login", "normal", "/login", "validation-error", "validation-error");
        Node successFill = action("action:login-success:1", successEvidence, "login-success",
                "/login", "normal", "/login", "normal", "normal", "Email", "fill");
        Node failureFill = action("action:login-failure:1", failureEvidence, "login-failure",
                "/login", "normal", "/login", "normal", "normal", "Email", "fill");
        Node loginCall = Node.builder(StableId.of("client:POST:/api/auth/login"),
                NodeType.HTTP_CLIENT_CALL, "POST /api/auth/login").metadata(successEvidence).build();
        Node loginScreenshot = Node.builder(StableId.of("screenshot:login-success"),
                        NodeType.SCREENSHOT, "Login screen")
                .metadata(successEvidence).attributes(Map.of("path", "screenshots/login.png")).build();
        Node projectsScreenshot = Node.builder(StableId.of("screenshot:projects"),
                        NodeType.SCREENSHOT, "Projects screen")
                .metadata(successEvidence).attributes(Map.of("path", "screenshots/projects.png")).build();

        DocumentationGraph graph = DocumentationGraph.of("sample", "abc123",
                Instant.parse("2026-07-28T00:00:00Z"),
                List.of(login, projects, successFrom, successTo, failureFrom, failureTo,
                        successFlow, failureFlow, successAction, failureAction,
                        successFill, failureFill, loginCall, loginScreenshot, projectsScreenshot),
                List.of(
                        edge("edge:screen-navigation", EdgeType.NAVIGATES_TO, login, projects,
                                successEvidence, Map.of("scenarioId", "login-success")),
                        edge("edge:login-success-state", EdgeType.HAS_STATE, login,
                                successFrom, successEvidence, Map.of()),
                        edge("edge:login-failure-state", EdgeType.HAS_STATE, login,
                                failureFrom, failureEvidence, Map.of()),
                        edge("edge:login-error-state", EdgeType.HAS_STATE, login,
                                failureTo, failureEvidence, Map.of()),
                        edge("edge:projects-state", EdgeType.HAS_STATE, projects,
                                successTo, successEvidence, Map.of()),
                        edge("edge:login-screenshot", EdgeType.CAPTURED_AS, successFrom,
                                loginScreenshot, successEvidence, Map.of()),
                        edge("edge:projects-screenshot", EdgeType.CAPTURED_AS, successTo,
                                projectsScreenshot, successEvidence, Map.of()),
                        edge("edge:success-performed", EdgeType.PERFORMED_ON, successFrom,
                                successAction, successEvidence, Map.of()),
                        edge("edge:success-transition", EdgeType.TRANSITIONS_TO, successAction,
                                successTo, successEvidence, Map.of()),
                        edge("edge:failure-performed", EdgeType.PERFORMED_ON, failureFrom,
                                failureAction, failureEvidence, Map.of()),
                        edge("edge:failure-transition", EdgeType.TRANSITIONS_TO, failureAction,
                                failureTo, failureEvidence, Map.of()),
                        edge("edge:success-fill-performed", EdgeType.PERFORMED_ON, successFrom,
                                successFill, successEvidence, Map.of()),
                        edge("edge:success-fill-transition", EdgeType.TRANSITIONS_TO, successFill,
                                successFrom, successEvidence, Map.of()),
                        edge("edge:failure-fill-performed", EdgeType.PERFORMED_ON, failureFrom,
                                failureFill, failureEvidence, Map.of()),
                        edge("edge:failure-fill-transition", EdgeType.TRANSITIONS_TO, failureFill,
                                failureFrom, failureEvidence, Map.of()),
                        edge("edge:success-http", EdgeType.CALLS_HTTP, successAction, loginCall,
                                successEvidence, Map.of("sequence", 0, "status", 200)),
                        edge("edge:failure-http", EdgeType.CALLS_HTTP, failureAction, loginCall,
                                failureEvidence, Map.of("sequence", 0, "status", 401))
                ));

        Files.createDirectories(temp.resolve("screenshots"));
        Files.writeString(temp.resolve("screenshots/login.png"), "login");
        Files.writeString(temp.resolve("screenshots/projects.png"), "projects");
        Path site = temp.resolve("transition-site");
        StaticSiteRenderer.RenderResult result = new StaticSiteRenderer().render(
                graph, site, temp.resolve("custom"), RenderOptions.defaults());

        assertTrue(result.brokenLinks().isEmpty(), result.brokenLinks().toString());
        String transitions = Files.readString(site.resolve("screens/transitions.html"));
        assertTrue(transitions.contains("class=\"screen-map\""));
        assertTrue(transitions.contains("data-screen-node=\"screen:/login\""));
        assertTrue(transitions.contains("data-screen-node=\"screen:/projects\""));
        assertTrue(transitions.contains("data-screen-edge"));
        assertTrue(transitions.contains("../screenshots/login.png"));
        assertTrue(transitions.contains("../screenshots/projects.png"));
        assertFalse(transitions.contains("class=\"screen-transition\""),
                "One-to-one transition rows belong on the Screen page");
        assertFalse(transitions.contains("class=\"action-transition-diagram\""),
                "State and action details belong on the Screen page");
        assertFalse(transitions.contains("POST /api/auth/login"));

        String loginPage = Files.readString(site.resolve(PagePaths.forNode(login)));
        assertTrue(loginPage.contains("screen-transition-details"));
        assertTrue(loginPage.contains(PagePaths.forNode(projects)));
        assertTrue(loginPage.contains("class=\"action-transition-diagram\""));
        assertTrue(loginPage.contains("validation-error"));
        assertTrue(loginPage.contains("ADMIN"));
        assertTrue(loginPage.contains("projectCreation=true"));
        assertTrue(loginPage.contains("POST /api/auth/login"));
        assertTrue(loginPage.contains("HTTP 200"));
        assertTrue(loginPage.contains("HTTP 401"));
        assertTrue(loginPage.contains("data-i18n-template=\"transitions.branchCount\" data-i18n-values=\"2\""));
        assertEquals(2, loginPage.split("data-i18n-template=\"transitions.branchCount\"", -1).length - 1,
                "Equivalent transitions observed in multiple Scenarios are not separate branches");
        String flowPage = Files.readString(site.resolve(PagePaths.forNode(successFlow)));
        assertFalse(flowPage.contains("class=\"action-transition-diagram\""));
        assertFalse(flowPage.contains(PagePaths.forNode(failureAction)));
    }

    private Node state(String id, String name, String route, String state, ElementMetadata metadata) {
        String scenarioId = id.substring(id.lastIndexOf('.') + 1);
        return Node.builder(StableId.of(id), NodeType.SCREEN_STATE, name).metadata(metadata)
                .attributes(Map.of("route", route, "state", state, "scenarioId", scenarioId)).build();
    }

    private Node action(String id, ElementMetadata metadata, String scenarioId,
                        String fromRoute, String fromState, String toRoute, String toState,
                        String outcome) {
        return action(id, metadata, scenarioId, fromRoute, fromState, toRoute, toState,
                outcome, "Login", "click");
    }

    private Node action(String id, ElementMetadata metadata, String scenarioId,
                        String fromRoute, String fromState, String toRoute, String toState,
                        String outcome, String displayName, String kind) {
        return Node.builder(StableId.of(id), NodeType.UI_ACTION, displayName).metadata(metadata)
                .attributes(Map.of(
                        "scenarioId", scenarioId,
                        "sequence", 0,
                        "kind", kind,
                        "role", "ADMIN",
                        "featureFlags", Map.of("projectCreation", true),
                        "fromRoute", fromRoute,
                        "fromState", fromState,
                        "toRoute", toRoute,
                        "toState", toState,
                        "outcome", outcome))
                .build();
    }

    private Edge edge(String id, EdgeType type, Node from, Node to,
                      ElementMetadata metadata, Map<String, ?> attributes) {
        return Edge.builder(StableId.of(id), type, from.id(), to.id())
                .metadata(metadata).attributes(attributes).build();
    }

    @Test
    void failsClosedForEntityObfuscatedCustomJavaScript() throws Exception {
        ElementMetadata metadata = ElementMetadata.builder().confidence(Confidence.HUMAN_REVIEWED).build();
        Node flow = Node.builder(StableId.of("flow:unsafe"), NodeType.E2E_FLOW, "Unsafe").metadata(metadata).build();
        DocumentationGraph graph = DocumentationGraph.of("sample", "commit", Instant.EPOCH, List.of(flow), List.of());
        Path custom = temp.resolve("unsafe/entries/unsafe");
        Files.createDirectories(custom);
        Files.writeString(custom.resolve("details.html"), "<a href=\"java&#x73;cript:alert(1)\">unsafe</a>");

        assertThrows(RuntimeException.class,
                () -> new CustomHtmlIntegrator(temp.resolve("unsafe"), false, graph).sectionsFor(flow));
    }

    @Test
    void rejectsGlobalCustomStylesOutsidePaletteTokenContract() throws Exception {
        Path custom = temp.resolve("invalid-palette");
        Files.createDirectories(custom);
        Files.writeString(custom.resolve("palette.css"), "body { display: none; }");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new CustomHtmlIntegrator(custom, false, DocumentationGraph.empty("sample")).stylesheet());

        assertTrue(error.getMessage().contains("--mandala-light-* and --mandala-dark-*"));
    }

    @Test
    void reportsBrokenRelativeLinks() throws Exception {
        Path site = temp.resolve("broken"); Files.createDirectories(site);
        Files.writeString(site.resolve("index.html"), "<a href=\"missing.html\">missing</a>");
        assertEquals(List.of("index.html -> missing.html"), new LinkVerifier().verify(site));
    }

    @Test
    void refusesToOverwriteAnUnmanagedNonEmptyDirectory() throws Exception {
        Path unsafe = temp.resolve("source-directory");
        Files.createDirectories(unsafe);
        Files.writeString(unsafe.resolve("important.txt"), "keep");

        IOException error = assertThrows(IOException.class, () -> new StaticSiteRenderer().render(
                DocumentationGraph.empty("sample"), unsafe, null, RenderOptions.defaults()));

        assertTrue(error.getMessage().contains("not managed by Mandala"));
        assertEquals("keep", Files.readString(unsafe.resolve("important.txt")));
    }
}
