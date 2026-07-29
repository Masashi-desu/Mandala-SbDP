package io.github.mandala.sbdp.cli.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class UiGraphAdapter extends AbstractProjectAdapter {
    private final ObjectMapper mapper = new ObjectMapper();
    UiGraphAdapter(io.github.mandala.sbdp.cli.RepositoryContext repository) {
        super(repository, Set.of(ChangeCategory.FRONTEND, ChangeCategory.FIXTURE,
                ChangeCategory.PLAYWRIGHT_SCENARIO, ChangeCategory.UI_CAPTURE));
    }
    @Override public String name() { return "playwright"; }

    @Override public DocumentationGraph analyze(RefreshContext context) throws Exception {
        Map<StableId, Node> nodes = new LinkedHashMap<>(); List<Edge> edges = new ArrayList<>();
        List<Path> observations = repository.glob(repository.config().mandala.playwright.observations);
        int imported = 0;
        for (Path path : observations) {
            if (path.getFileName().toString().equals("discovery.json")) continue;
            JsonNode root = mapper.readTree(path.toFile()); if (!root.hasNonNull("id") || !root.hasNonNull("route")) continue;
            addObservation(root, path, nodes, edges, context); imported++;
        }
        if (imported == 0) throw new IllegalStateException("No Playwright scenario observations match " + repository.config().mandala.playwright.observations + "; run `mandala capture-ui`");
        return persist(GraphSupport.graph(context.projectId(), context.targetCommit(), context.analyzedAt(), nodes.values(), edges));
    }

    private void addObservation(JsonNode root, Path file, Map<StableId, Node> nodes, List<Edge> edges, RefreshContext context) {
        String id = root.path("id").asText(); String title = root.path("title").asText(id); String scenarioRoute = root.path("route").asText("/"); String route = normalizeUiRoute(scenarioRoute); String finalRoute = normalizeUiRoute(pagePath(root.path("pageUrl").asText(scenarioRoute))); String state = root.path("state").asText("normal"); String relative = repository.root().relativize(file).toString().replace('\\', '/');
        ElementMetadata metadata = GraphSupport.metadata(EvidenceType.PLAYWRIGHT_OBSERVATION, relative, "Deterministic Playwright capture for " + id, name(), context.targetCommit(), context.analyzedAt(), textList(root.path("consoleErrors")), List.of(id), SourceLocation.of(relative));
        StableId flowId = GraphSupport.IDS.flow(id.replace('-', '.')); StableId entryId = GraphSupport.IDS.custom("ui-entry", route); StableId screenId = GraphSupport.IDS.screen(route); StableId finalScreenId = GraphSupport.IDS.screen(finalRoute); StableId stateId = GraphSupport.IDS.screenState(finalRoute, state + "." + id);
        nodes.put(flowId, Node.builder(flowId, NodeType.E2E_FLOW, title).description("Observed E2E flow from " + route + " to " + finalRoute + " in " + state + " state").metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "scenarioId", id, "route", route, "finalRoute", finalRoute, "state", state, "actions", jsonValue(root.path("actions")), "transitions", jsonValue(root.path("transitions")), "requests", jsonValue(root.path("requests")), "capturedAt", root.path("capturedAt").asText(), "sourceFingerprint", GraphSupport.fingerprint(route, finalRoute, state, root.path("actions"), root.path("transitions"), root.path("requests")))).build());
        boolean firstEntryForRoute = nodes.putIfAbsent(entryId, Node.builder(entryId, NodeType.UI_ENTRY, "Open " + route).description("Direct UI entry point for frontend route " + route).metadata(metadata).attributes(Map.of("route", route)).build()) == null;
        nodes.putIfAbsent(screenId, Node.builder(screenId, NodeType.SCREEN, route).description("Frontend route " + route).metadata(metadata).attributes(Map.of("route", route)).build());
        nodes.putIfAbsent(finalScreenId, Node.builder(finalScreenId, NodeType.SCREEN, finalRoute).description("Observed frontend route " + finalRoute).metadata(metadata).attributes(Map.of("route", finalRoute)).build());
        edges.add(GraphSupport.edge(EdgeType.CONTAINS, flowId, entryId, metadata));
        edges.add(GraphSupport.edge(EdgeType.CONTAINS, flowId, screenId, metadata));
        if (firstEntryForRoute) edges.add(GraphSupport.edge(EdgeType.NAVIGATES_TO, entryId, screenId, metadata));

        Map<Integer, StableId> actionsBySequence = new LinkedHashMap<>();
        JsonNode transitions = root.path("transitions");
        boolean hasActionTransitions = transitions.isArray() && !transitions.isEmpty();
        if (hasActionTransitions) {
            addTransitions(transitions, id, title, flowId, metadata, nodes, edges, actionsBySequence, root.path("environment"));
        } else {
            addLegacyActions(root.path("actions"), id, flowId, metadata, nodes, edges, actionsBySequence);
            if (!finalScreenId.equals(screenId)) {
                edges.add(GraphSupport.edge(EdgeType.NAVIGATES_TO, screenId, finalScreenId, metadata, Map.of("scenarioId", id)));
                edges.add(GraphSupport.edge(EdgeType.CONTAINS, flowId, finalScreenId, metadata));
            }
        }

        nodes.put(stateId, Node.builder(stateId, NodeType.SCREEN_STATE, title + " · " + state).metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "route", finalRoute, "state", state, "scenarioId", id, "pageUrl", redactUrl(root.path("pageUrl").asText()), "ariaSnapshot", root.path("ariaSnapshot").asText(), "domSnapshot", root.path("domSnapshot").asText(), "environment", jsonValue(root.path("environment")))).build());
        edges.add(GraphSupport.edge(EdgeType.HAS_STATE, finalScreenId, stateId, metadata));
        edges.add(GraphSupport.edge(EdgeType.CONTAINS, flowId, finalScreenId, metadata));
        String screenshotPath = root.path("screenshot").asText();
        if (!screenshotPath.isBlank()) {
            StableId screenshotId = StableId.of("screenshot:" + id);
            nodes.put(screenshotId, Node.builder(screenshotId, NodeType.SCREENSHOT, title + " screenshot")
                    .metadata(metadata).attributes(GraphSupport.attributes(Map.of(),
                            "path", screenshotPath, "viewport", "1440x1000", "scenarioId", id,
                            "route", finalRoute, "state", state,
                            "screenName", root.path("screenName").asText())).build());
            addEdgeIfAbsent(edges, EdgeType.CAPTURED_AS, stateId, screenshotId, metadata);
        }
        int requestIndex = 0;
        for (JsonNode request : root.path("requests")) {
            String method = request.path("method").asText("GET").toUpperCase(); String requestPath = request.path("path").asText("/"); StableId callId = StableId.of("client:" + method + ":" + requestPath);
            nodes.putIfAbsent(callId, Node.builder(callId, NodeType.HTTP_CLIENT_CALL, method + " " + requestPath).description("Client API discovered from Playwright network observation").metadata(metadata).attributes(Map.of("method", method, "path", requestPath)).build());
            int actionSequence = request.has("actionSequence") ? request.path("actionSequence").asInt(-1) : -1;
            Map<String, Object> callAttributes = GraphSupport.attributes(Map.of(), "scenarioId", id, "sequence", requestIndex++, "actionSequence", actionSequence, "status", request.path("status").asInt(), "requestBody", jsonValue(request.path("requestBody")), "responseBody", jsonValue(request.path("responseBody")), "mockId", request.path("mockId").asText(), "undefined", request.path("undefined").asBoolean());
            edges.add(GraphSupport.edge(EdgeType.CALLS_HTTP, flowId, callId, metadata, callAttributes));
            StableId triggerAction = actionSequence >= 0 ? actionsBySequence.get(actionSequence)
                    : hasActionTransitions ? null
                    : actionsBySequence.values().stream().reduce((first, second) -> second).orElse(null);
            if (triggerAction != null) edges.add(GraphSupport.edge(EdgeType.CALLS_HTTP, triggerAction, callId, metadata, callAttributes));
        }
    }

    private void addTransitions(JsonNode transitions, String scenarioId, String title, StableId flowId,
                                ElementMetadata metadata, Map<StableId, Node> nodes, List<Edge> edges,
                                Map<Integer, StableId> actionsBySequence, JsonNode environment) {
        int fallbackSequence = 0;
        boolean firstTransition = true;
        for (JsonNode transition : transitions) {
            int sequence = transition.path("sequence").asInt(fallbackSequence++);
            JsonNode action = transition.path("action");
            JsonNode from = transition.path("from");
            JsonNode to = transition.path("to");
            JsonNode condition = transition.path("condition");
            String fromRoute = normalizeUiRoute(from.path("route").asText("/"));
            String toRoute = normalizeUiRoute(to.path("route").asText(fromRoute));
            String fromState = from.path("state").asText("normal");
            String toState = to.path("state").asText(fromState);
            StableId fromScreenId = GraphSupport.IDS.screen(fromRoute);
            StableId toScreenId = GraphSupport.IDS.screen(toRoute);
            StableId fromStateId = transitionStateId(fromRoute, fromState, scenarioId);
            StableId toStateId = transitionStateId(toRoute, toState, scenarioId);
            StableId actionId = StableId.of("action:" + scenarioId + ":" + sequence);
            String label = action.path("name").asText(action.path("label").asText(action.path("kind").asText("action")));
            Map<String, Object> conditions = GraphSupport.attributes(Map.of(),
                    "role", condition.path("role").asText("UNSPECIFIED"),
                    "featureFlags", jsonValue(condition.path("featureFlags")),
                    "outcome", condition.path("outcome").asText(toState),
                    "scenarioOutcome", condition.path("scenarioOutcome").asText(condition.path("outcome").asText(toState)));
            Map<String, Object> actionAttributes = GraphSupport.attributes(conditions,
                    "kind", action.path("kind").asText(),
                    "controlRole", action.path("role").asText(),
                    "name", action.path("name").asText(),
                    "label", action.path("label").asText(),
                    "locator", locator(action),
                    "sequence", sequence,
                    "scenarioId", scenarioId,
                    "fromRoute", fromRoute,
                    "fromState", fromState,
                    "toRoute", toRoute,
                    "toState", toState,
                    "relatedHttp", jsonValue(transition.path("relatedHttp")));
            nodes.putIfAbsent(fromScreenId, Node.builder(fromScreenId, NodeType.SCREEN, fromRoute).description("Observed frontend route " + fromRoute).metadata(metadata).attributes(Map.of("route", fromRoute)).build());
            nodes.putIfAbsent(toScreenId, Node.builder(toScreenId, NodeType.SCREEN, toRoute).description("Observed frontend route " + toRoute).metadata(metadata).attributes(Map.of("route", toRoute)).build());
            nodes.putIfAbsent(fromStateId, transitionState(fromStateId, title, fromRoute, fromState, scenarioId, metadata, environment));
            nodes.putIfAbsent(toStateId, transitionState(toStateId, title, toRoute, toState, scenarioId, metadata, environment));
            if (firstTransition) {
                addTransitionScreenshot(from, sequence, scenarioId, title, fromStateId,
                        metadata, nodes, edges);
                firstTransition = false;
            }
            addTransitionScreenshot(to, sequence + 1, scenarioId, title, toStateId,
                    metadata, nodes, edges);
            nodes.put(actionId, Node.builder(actionId, NodeType.UI_ACTION, label).description("Observed UI action transition from " + fromRoute + " (" + fromState + ") to " + toRoute + " (" + toState + ")").metadata(metadata).attributes(actionAttributes).build());
            actionsBySequence.put(sequence, actionId);
            edges.add(GraphSupport.edge(EdgeType.CONTAINS, flowId, fromScreenId, metadata));
            edges.add(GraphSupport.edge(EdgeType.CONTAINS, flowId, toScreenId, metadata));
            edges.add(GraphSupport.edge(EdgeType.HAS_STATE, fromScreenId, fromStateId, metadata));
            edges.add(GraphSupport.edge(EdgeType.HAS_STATE, toScreenId, toStateId, metadata));
            edges.add(GraphSupport.edge(EdgeType.HAS_ACTION, flowId, actionId, metadata, Map.of("sequence", sequence)));
            edges.add(GraphSupport.edge(EdgeType.PERFORMED_ON, fromStateId, actionId, metadata, conditions));
            edges.add(GraphSupport.edge(EdgeType.TRANSITIONS_TO, actionId, toStateId, metadata, conditions));
            if (!fromScreenId.equals(toScreenId)) {
                edges.add(GraphSupport.edge(EdgeType.NAVIGATES_TO, fromScreenId, toScreenId, metadata,
                        GraphSupport.attributes(conditions, "scenarioId", scenarioId, "actionId", actionId,
                                "sequence", sequence, "fromState", fromState, "toState", toState)));
            }
        }
    }

    private void addTransitionScreenshot(JsonNode point, int pointIndex, String scenarioId, String title,
                                         StableId stateId, ElementMetadata metadata,
                                         Map<StableId, Node> nodes, List<Edge> edges) {
        String screenshotPath = point.path("screenshot").asText();
        if (screenshotPath.isBlank()) return;
        String fileName;
        try { fileName = Path.of(screenshotPath).getFileName().toString(); }
        catch (RuntimeException invalid) { return; }
        StableId screenshotId = fileName.equals(scenarioId + ".png")
                ? StableId.of("screenshot:" + scenarioId)
                : StableId.of("screenshot:" + scenarioId + ":step:" + pointIndex);
        String route = normalizeUiRoute(point.path("route").asText("/"));
        String state = point.path("state").asText("normal");
        String screenName = point.path("name").asText();
        nodes.putIfAbsent(screenshotId, Node.builder(screenshotId, NodeType.SCREENSHOT,
                        (screenName.isBlank() ? title + " · " + state : screenName) + " screenshot")
                .metadata(metadata).attributes(GraphSupport.attributes(Map.of(),
                        "path", screenshotPath, "viewport", "1440x1000", "scenarioId", scenarioId,
                        "route", route, "state", state, "screenName", screenName,
                        "pointIndex", pointIndex)).build());
        addEdgeIfAbsent(edges, EdgeType.CAPTURED_AS, stateId, screenshotId, metadata);
    }

    private void addEdgeIfAbsent(List<Edge> edges, EdgeType type, StableId from, StableId to,
                                 ElementMetadata metadata) {
        if (edges.stream().noneMatch(edge -> edge.type() == type
                && edge.from().equals(from) && edge.to().equals(to))) {
            edges.add(GraphSupport.edge(type, from, to, metadata));
        }
    }

    private void addLegacyActions(JsonNode actions, String scenarioId, StableId flowId,
                                  ElementMetadata metadata, Map<StableId, Node> nodes, List<Edge> edges,
                                  Map<Integer, StableId> actionsBySequence) {
        int actionIndex = 0;
        for (JsonNode action : actions) {
            int sequence = actionIndex++;
            StableId actionId = StableId.of("action:" + scenarioId + ":" + sequence);
            String label = action.path("name").asText(action.path("label").asText(action.path("kind").asText("action")));
            nodes.put(actionId, Node.builder(actionId, NodeType.UI_ACTION, label).metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "kind", action.path("kind").asText(), "role", action.path("role").asText(), "name", action.path("name").asText(), "label", action.path("label").asText(), "locator", locator(action), "sequence", sequence, "scenarioId", scenarioId)).build());
            edges.add(GraphSupport.edge(EdgeType.HAS_ACTION, flowId, actionId, metadata, Map.of("sequence", sequence)));
            actionsBySequence.put(sequence, actionId);
        }
    }

    private StableId transitionStateId(String route, String state, String scenarioId) {
        return GraphSupport.IDS.screenState(route, state + "." + scenarioId);
    }

    private Node transitionState(StableId stateId, String title, String route, String state, String scenarioId,
                                 ElementMetadata metadata, JsonNode environment) {
        return Node.builder(stateId, NodeType.SCREEN_STATE, title + " · " + state)
                .description("Observed " + state + " screen state at " + route)
                .metadata(metadata)
                .attributes(GraphSupport.attributes(Map.of(), "route", route, "state", state,
                        "scenarioId", scenarioId, "environment", jsonValue(environment)))
                .build();
    }

    private Object jsonValue(JsonNode node) { if (node == null || node.isMissingNode() || node.isNull()) return ""; return mapper.convertValue(node, Object.class); }
    private List<String> textList(JsonNode node) { List<String> values = new ArrayList<>(); if (node != null && node.isArray()) node.forEach(item -> values.add(item.asText())); return values; }
    private String redactUrl(String input) { try { java.net.URI uri = java.net.URI.create(input); return uri.getPath(); } catch (RuntimeException ignored) { return input; } }
    private String pagePath(String input) { try { return java.net.URI.create(input).getPath(); } catch (RuntimeException ignored) { return input; } }
    private String normalizeUiRoute(String input) {
        String path = pagePath(input == null || input.isBlank() ? "/" : input).replaceAll("/:([A-Za-z_][A-Za-z0-9_]*)", "/{$1}");
        return path.replaceAll("/(?<![A-Za-z0-9])\\d+(?=/|$)", "/{id}");
    }
    private String locator(JsonNode action) {
        String role = action.path("role").asText(); String name = action.path("name").asText(); String label = action.path("label").asText();
        if (!role.isBlank() && !name.isBlank()) return "getByRole(" + role + ", name=" + name + ")";
        if (!label.isBlank()) return "getByLabel(" + label + ")";
        return action.path("kind").asText("action");
    }
}
