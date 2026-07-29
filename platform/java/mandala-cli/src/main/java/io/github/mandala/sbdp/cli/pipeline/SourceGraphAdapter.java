package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.doma.DomaAnalysis;
import io.github.mandala.sbdp.doma.DomaDaoDescriptor;
import io.github.mandala.sbdp.doma.DomaMethodDescriptor;
import io.github.mandala.sbdp.doma.DomaSourceAnalyzer;
import io.github.mandala.sbdp.doma.ExternalSqlMapping;
import io.github.mandala.sbdp.doma.sql.ColumnReference;
import io.github.mandala.sbdp.doma.sql.CrudOperation;
import io.github.mandala.sbdp.doma.sql.SqlStatementAnalysis;
import io.github.mandala.sbdp.doma.sql.TableReference;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.Conflict;
import io.github.mandala.sbdp.model.ConflictStatus;
import io.github.mandala.sbdp.model.ConflictType;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.spring.EndpointDescriptor;
import io.github.mandala.sbdp.spring.EndpointDiscovery;
import io.github.mandala.sbdp.spring.EndpointReconciler;
import io.github.mandala.sbdp.spring.EndpointSource;
import io.github.mandala.sbdp.spring.ErrorResponseDescriptor;
import io.github.mandala.sbdp.spring.HttpStatusNormalizer;
import io.github.mandala.sbdp.spring.JavaSymbolDescriptor;
import io.github.mandala.sbdp.spring.OpenApiAnalyzer;
import io.github.mandala.sbdp.spring.ActuatorMappingsAnalyzer;
import io.github.mandala.sbdp.spring.ReconciledEndpoint;
import io.github.mandala.sbdp.spring.SpringSourceAnalysis;
import io.github.mandala.sbdp.spring.SpringSourceAnalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SourceGraphAdapter extends AbstractProjectAdapter {
    SourceGraphAdapter(io.github.mandala.sbdp.cli.RepositoryContext repository) {
        super(repository, Set.of(ChangeCategory.JAVA, ChangeCategory.SQL, ChangeCategory.OPENAPI,
                ChangeCategory.SPRING_CAPTURE));
    }
    @Override public String name() { return "source"; }

    @Override public DocumentationGraph analyze(RefreshContext context) throws Exception {
        Map<StableId, Node> nodes = new LinkedHashMap<>(); List<Edge> edges = new ArrayList<>();
        List<EndpointDescriptor> endpointDeclarations = new ArrayList<>();
        List<ErrorResponseDescriptor> errorResponses = new ArrayList<>();
        List<String> endpointWarnings = new ArrayList<>();
        for (String root : repository.config().mandala.source.java.roots) {
            Path javaRoot = repository.resolve(root); if (!java.nio.file.Files.isDirectory(javaRoot)) continue;
            SpringSourceAnalysis analysis = new SpringSourceAnalyzer().analyze(javaRoot);
            analysis.symbols().forEach(symbol -> nodes.merge(StableId.of(symbol.stableId()), symbol(symbol, analysis.warnings(), context), this::prefer));
            endpointDeclarations.addAll(analysis.endpoints()); errorResponses.addAll(analysis.errorResponses());
            endpointWarnings.addAll(analysis.warnings());
        }
        for (String pattern : repository.config().mandala.spring.openApiSnapshots) for (Path snapshot : repository.glob(pattern)) {
            EndpointDiscovery discovery = new OpenApiAnalyzer().analyze(snapshot);
            endpointDeclarations.addAll(discovery.endpoints()); endpointWarnings.addAll(discovery.warnings());
        }
        for (String pattern : repository.config().mandala.spring.mappingSnapshots) for (Path snapshot : repository.glob(pattern)) {
            EndpointDiscovery discovery = new ActuatorMappingsAnalyzer().analyze(snapshot);
            endpointDeclarations.addAll(discovery.endpoints()); endpointWarnings.addAll(discovery.warnings());
        }
        for (ReconciledEndpoint endpoint : new EndpointReconciler().reconcile(endpointDeclarations)) {
            addEndpoint(endpoint, applicableErrorResponses(endpoint.canonical(), errorResponses),
                    nodes, edges, context, endpointWarnings);
        }
        Path resources = resourceRoot();
        for (String root : repository.config().mandala.source.java.roots) {
            Path javaRoot = repository.resolve(root); if (!java.nio.file.Files.isDirectory(javaRoot) || resources == null) continue;
            DomaAnalysis analysis = new DomaSourceAnalyzer().analyze(javaRoot, resources);
            for (DomaDaoDescriptor dao : analysis.daos()) addDao(dao, nodes, edges, context, analysis.warnings());
            for (ExternalSqlMapping mapping : analysis.sqlMappings()) addSql(mapping, nodes, edges, context);
        }
        return persist(GraphSupport.graph(context.projectId(), context.targetCommit(), context.analyzedAt(), nodes.values(), edges));
    }

    private Node prefer(Node left, Node right) { return right.description().length() > left.description().length() ? right : left; }

    private Node symbol(JavaSymbolDescriptor symbol, Collection<String> warnings, RefreshContext context) {
        NodeType type = symbol.kind().equalsIgnoreCase("class") || symbol.memberName().isBlank() ? NodeType.JAVA_CLASS : NodeType.JAVA_METHOD;
        String qualified = symbol.qualifiedName();
        if (qualified.endsWith("Controller")) type = symbol.memberName().isBlank() ? NodeType.CONTROLLER : NodeType.JAVA_METHOD;
        else if (qualified.contains(".service.")) type = symbol.memberName().isBlank() ? NodeType.JAVA_CLASS : NodeType.APPLICATION_SERVICE;
        String relative = relative(symbol.sourcePosition().file());
        List<Evidence> evidence = new ArrayList<>(); evidence.add(Evidence.of(EvidenceType.SOURCE_CODE, relative, symbol.signature()));
        if (!symbol.javadocSummary().isBlank()) evidence.add(Evidence.of(EvidenceType.JAVADOC, relative, symbol.javadocSummary()));
        ElementMetadata metadata = ElementMetadata.builder().evidence(evidence).sourceLocations(List.of(new SourceLocation(relative, symbol.sourcePosition().line(), symbol.sourcePosition().column(), symbol.sourcePosition().line(), symbol.sourcePosition().column(), symbol.qualifiedName() + (symbol.memberName().isBlank() ? "" : "#" + symbol.memberName())))).targetCommit(context.targetCommit()).analyzedAt(context.analyzedAt()).adapter(name()).confidence(Confidence.INFERRED).warnings(warnings).build();
        Map<String, Object> attributes = GraphSupport.attributes(Map.of(), "qualifiedName", qualified, "memberName", symbol.memberName(), "kind", symbol.kind(), "signature", symbol.signature(), "annotations", symbol.annotations(), "javadocSummary", symbol.javadocSummary(), "sourceFingerprint", GraphSupport.fingerprint(symbol.kind(), qualified, symbol.memberName(), symbol.signature(), symbol.annotations()));
        return Node.builder(StableId.of(symbol.stableId()), type, symbol.memberName().isBlank() ? GraphSupport.simpleName(qualified) : GraphSupport.simpleName(qualified) + "#" + symbol.memberName()).description(symbol.javadocSummary()).metadata(metadata).attributes(attributes).build();
    }

    private void addEndpoint(ReconciledEndpoint reconciled, List<ErrorResponseDescriptor> errors,
                             Map<StableId, Node> nodes, List<Edge> edges,
                             RefreshContext context, Collection<String> warnings) {
        EndpointDescriptor endpoint = reconciled.canonical();
        List<Evidence> evidence = new ArrayList<>(reconciled.declarations().stream().map(declaration -> Evidence.of(
                evidenceType(declaration.source()), declarationSource(declaration),
                declaration.source() + " declares " + declaration.httpMethod() + " " + declaration.path())).toList());
        errors.stream().map(this::errorEvidence).forEach(evidence::add);
        List<SourceLocation> locations = new ArrayList<>(reconciled.declarations().stream().map(declaration -> declaration.sourcePosition() == null
                ? SourceLocation.of(declarationSource(declaration))
                : SourceLocation.line(declarationSource(declaration), declaration.sourcePosition().line())).distinct().toList());
        errors.stream().map(this::errorLocation).distinct().forEach(locations::add);
        StableId endpointId = StableId.of(endpoint.stableId());
        List<Conflict> conflicts = reconciled.conflicts().stream().map(description -> new Conflict(
                StableId.of("conflict:endpoint:" + GraphSupport.fingerprint(endpoint.stableId(), description).substring(0, 20)),
                ConflictType.SOURCE_DISAGREEMENT, endpointId, "declarations", description, evidence,
                context.analyzedAt(), ConflictStatus.OPEN, "")).toList();
        List<String> combinedWarnings = new ArrayList<>(warnings); combinedWarnings.addAll(reconciled.conflicts());
        Confidence confidence = !conflicts.isEmpty() ? Confidence.CONFLICTED
                : reconciled.declarationSources().stream().anyMatch(source -> source == EndpointSource.ACTUATOR || source == EndpointSource.OPENAPI)
                ? Confidence.DECLARED : Confidence.INFERRED;
        ElementMetadata metadata = ElementMetadata.builder().evidence(evidence).sourceLocations(locations)
                .targetCommit(context.targetCommit()).analyzedAt(context.analyzedAt()).adapter(name())
                .confidence(confidence).conflicts(conflicts).warnings(combinedWarnings).build();
        List<Map<String, Object>> normalizedErrors = errors.stream().map(this::errorAttributes).toList();
        Map<String, Object> attrs = GraphSupport.attributes(endpoint.attributes(), "method", endpoint.httpMethod(), "path", endpoint.path(), "controllerClass", endpoint.controllerClass(), "handlerMethod", endpoint.handlerMethod(), "consumes", endpoint.consumes(), "produces", endpoint.produces(), "parameters", endpoint.parameters(), "requestBodyType", endpoint.requestBodyType(), "responses", endpoint.responses(), "errorResponses", normalizedErrors, "operationId", endpoint.operationId(), "sourceFingerprint", GraphSupport.fingerprint(endpoint.httpMethod(), endpoint.path(), endpoint.controllerClass(), endpoint.handlerMethod(), endpoint.parameters(), endpoint.responses(), normalizedErrors));
        Node node = Node.builder(endpointId, NodeType.HTTP_ENDPOINT, endpoint.httpMethod() + " " + endpoint.path()).description(endpoint.summary().isBlank() ? endpoint.description() : endpoint.summary()).metadata(metadata).attributes(attrs).build(); nodes.merge(node.id(), node, this::prefer);
        if (!endpoint.requestBodyType().isBlank()) {
            StableId schema = StableId.of("request:" + endpoint.stableId()); Node request = Node.builder(schema, NodeType.REQUEST_SCHEMA, endpoint.requestBodyType()).metadata(metadata).attributes(GraphSupport.attributes(Map.of(), "javaType", endpoint.requestBodyType(), "parameters", endpoint.parameters())).build(); nodes.putIfAbsent(schema, request); edges.add(GraphSupport.edge(EdgeType.ACCEPTS, node.id(), schema, metadata));
        }
        for (var response : endpoint.responses()) if (!response.type().isBlank()) {
            String status = HttpStatusNormalizer.normalize(response.status());
            StableId schema = StableId.of("response:" + endpoint.stableId() + ":" + status); Node responseNode = Node.builder(schema, NodeType.RESPONSE_SCHEMA, response.type() + " (" + status + ")").description(response.description()).metadata(metadata).attributes(Map.of("status", status, "declaredStatus", response.status(), "javaType", response.type(), "mediaType", response.mediaType(), "errorResponse", false)).build(); nodes.putIfAbsent(schema, responseNode); edges.add(GraphSupport.edge(EdgeType.RETURNS, node.id(), schema, metadata));
        }
        addErrorResponseNodes(endpoint, errors, node.id(), nodes, edges, metadata);
        reconciled.declarations().stream().filter(declaration -> declaration.source() == EndpointSource.OPENAPI)
                .forEach(declaration -> addOpenApiOperation(declaration, node.id(), nodes, edges, context));
    }

    private List<ErrorResponseDescriptor> applicableErrorResponses(
            EndpointDescriptor endpoint, Collection<ErrorResponseDescriptor> errors) {
        return errors.stream()
                .filter(error -> error.globalAdvice() || error.handlerClass().equals(endpoint.controllerClass()))
                .sorted(java.util.Comparator.comparing((ErrorResponseDescriptor error) ->
                                HttpStatusNormalizer.normalize(error.status()))
                        .thenComparing(ErrorResponseDescriptor::handlerClass)
                        .thenComparing(ErrorResponseDescriptor::handlerMethod))
                .toList();
    }

    private void addErrorResponseNodes(
            EndpointDescriptor endpoint,
            List<ErrorResponseDescriptor> errors,
            StableId endpointId,
            Map<StableId, Node> nodes,
            List<Edge> edges,
            ElementMetadata metadata) {
        Map<String, List<ErrorResponseDescriptor>> byStatus = errors.stream().collect(
                java.util.stream.Collectors.groupingBy(error -> HttpStatusNormalizer.normalize(error.status()),
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        byStatus.forEach((status, descriptors) -> {
            List<String> types = descriptors.stream().map(ErrorResponseDescriptor::responseType)
                    .filter(type -> !type.isBlank()).distinct().sorted().toList();
            List<String> exceptions = descriptors.stream().flatMap(error -> error.exceptionTypes().stream())
                    .distinct().sorted().toList();
            List<String> handlers = descriptors.stream()
                    .map(error -> error.handlerClass() + "#" + error.handlerMethod()).distinct().sorted().toList();
            List<String> descriptions = descriptors.stream().map(ErrorResponseDescriptor::description)
                    .filter(description -> !description.isBlank()).distinct().sorted().toList();
            String type = types.isEmpty() ? "Error response" : String.join(" | ", types);
            String description = String.join(" ", descriptions);
            Map<String, Object> errorAttributes = GraphSupport.attributes(Map.of(),
                    "status", status,
                    "declaredStatuses", descriptors.stream().map(ErrorResponseDescriptor::status).distinct().sorted().toList(),
                    "javaType", types.size() == 1 ? types.getFirst() : "",
                    "javaTypes", types,
                    "mediaType", endpoint.produces().stream().sorted().findFirst().orElse(""),
                    "errorResponse", true,
                    "exceptionTypes", exceptions,
                    "handlers", handlers,
                    "descriptions", descriptions);
            StableId schema = StableId.of("response:" + endpoint.stableId() + ":" + status);
            Node existing = nodes.get(schema);
            Node responseNode = existing == null
                    ? Node.builder(schema, NodeType.RESPONSE_SCHEMA, type + " (" + status + ")")
                            .description(description).metadata(metadata).attributes(errorAttributes).build()
                    : existing.toBuilder()
                            .description(existing.description().isBlank() ? description : existing.description())
                            .attributes(GraphSupport.attributes(existing.attributes(),
                                    "errorResponse", true,
                                    "errorResponses", descriptors.stream().map(this::errorAttributes).toList()))
                            .build();
            nodes.put(schema, responseNode);
            edges.add(GraphSupport.edge(EdgeType.RETURNS, endpointId, schema, metadata,
                    Map.of("errorResponse", true, "statuses", List.of(status))));
        });
    }

    private Map<String, Object> errorAttributes(ErrorResponseDescriptor error) {
        return GraphSupport.attributes(Map.of(),
                "status", HttpStatusNormalizer.normalize(error.status()),
                "declaredStatus", error.status(),
                "responseType", error.responseType(),
                "description", error.description(),
                "exceptionTypes", error.exceptionTypes(),
                "handlerClass", error.handlerClass(),
                "handlerMethod", error.handlerMethod(),
                "scope", error.globalAdvice() ? "GLOBAL_CONTROLLER_ADVICE" : "CONTROLLER_LOCAL");
    }

    private Evidence errorEvidence(ErrorResponseDescriptor error) {
        String source = relative(error.sourcePosition().file());
        String scope = error.globalAdvice() ? "Global @ControllerAdvice" : "Controller-local @ExceptionHandler";
        return Evidence.of(EvidenceType.SOURCE_CODE, source,
                scope + " " + error.handlerClass() + "#" + error.handlerMethod()
                        + " declares HTTP " + HttpStatusNormalizer.normalize(error.status())
                        + " for " + String.join(", ", error.exceptionTypes()));
    }

    private SourceLocation errorLocation(ErrorResponseDescriptor error) {
        String source = relative(error.sourcePosition().file());
        return new SourceLocation(source, error.sourcePosition().line(), error.sourcePosition().column(),
                error.sourcePosition().line(), error.sourcePosition().column(),
                error.handlerClass() + "#" + error.handlerMethod());
    }

    private void addOpenApiOperation(EndpointDescriptor declaration, StableId endpointId,
                                     Map<StableId, Node> nodes, List<Edge> edges, RefreshContext context) {
        String key = declaration.operationId().isBlank()
                ? declaration.httpMethod() + ":" + declaration.path() : declaration.operationId();
        StableId id = StableId.of("openapi:" + key);
        String source = declarationSource(declaration);
        ElementMetadata metadata = GraphSupport.metadata(EvidenceType.OPENAPI, source,
                "OpenAPI operation " + key, name(), context.targetCommit(), context.analyzedAt(), List.of(), List.of(),
                declaration.sourcePosition() == null ? SourceLocation.of(source)
                        : SourceLocation.line(source, declaration.sourcePosition().line()));
        Map<String, Object> attributes = GraphSupport.attributes(declaration.attributes(), "operationId", declaration.operationId(),
                "method", declaration.httpMethod(), "path", declaration.path(), "parameters", declaration.parameters(),
                "requestBodyType", declaration.requestBodyType(), "responses", declaration.responses());
        nodes.put(id, Node.builder(id, NodeType.OPENAPI_OPERATION,
                declaration.operationId().isBlank() ? declaration.httpMethod() + " " + declaration.path() : declaration.operationId())
                .description(declaration.summary().isBlank() ? declaration.description() : declaration.summary())
                .metadata(metadata).attributes(attributes).build());
        edges.add(GraphSupport.edge(EdgeType.MATCHES_OPERATION, endpointId, id, metadata));
    }

    private EvidenceType evidenceType(EndpointSource source) {
        return switch (source) { case ACTUATOR -> EvidenceType.SPRING_MAPPING; case OPENAPI -> EvidenceType.OPENAPI; case JAVA_SOURCE -> EvidenceType.SOURCE_CODE; };
    }

    private String declarationSource(EndpointDescriptor declaration) {
        return declaration.sourcePosition() == null ? declaration.source().name().toLowerCase(java.util.Locale.ROOT)
                : relative(declaration.sourcePosition().file());
    }

    private void addDao(DomaDaoDescriptor dao, Map<StableId, Node> nodes, List<Edge> edges, RefreshContext context, Collection<String> warnings) {
        String relative = relative(dao.sourcePosition().file()); ElementMetadata metadata = GraphSupport.metadata(EvidenceType.DOMA_MAPPING, relative, "Doma DAO " + dao.qualifiedName(), name(), context.targetCommit(), context.analyzedAt(), warnings, List.of(), SourceLocation.line(relative, dao.sourcePosition().line()));
        StableId daoId = StableId.of(dao.stableId()); nodes.put(daoId, Node.builder(daoId, NodeType.DOMA_DAO, GraphSupport.simpleName(dao.qualifiedName())).description(dao.javadocSummary()).metadata(metadata).attributes(Map.of("qualifiedName", dao.qualifiedName(), "configAutowireable", dao.configAutowireable(), "sourceFingerprint", GraphSupport.fingerprint(dao.qualifiedName(), dao.methods()))).build());
        for (DomaMethodDescriptor method : dao.methods()) {
            StableId methodId = StableId.of(method.stableId()); Map<String, Object> attrs = GraphSupport.attributes(method.attributes(), "daoClass", method.daoClass(), "memberName", method.methodName(), "returnType", method.returnType(), "parameters", method.parameters(), "operation", method.operation().name(), "sqlFileDeclared", method.sqlFileDeclared(), "externalSqlFile", method.externalSqlFile() == null ? "" : relative(method.externalSqlFile()), "sourceFingerprint", GraphSupport.fingerprint(method.daoClass(), method.methodName(), method.operation(), method.parameters()));
            Node methodNode = Node.builder(methodId, NodeType.DOMA_DAO_METHOD, GraphSupport.simpleName(method.daoClass()) + "#" + method.methodName()).description(method.javadocSummary()).metadata(metadata).attributes(attrs).build(); nodes.put(methodId, methodNode); edges.add(GraphSupport.edge(EdgeType.CONTAINS, daoId, methodId, metadata));
        }
    }

    private void addSql(ExternalSqlMapping mapping, Map<StableId, Node> nodes, List<Edge> edges, RefreshContext context) {
        String relative = relative(mapping.sqlFile()); ElementMetadata metadata = GraphSupport.metadata(EvidenceType.SQL_STATIC_ANALYSIS, relative, "Parsed PostgreSQL SQL AST", name(), context.targetCommit(), context.analyzedAt(), mapping.warnings(), List.of(), SourceLocation.of(relative)); StableId daoMethod = StableId.of(mapping.daoMethodId());
        List<SqlStatementAnalysis> statements = mapping.statements();
        for (int index = 0; index < statements.size(); index++) {
            SqlStatementAnalysis statement = statements.get(index); StableId sqlId = StableId.of(mapping.stableId() + (statements.size() > 1 ? "#" + statement.statementIndex() : ""));
            Map<String, Object> attrs = GraphSupport.attributes(Map.of(), "file", relative, "statementIndex", statement.statementIndex(), "kind", statement.kind().name(), "normalizedSql", statement.normalizedSql(), "tables", statement.tables(), "columns", statement.columns(), "joins", statement.joins(), "ctes", statement.ctes(), "functions", statement.functions(), "hasWhere", statement.hasWhere(), "hasSubquery", statement.hasSubquery(), "dynamicTemplate", statement.dynamicTemplate(), "sourceFingerprint", GraphSupport.fingerprint(statement.normalizedSql(), statement.tables(), statement.columns()));
            nodes.put(sqlId, Node.builder(sqlId, NodeType.SQL_STATEMENT, mapping.sqlFile().getFileName() + (statements.size() > 1 ? " #" + (index + 1) : "")).description(statement.kind() + " statement from " + relative).metadata(metadata).attributes(attrs).build());
            edges.add(GraphSupport.edge(EdgeType.EXECUTES_SQL, daoMethod, sqlId, metadata));
            addCrud(statement, sqlId, nodes, edges, metadata);
        }
    }

    private void addCrud(SqlStatementAnalysis statement, StableId sqlId, Map<StableId, Node> nodes, List<Edge> edges, ElementMetadata metadata) {
        for (TableReference table : statement.tables()) {
            String schema = table.schema().isBlank() ? "public" : table.schema(); StableId tableId = GraphSupport.IDS.table(schema, table.table());
            nodes.putIfAbsent(tableId, Node.builder(tableId, NodeType.DB_TABLE, schema + "." + table.table())
                    .description("")
                    .metadata(metadata).attributes(Map.of("schema", schema, "table", table.table(), "inferredFromSql", true)).build());
            for (CrudOperation operation : table.operations()) edges.add(GraphSupport.edge(crudEdge(operation), sqlId, tableId, metadata));
            for (ColumnReference column : statement.columns()) {
                if (column.column().equals("*")) continue;
                boolean matches = column.qualifier().isBlank() || column.qualifier().equalsIgnoreCase(table.alias()) || column.qualifier().equalsIgnoreCase(table.table());
                if (matches) {
                    StableId columnId = GraphSupport.IDS.column(schema, table.table(), column.column());
                    nodes.putIfAbsent(columnId, Node.builder(columnId, NodeType.DB_COLUMN, schema + "." + table.table() + "." + column.column())
                            .description("")
                            .metadata(metadata).attributes(Map.of("schema", schema, "table", table.table(), "column", column.column(), "usage", column.usage().name(), "inferredFromSql", true)).build());
                    edges.add(GraphSupport.edge(EdgeType.CONTAINS, tableId, columnId, metadata));
                    for (CrudOperation operation : table.operations()) edges.add(GraphSupport.edge(crudEdge(operation), sqlId, columnId, metadata));
                }
            }
        }
    }

    private EdgeType crudEdge(CrudOperation operation) { return switch (operation) { case CREATE -> EdgeType.CREATES; case READ -> EdgeType.READS; case UPDATE -> EdgeType.UPDATES; case DELETE -> EdgeType.DELETES; }; }
    private Path resourceRoot() { if (!repository.config().mandala.source.resources.roots.isEmpty()) return repository.resolve(repository.config().mandala.source.resources.roots.getFirst()); if (!repository.config().mandala.doma.sqlRoots.isEmpty()) { Path sql = repository.resolve(repository.config().mandala.doma.sqlRoots.getFirst()); return sql.getFileName().toString().equals("META-INF") ? sql.getParent() : sql; } return null; }
    private String relative(Path path) { try { return repository.root().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'); } catch (IllegalArgumentException outside) { return path.toString().replace('\\', '/'); } }
}
