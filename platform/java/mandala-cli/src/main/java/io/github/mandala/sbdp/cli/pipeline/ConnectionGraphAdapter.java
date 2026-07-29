package io.github.mandala.sbdp.cli.pipeline;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class ConnectionGraphAdapter extends AbstractProjectAdapter {
    private static final Set<EdgeType> CRUD_TYPES = Set.of(
            EdgeType.CREATES, EdgeType.READS, EdgeType.UPDATES, EdgeType.DELETES);
    private final JavaParser javaParser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    ConnectionGraphAdapter(io.github.mandala.sbdp.cli.RepositoryContext repository) {
        super(repository, EnumSet.allOf(ChangeCategory.class));
    }
    @Override public String name() { return "connections"; }

    @Override public DocumentationGraph analyze(RefreshContext context) throws Exception {
        KnownElements known = knownElements();
        Map<StableId, Node> nodes = known.nodes(); List<Edge> edges = new ArrayList<>();
        Map<StableId, List<String>> warnings = new TreeMap<>();
        ElementMetadata inferred = GraphSupport.metadata(EvidenceType.AGENT_INFERENCE, "mandala-reconcile", "Cross-adapter semantic connection", name(), context.targetCommit(), context.analyzedAt(), List.of(), List.of(), SourceLocation.of("mandala/config/mandala.yml"));
        connectHttp(nodes, edges, inferred, warnings);
        connectEndpointHandlers(nodes, edges, inferred, warnings);
        connectSourceCalls(nodes, edges, context, warnings);
        connectRuntime(nodes, known.edges(), edges, inferred, warnings, context);
        return persist(GraphSupport.graph(context.projectId(), context.targetCommit(), context.analyzedAt(),
                warningNodes(nodes, warnings, context), edges));
    }

    private KnownElements knownElements() throws Exception {
        Map<StableId, Node> nodes = new LinkedHashMap<>();
        Map<StableId, Edge> edges = new LinkedHashMap<>();
        Path root = repository.resolve("mandala/cache/fragments");
        if (!Files.isDirectory(root)) return new KnownElements(nodes, List.of());
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")
                    && !file.getFileName().toString().equals(name() + ".json")).sorted().toList()) {
                try {
                    DocumentationGraph fragment = DocumentationGraphJson.read(path);
                    fragment.nodes().forEach(node -> nodes.put(node.id(), node));
                    fragment.edges().forEach(edge -> edges.putIfAbsent(edge.id(), edge));
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot read graph fragment " + path, error);
                }
            }
        }
        return new KnownElements(nodes, List.copyOf(edges.values()));
    }

    private void connectHttp(Map<StableId, Node> nodes, List<Edge> edges, ElementMetadata metadata,
                             Map<StableId, List<String>> warnings) {
        List<Node> endpoints = nodes.values().stream().filter(node -> node.type() == NodeType.HTTP_ENDPOINT).toList();
        for (Node client : nodes.values()) if (client.type() == NodeType.HTTP_CLIENT_CALL) {
            String method = string(client, "method"); String path = string(client, "path");
            List<Node> matches = endpoints.stream().filter(endpoint -> method.equalsIgnoreCase(string(endpoint, "method"))
                    && pathsMatch(path, string(endpoint, "path"))).sorted().toList();
            if (matches.size() == 1) edges.add(GraphSupport.edge(EdgeType.MATCHES_OPERATION, client.id(), matches.getFirst().id(), metadata));
            else if (matches.size() > 1) ambiguous(warnings, client, "HTTP operation", method + " " + path, matches);
        }
    }

    private void connectEndpointHandlers(Map<StableId, Node> nodes, List<Edge> edges, ElementMetadata metadata,
                                         Map<StableId, List<String>> warnings) {
        for (Node endpoint : nodes.values()) if (endpoint.type() == NodeType.HTTP_ENDPOINT) {
            String controller = string(endpoint, "controllerClass"); String handler = string(endpoint, "handlerMethod");
            List<Node> matches = methodCandidates(nodes, controller, handler);
            if (matches.size() == 1) edges.add(GraphSupport.edge(EdgeType.ROUTES_TO, endpoint.id(), matches.getFirst().id(), metadata));
            else if (matches.size() > 1) ambiguous(warnings, endpoint, "endpoint handler", controller + "#" + handler, matches);
        }
    }

    private void connectSourceCalls(Map<StableId, Node> nodes, List<Edge> edges, RefreshContext context,
                                    Map<StableId, List<String>> warnings) throws Exception {
        for (String sourceRoot : repository.config().mandala.source.java.roots) {
            Path root = repository.resolve(sourceRoot); if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) { for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) addCalls(file, nodes, edges, context, warnings); }
        }
    }

    private void addCalls(Path file, Map<StableId, Node> nodes, List<Edge> edges, RefreshContext context,
                          Map<StableId, List<String>> warnings) {
        try {
            var parsed = javaParser.parse(file);
            if (parsed.getResult().isEmpty() || !parsed.isSuccessful()) {
                throw new IllegalArgumentException(parsed.getProblems().toString());
            }
            CompilationUnit unit = parsed.getResult().orElseThrow(); String packageName = unit.getPackageDeclaration().map(value -> value.getNameAsString()).orElse(""); Map<String, String> imports = new HashMap<>(); unit.getImports().stream().filter(value -> !value.isAsterisk() && !value.isStatic()).forEach(value -> imports.put(GraphSupport.simpleName(value.getNameAsString()), value.getNameAsString()));
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                String qualified = packageName.isBlank() ? type.getNameAsString() : packageName + "." + type.getNameAsString(); Map<String, String> fields = new HashMap<>();
                type.getFields().forEach(field -> field.getVariables().forEach(variable -> fields.put(variable.getNameAsString(), resolve(variable.getTypeAsString(), packageName, imports))));
                for (MethodDeclaration method : type.getMethods()) {
                    Node caller = findCaller(nodes, qualified, method); if (caller == null) continue;
                    int line = method.getBegin().map(position -> position.line).orElse(1); String relative = repository.root().relativize(file).toString().replace('\\', '/');
                    ElementMetadata metadata = GraphSupport.metadata(EvidenceType.SOURCE_CODE, relative, "JavaParser method-call AST", name(), context.targetCommit(), context.analyzedAt(), List.of(), List.of(), SourceLocation.line(relative, line));
                    for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                        String owner = call.getScope().map(scope -> { if (scope instanceof NameExpr name) return fields.getOrDefault(name.getNameAsString(), ""); if (scope instanceof FieldAccessExpr fieldAccess && fieldAccess.getScope().isThisExpr()) return fields.getOrDefault(fieldAccess.getNameAsString(), ""); return ""; }).orElse(qualified);
                        if (owner.isBlank()) continue;
                        List<Node> candidates = methodCandidates(nodes, owner, call.getNameAsString());
                        List<Node> arityMatches = candidates.stream()
                                .filter(candidate -> methodArity(candidate) == call.getArguments().size()).toList();
                        if (arityMatches.size() == 1) {
                            Node target = arityMatches.getFirst();
                            if (!target.id().equals(caller.id())) edges.add(GraphSupport.edge(EdgeType.CALLS, caller.id(), target.id(), metadata));
                        } else if (arityMatches.size() > 1) {
                            ambiguous(warnings, caller, "method call", owner + "#" + call.getNameAsString()
                                    + "/" + call.getArguments().size(), arityMatches);
                        }
                    }
                }
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot analyze Java calls in " + file, error); }
    }

    private Node findCaller(Map<StableId, Node> nodes, String owner, MethodDeclaration method) {
        String parameters = method.getParameters().stream().map(parameter -> {
            String type = parameter.getTypeAsString().replaceAll("\\s+", "");
            return parameter.isVarArgs() ? type + "..." : type;
        }).collect(java.util.stream.Collectors.joining(","));
        Node exact = nodes.get(StableId.of("java:" + owner + "#" + method.getNameAsString() + "(" + parameters + ")"));
        if (exact != null) return exact;
        List<Node> candidates = methodCandidates(nodes, owner, method.getNameAsString()).stream()
                .filter(candidate -> methodArity(candidate) == method.getParameters().size()).toList();
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private void connectRuntime(Map<StableId, Node> nodes, List<Edge> knownEdges, List<Edge> edges,
                                ElementMetadata metadata, Map<StableId, List<String>> warnings,
                                RefreshContext context) {
        List<Node> traces = nodes.values().stream().filter(node -> node.type() == NodeType.TRACE).toList();
        Map<StableId, Node> tracesBySpan = new HashMap<>();
        for (Edge edge : knownEdges) {
            Node from = nodes.get(edge.from()); Node to = nodes.get(edge.to());
            if (edge.type() == EdgeType.CONTAINS && from != null && to != null
                    && from.type() == NodeType.TRACE && to.type() == NodeType.SPAN) {
                tracesBySpan.put(to.id(), from);
            }
        }
        Map<StableId, CrudObservation> crudObservations = new TreeMap<>();
        for (Node trace : traces) {
            String flow = string(trace, "flowId"); if (!flow.isBlank()) { StableId flowId = flow.startsWith("flow:") ? StableId.of(flow) : GraphSupport.IDS.flow(flow); if (nodes.containsKey(flowId)) edges.add(GraphSupport.edge(EdgeType.OBSERVED_IN, flowId, trace.id(), metadata)); }
        }
        for (Node span : nodes.values()) if (span.type() == NodeType.SPAN) {
            String semantic = string(span, "semanticStableId"); Node target = null;
            if (!semantic.isBlank()) try { target = nodes.get(StableId.of(semantic)); } catch (IllegalArgumentException ignored) { }
            if (target == null && semantic.startsWith("java:") && semantic.contains("#")) {
                String owner = semantic.substring(5, semantic.indexOf('#'));
                String method = semantic.substring(semantic.indexOf('#') + 1);
                int signature = method.indexOf('('); if (signature >= 0) method = method.substring(0, signature);
                List<Node> matches = methodCandidates(nodes, owner, method);
                if (matches.size() == 1) target = matches.getFirst();
                else if (matches.size() > 1) ambiguous(warnings, span, "runtime Java symbol", semantic, matches);
            }
            if (target != null) edges.add(GraphSupport.edge(EdgeType.OBSERVED_IN, target.id(), span.id(), metadata));
            String method = first(span.attributes(), "http.request.method", "http.method"); String route = first(span.attributes(), "http.route", "url.path");
            if (!method.isBlank() && !route.isBlank()) {
                List<Node> endpoints = nodes.values().stream().filter(node -> node.type() == NodeType.HTTP_ENDPOINT
                        && method.equalsIgnoreCase(string(node, "method"))
                        && pathsMatch(route, string(node, "path"))).sorted().toList();
                if (endpoints.size() == 1) edges.add(GraphSupport.edge(EdgeType.OBSERVED_IN, endpoints.getFirst().id(), span.id(), metadata));
                else if (endpoints.size() > 1) ambiguous(warnings, span, "runtime HTTP operation", method + " " + route, endpoints);
            }
            String sql = first(span.attributes(), "db.query.text", "db.statement");
            if (!sql.isBlank()) {
                String normalized = normalizeSql(sql);
                List<Node> statements = nodes.values().stream().filter(node -> node.type() == NodeType.SQL_STATEMENT)
                        .filter(node -> { String candidate = normalizeSql(string(node, "normalizedSql")); return candidate.equals(normalized) || (!candidate.isBlank() && (candidate.contains(normalized) || normalized.contains(candidate))); })
                        .sorted().toList();
                if (statements.size() == 1) {
                    Node statement = statements.getFirst();
                    edges.add(GraphSupport.edge(EdgeType.OBSERVED_IN, statement.id(), span.id(), metadata));
                    observeCrud(statement, span, tracesBySpan.get(span.id()), knownEdges, nodes,
                            crudObservations);
                }
                else if (statements.size() > 1) ambiguous(warnings, span, "runtime SQL statement", normalized, statements);
            }
        }
        crudObservations.values().stream().map(observation -> observation.toEdge(context, name()))
                .forEach(edges::add);
    }

    private void observeCrud(Node statement, Node span, Node trace, List<Edge> knownEdges,
                             Map<StableId, Node> nodes, Map<StableId, CrudObservation> observations) {
        for (Edge crud : knownEdges) {
            if (!crud.from().equals(statement.id()) || !CRUD_TYPES.contains(crud.type())) continue;
            Node target = nodes.get(crud.to());
            if (target == null) continue;
            boolean direct = directTarget(statement, target, crud);
            observations.computeIfAbsent(crud.id(), ignored -> new CrudObservation(crud))
                    .observe(span, trace, direct);
        }
    }

    private boolean directTarget(Node statement, Node target, Edge edge) {
        Object declared = edge.attributes().get("direct");
        if (declared instanceof Boolean value) return value;
        String schema = string(target, "schema"); String table = string(target, "table");
        Object tables = statement.attributes().get("tables");
        if (tables instanceof Collection<?> values) {
            for (Object value : values) if (value instanceof Map<?, ?> candidate) {
                String candidateSchema = mapString(candidate, "schema");
                if (candidateSchema.isBlank()) candidateSchema = "public";
                if (candidateSchema.equalsIgnoreCase(schema) && mapString(candidate, "table")
                        .equalsIgnoreCase(table)) {
                    return Boolean.parseBoolean(mapString(candidate, "directTarget"));
                }
            }
        }
        return edge.type() == EdgeType.CREATES || edge.type() == EdgeType.UPDATES
                || edge.type() == EdgeType.DELETES;
    }

    private String mapString(Map<?, ?> values, String key) {
        Object value = values.get(key); return value == null ? "" : String.valueOf(value);
    }

    private List<Node> methodCandidates(Map<StableId, Node> nodes, String owner, String method) {
        if (owner.isBlank() || method.isBlank()) return List.of();
        return nodes.values().stream().filter(this::isMethodNode)
                .filter(node -> method.equals(string(node, "memberName")))
                .filter(node -> owner.equals(string(node, "qualifiedName")) || owner.equals(string(node, "daoClass")))
                .sorted().toList();
    }

    private boolean isMethodNode(Node node) {
        return node.type() == NodeType.JAVA_METHOD || node.type() == NodeType.APPLICATION_SERVICE
                || node.type() == NodeType.DOMA_DAO_METHOD;
    }

    private int methodArity(Node node) {
        Object parameters = node.attributes().get("parameters");
        if (parameters instanceof Collection<?> values) return values.size();
        String value = node.id().value();
        int open = value.lastIndexOf('('); int close = value.endsWith(")") ? value.length() - 1 : -1;
        if (open < 0 || close < open) return -1;
        String content = value.substring(open + 1, close);
        if (content.isBlank()) return 0;
        int arity = 1; int genericDepth = 0; int arrayDepth = 0;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '<') genericDepth++;
            else if (character == '>') genericDepth = Math.max(0, genericDepth - 1);
            else if (character == '[') arrayDepth++;
            else if (character == ']') arrayDepth = Math.max(0, arrayDepth - 1);
            else if (character == ',' && genericDepth == 0 && arrayDepth == 0) arity++;
        }
        return arity;
    }

    private void ambiguous(Map<StableId, List<String>> warnings, Node subject, String kind, String reference,
                           Collection<Node> candidates) {
        String candidateIds = candidates.stream().map(node -> node.id().value()).sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        warnings.computeIfAbsent(subject.id(), ignored -> new ArrayList<>()).add(
                "Skipped ambiguous " + kind + " " + reference + "; candidates: " + candidateIds);
    }

    private List<Node> warningNodes(Map<StableId, Node> known, Map<StableId, List<String>> warnings,
                                    RefreshContext context) {
        return warnings.entrySet().stream().map(entry -> {
            Node subject = known.get(entry.getKey());
            if (subject == null) return null;
            ElementMetadata metadata = GraphSupport.metadata(EvidenceType.AGENT_INFERENCE, "mandala-reconcile",
                    "Ambiguous cross-adapter relationship was intentionally left unresolved", name(),
                    context.targetCommit(), context.analyzedAt(), entry.getValue(), List.of(),
                    SourceLocation.of("mandala/config/mandala.yml"));
            return Node.builder(subject.id(), subject.type(), subject.displayName()).description(subject.description())
                    .metadata(metadata).attributes(subject.attributes()).build();
        }).filter(java.util.Objects::nonNull).toList();
    }

    private static final class CrudObservation {
        private final Edge declaredEdge;
        private final Set<StableId> traces = new TreeSet<>();
        private final Set<StableId> spans = new TreeSet<>();
        private final Set<String> scenarios = new TreeSet<>();
        private final Map<StableId, String> sourceByTrace = new TreeMap<>();
        private String fallbackSource = "mandala/traces";
        private boolean direct;

        private CrudObservation(Edge declaredEdge) {
            this.declaredEdge = declaredEdge;
        }

        private void observe(Node span, Node trace, boolean direct) {
            spans.add(span.id());
            this.direct |= direct;
            String source = span.metadata().evidence().stream()
                    .filter(evidence -> evidence.type() == EvidenceType.RUNTIME_OBSERVATION)
                    .map(Evidence::source).filter(value -> !value.isBlank()).findFirst()
                    .orElse("mandala/traces");
            fallbackSource = source;
            if (trace == null) return;
            traces.add(trace.id());
            sourceByTrace.putIfAbsent(trace.id(), source);
            Object flow = trace.attributes().get("flowId");
            if (flow != null && !String.valueOf(flow).isBlank()) scenarios.add(String.valueOf(flow));
        }

        private Edge toEdge(RefreshContext context, String adapter) {
            List<Evidence> evidence = traces.isEmpty()
                    ? List.of(Evidence.of(EvidenceType.RUNTIME_OBSERVATION, fallbackSource,
                    "Observed normalized SQL confirms " + declaredEdge.type() + " " + declaredEdge.to()))
                    : traces.stream().map(trace -> Evidence.of(EvidenceType.RUNTIME_OBSERVATION,
                    sourceByTrace.getOrDefault(trace, fallbackSource),
                    "Trace " + trace + " confirms " + declaredEdge.type() + " " + declaredEdge.to())).toList();
            Set<String> sources = new TreeSet<>(sourceByTrace.values()); sources.add(fallbackSource);
            ElementMetadata metadata = ElementMetadata.builder().evidence(evidence)
                    .sourceLocations(sources.stream().map(SourceLocation::of).toList())
                    .targetCommit(context.targetCommit()).analyzedAt(context.analyzedAt()).adapter(adapter)
                    .confidence(Confidence.OBSERVED).relatedTraces(traces).relatedScenarios(scenarios).build();
            Map<String, Object> attributes = new LinkedHashMap<>(declaredEdge.attributes());
            attributes.put("observed", true);
            attributes.put("direct", direct);
            attributes.put("observationCount", spans.size());
            attributes.put("runtimeSpans", spans.stream().map(StableId::value).toList());
            attributes.put("runtimeTraces", traces.stream().map(StableId::value).toList());
            attributes.put("scenarios", List.copyOf(scenarios));
            return Edge.builder(declaredEdge.id(), declaredEdge.type(), declaredEdge.from(), declaredEdge.to())
                    .description(declaredEdge.description()).metadata(metadata).attributes(attributes).build();
        }
    }

    private record KnownElements(Map<StableId, Node> nodes, List<Edge> edges) {
    }

    private String resolve(String type, String packageName, Map<String, String> imports) { String simple = type.replaceAll("<.*>", "").replace("[]", ""); return simple.contains(".") ? simple : imports.getOrDefault(simple, packageName.isBlank() ? simple : packageName + "." + simple); }
    private String string(Node node, String key) { Object value = node.attributes().get(key); return value == null ? "" : String.valueOf(value); }
    private String first(Map<String, Object> attributes, String... keys) { for (String key : keys) { Object value = attributes.get(key); if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value); } return ""; }
    private String normalizePath(String value) { try { return GraphSupport.IDS.normalizePath(value); } catch (RuntimeException invalid) { return value; } }

    private String normalizeSql(String value) {
        return value.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT)
                .replaceAll("\\s*([(),;=])\\s*", "$1").replaceFirst(";+$", "");
    }

    private boolean pathsMatch(String left, String right) {
        String[] leftSegments = normalizePath(left).split("/", -1);
        String[] rightSegments = normalizePath(right).split("/", -1);
        if (leftSegments.length != rightSegments.length) return false;
        for (int index = 0; index < leftSegments.length; index++) {
            if (leftSegments[index].equals(rightSegments[index])) continue;
            if (!templateSlot(leftSegments[index]) && !templateSlot(rightSegments[index])) return false;
        }
        return true;
    }

    private boolean templateSlot(String segment) {
        return segment.length() >= 3 && segment.startsWith("{") && segment.endsWith("}");
    }
}
