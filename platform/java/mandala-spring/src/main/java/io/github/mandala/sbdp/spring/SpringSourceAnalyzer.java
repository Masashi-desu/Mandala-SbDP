package io.github.mandala.sbdp.spring;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Static Spring MVC/WebFlux source analyzer. It deliberately has no runtime Spring dependency, so
 * it can inspect arbitrary source trees from CLI and Gradle tasks.
 */
public final class SpringSourceAnalyzer {
    private static final Map<String, String> MAPPING_METHODS = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "PatchMapping", "PATCH",
            "DeleteMapping", "DELETE");

    private static final Set<String> VALIDATION_ANNOTATIONS = Set.of(
            "Valid", "Validated", "NotNull", "NonNull", "NotBlank", "NotEmpty", "Size",
            "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
            "Negative", "NegativeOrZero", "Pattern", "Email", "Past", "PastOrPresent",
            "Future", "FutureOrPresent", "AssertTrue", "AssertFalse");

    private static final Set<String> RESPONSE_WRAPPERS = Set.of(
            "ResponseEntity", "HttpEntity", "Mono", "Flux", "CompletionStage", "CompletableFuture");

    private static final Map<String, String> RESPONSE_BUILDER_STATUSES = Map.ofEntries(
            Map.entry("ok", "200"),
            Map.entry("created", "201"),
            Map.entry("accepted", "202"),
            Map.entry("noContent", "204"),
            Map.entry("badRequest", "400"),
            Map.entry("notFound", "404"),
            Map.entry("unprocessableEntity", "422"),
            Map.entry("internalServerError", "500"));

    private static final Map<String, String> SPRING_CONSTANTS = Map.ofEntries(
            Map.entry("APPLICATION_JSON_VALUE", "application/json"),
            Map.entry("APPLICATION_XML_VALUE", "application/xml"),
            Map.entry("APPLICATION_FORM_URLENCODED_VALUE", "application/x-www-form-urlencoded"),
            Map.entry("MULTIPART_FORM_DATA_VALUE", "multipart/form-data"),
            Map.entry("TEXT_PLAIN_VALUE", "text/plain"),
            Map.entry("TEXT_HTML_VALUE", "text/html"),
            Map.entry("ALL_VALUE", "*/*"));

    private final JavaParser parser;

    public SpringSourceAnalyzer() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(configuration);
    }

    public SpringSourceAnalysis analyze(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Java source root does not exist: " + sourceRoot);
        }
        List<EndpointDescriptor> endpoints = new ArrayList<>();
        List<JavaSymbolDescriptor> symbols = new ArrayList<>();
        List<ErrorResponseDescriptor> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<ParsedUnit> parsedUnits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                ParseResult<CompilationUnit> result = parser.parse(file);
                if (result.getResult().isEmpty()) {
                    warnings.add(file + ": " + result.getProblems());
                    continue;
                }
                parsedUnits.add(new ParsedUnit(file, result.getResult().orElseThrow()));
                result.getProblems().forEach(problem -> warnings.add(file + ": " + problem.getMessage()));
            }
        }
        Map<String, String> projectConstants = new LinkedHashMap<>(SPRING_CONSTANTS);
        for (int pass = 0; pass < 4; pass++) {
            parsedUnits.forEach(parsed -> projectConstants.putAll(
                    JavaAnnotationValues.stringConstants(parsed.unit(), projectConstants)));
        }
        parsedUnits.forEach(parsed -> analyzeUnit(
                parsed.file(), parsed.unit(), projectConstants, endpoints, symbols, errors, warnings));

        endpoints.sort(Comparator.comparing((EndpointDescriptor endpoint) -> endpoint.stableId())
                .thenComparing(EndpointDescriptor::controllerClass));
        symbols.sort(Comparator.comparing(JavaSymbolDescriptor::stableId));
        errors.sort(Comparator.comparing(ErrorResponseDescriptor::handlerClass)
                .thenComparing(ErrorResponseDescriptor::handlerMethod));
        return new SpringSourceAnalysis(endpoints, symbols, errors, warnings);
    }

    private void analyzeUnit(
            Path file,
            CompilationUnit unit,
            Map<String, String> projectConstants,
            List<EndpointDescriptor> endpoints,
            List<JavaSymbolDescriptor> symbols,
            List<ErrorResponseDescriptor> errors,
            List<String> warnings) {
        Map<String, String> constants = JavaAnnotationValues.stringConstants(unit, projectConstants);
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            boolean controller = hasAnnotation(type, "Controller") || hasAnnotation(type, "RestController")
                    || type.getMethods().stream().anyMatch(this::hasMapping);
            boolean controllerAdvice = hasAnnotation(type, "ControllerAdvice")
                    || hasAnnotation(type, "RestControllerAdvice");
            boolean globalAdvice = controllerAdvice && isUnscopedControllerAdvice(type);
            boolean applicationService = hasAnnotation(type, "Service")
                    || hasAnnotation(type, "Repository")
                    || hasAnnotation(type, "Component");
            String qualifiedName = qualifiedName(unit, type);

            if (controller || applicationService) {
                String kind = controller ? "CONTROLLER" : "APPLICATION_SERVICE";
                String declaration = (type.isInterface() ? "interface " : "class ") + type.getNameAsString();
                symbols.add(symbol(file, type, qualifiedName, "", kind, declaration, type));
            }

            Mapping classMapping = mapping(type, constants).orElse(Mapping.empty());
            for (MethodDeclaration method : type.getMethods()) {
                if (hasAnnotation(method, "ExceptionHandler")) {
                    errors.add(errorDescriptor(file, qualifiedName, method, constants, globalAdvice));
                }
                if (controller && hasMapping(method)) {
                    symbols.add(symbol(
                            file,
                            method,
                            qualifiedName,
                            method.getNameAsString(),
                            "HANDLER_METHOD",
                            method.getDeclarationAsString(false, false, false),
                            method));
                    Mapping methodMapping = mapping(method, constants).orElse(Mapping.empty());
                    endpoints.addAll(toEndpoints(file, qualifiedName, method, classMapping, methodMapping, constants));
                } else if (applicationService && method.isPublic()) {
                    symbols.add(symbol(
                            file,
                            method,
                            qualifiedName,
                            method.getNameAsString(),
                            "APPLICATION_METHOD",
                            method.getDeclarationAsString(false, false, false),
                            method));
                }
            }
        }
    }

    private List<EndpointDescriptor> toEndpoints(
            Path file,
            String controller,
            MethodDeclaration method,
            Mapping classMapping,
            Mapping methodMapping,
            Map<String, String> constants) {
        List<String> classPaths = classMapping.paths().isEmpty() ? List.of("") : classMapping.paths();
        List<String> methodPaths = methodMapping.paths().isEmpty() ? List.of("") : methodMapping.paths();
        Set<String> methods = methodMapping.methods().isEmpty() ? Set.of("ANY") : methodMapping.methods();
        Set<String> consumes = methodMapping.consumes().isEmpty() ? classMapping.consumes() : methodMapping.consumes();
        Set<String> produces = methodMapping.produces().isEmpty() ? classMapping.produces() : methodMapping.produces();
        List<EndpointParameter> parameters = method.getParameters().stream()
                .map(parameter -> parameter(parameter, constants))
                .toList();
        String requestBody = parameters.stream()
                .filter(parameter -> parameter.location() == ParameterLocation.BODY)
                .map(EndpointParameter::javaType)
                .findFirst()
                .orElse("");
        String status = responseStatus(method).orElse("200");
        String operationId = JavaAnnotationValues.annotation(method, "Operation")
                .flatMap(annotation -> first(JavaAnnotationValues.strings(annotation, "operationId", constants)))
                .orElse("");
        String operationSummary = JavaAnnotationValues.annotation(method, "Operation")
                .flatMap(annotation -> first(JavaAnnotationValues.strings(annotation, "summary", constants)))
                .orElseGet(() -> javadocSummary(method));
        String operationDescription = JavaAnnotationValues.annotation(method, "Operation")
                .flatMap(annotation -> first(JavaAnnotationValues.strings(annotation, "description", constants)))
                .orElse("");

        List<EndpointDescriptor> result = new ArrayList<>();
        for (String httpMethod : methods) {
            for (String classPath : classPaths) {
                for (String methodPath : methodPaths) {
                    String path = combinePath(classPath, methodPath);
                    Map<String, Object> attributes = new LinkedHashMap<>();
                    attributes.put("javaSignature", method.getDeclarationAsString(false, false, false));
                    if (!methodMapping.params().isEmpty()) {
                        attributes.put("mappingParams", methodMapping.params());
                    }
                    if (!methodMapping.headers().isEmpty()) {
                        attributes.put("mappingHeaders", methodMapping.headers());
                    }
                    result.add(new EndpointDescriptor(
                            EndpointDescriptor.stableId(httpMethod, path),
                            httpMethod,
                            path,
                            controller,
                            method.getNameAsString(),
                            consumes,
                            produces,
                            parameters,
                            requestBody,
                            List.of(new EndpointResponse(status, method.getTypeAsString(), firstOrEmpty(produces), "")),
                            operationId,
                            operationSummary,
                            operationDescription,
                            EndpointSource.JAVA_SOURCE,
                            position(file, method),
                            attributes));
                }
            }
        }
        return result;
    }

    private EndpointParameter parameter(Parameter parameter, Map<String, String> constants) {
        ParameterLocation location = ParameterLocation.UNANNOTATED;
        AnnotationExpr binding = null;
        for (Map.Entry<String, ParameterLocation> entry : Map.of(
                        "PathVariable", ParameterLocation.PATH,
                        "RequestParam", ParameterLocation.QUERY,
                        "RequestHeader", ParameterLocation.HEADER,
                        "CookieValue", ParameterLocation.COOKIE,
                        "RequestBody", ParameterLocation.BODY,
                        "ModelAttribute", ParameterLocation.MODEL)
                .entrySet()) {
            Optional<AnnotationExpr> candidate = JavaAnnotationValues.annotation(parameter, entry.getKey());
            if (candidate.isPresent()) {
                location = entry.getValue();
                binding = candidate.orElseThrow();
                break;
            }
        }
        String name = parameter.getNameAsString();
        boolean required = location == ParameterLocation.PATH || location == ParameterLocation.BODY;
        String defaultValue = "";
        if (binding != null) {
            List<String> names = JavaAnnotationValues.strings(binding, "name", constants);
            if (names.isEmpty()) {
                names = JavaAnnotationValues.strings(binding, "value", constants);
            }
            if (!names.isEmpty()) {
                name = names.getFirst();
            }
            required = JavaAnnotationValues.booleanValue(binding, "required").orElse(required);
            defaultValue = first(JavaAnnotationValues.strings(binding, "defaultValue", constants)).orElse("");
            if (!defaultValue.isEmpty()) {
                required = false;
            }
        }
        List<String> validation = parameter.getAnnotations().stream()
                .filter(annotation -> VALIDATION_ANNOTATIONS.contains(JavaAnnotationValues.simpleName(annotation)))
                .map(AnnotationExpr::toString)
                .toList();
        return new EndpointParameter(
                name, location, parameter.getTypeAsString(), required, defaultValue, validation, "");
    }

    private Optional<Mapping> mapping(NodeWithAnnotations<?> node, Map<String, String> constants) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String annotationName = JavaAnnotationValues.simpleName(annotation);
            if (MAPPING_METHODS.containsKey(annotationName)) {
                return Optional.of(new Mapping(
                        paths(annotation, constants),
                        Set.of(MAPPING_METHODS.get(annotationName)),
                        values(annotation, "consumes", constants),
                        values(annotation, "produces", constants),
                        values(annotation, "params", constants),
                        values(annotation, "headers", constants)));
            }
            if (annotationName.equals("RequestMapping")) {
                Set<String> methods = new LinkedHashSet<>();
                for (Expression expression : JavaAnnotationValues.expressions(annotation, "method")) {
                    String value = expression.toString();
                    int dot = value.lastIndexOf('.');
                    methods.add((dot < 0 ? value : value.substring(dot + 1)).toUpperCase());
                }
                return Optional.of(new Mapping(
                        paths(annotation, constants),
                        methods,
                        values(annotation, "consumes", constants),
                        values(annotation, "produces", constants),
                        values(annotation, "params", constants),
                        values(annotation, "headers", constants)));
            }
        }
        return Optional.empty();
    }

    private List<String> paths(AnnotationExpr annotation, Map<String, String> constants) {
        List<String> paths = JavaAnnotationValues.strings(annotation, "path", constants);
        if (paths.isEmpty()) {
            paths = JavaAnnotationValues.strings(annotation, "value", constants);
        }
        return paths;
    }

    private Set<String> values(AnnotationExpr annotation, String attribute, Map<String, String> constants) {
        return new LinkedHashSet<>(JavaAnnotationValues.strings(annotation, attribute, constants));
    }

    private ErrorResponseDescriptor errorDescriptor(
            Path file, String qualifiedName, MethodDeclaration method, Map<String, String> constants,
            boolean globalAdvice) {
        List<String> exceptions = JavaAnnotationValues.annotation(method, "ExceptionHandler")
                .map(annotation -> JavaAnnotationValues.expressions(annotation, "value").stream()
                        .map(Expression::toString)
                        .map(value -> value.endsWith(".class") ? value.substring(0, value.length() - 6) : value)
                        .toList())
                .orElseGet(List::of);
        if (exceptions.isEmpty()) {
            exceptions = method.getParameters().stream().map(Parameter::getTypeAsString).toList();
        }
        String status = responseStatus(method).orElse("UNSPECIFIED");
        String description = javadocSummary(method);
        if (description.isBlank()) {
            String handled = exceptions.isEmpty() ? "an exception" : String.join(", ", exceptions);
            description = "Handles " + handled + (status.equals("UNSPECIFIED") ? "" : " as HTTP " + status) + ".";
        }
        return new ErrorResponseDescriptor(
                qualifiedName,
                method.getNameAsString(),
                exceptions,
                status,
                responsePayloadType(method.getType()),
                description,
                globalAdvice,
                position(file, method));
    }

    private boolean isUnscopedControllerAdvice(ClassOrInterfaceDeclaration type) {
        return type.getAnnotations().stream()
                .filter(annotation -> Set.of("ControllerAdvice", "RestControllerAdvice")
                        .contains(JavaAnnotationValues.simpleName(annotation)))
                .noneMatch(annotation -> List.of(
                                "value", "basePackages", "basePackageClasses", "assignableTypes", "annotations")
                        .stream().anyMatch(attribute -> !JavaAnnotationValues.expressions(annotation, attribute).isEmpty()));
    }

    private String responsePayloadType(Type type) {
        if (type instanceof ClassOrInterfaceType declared
                && RESPONSE_WRAPPERS.contains(declared.getName().getIdentifier())
                && declared.getTypeArguments().filter(arguments -> arguments.size() == 1).isPresent()) {
            return responsePayloadType(declared.getTypeArguments().orElseThrow().getFirst().orElseThrow());
        }
        return type.asString();
    }

    private JavaSymbolDescriptor symbol(
            Path file,
            NodeWithAnnotations<?> annotatedNode,
            String qualifiedName,
            String member,
            String kind,
            String signature,
            Node documentedNode) {
        String memberIdentity = member.isEmpty() ? "" : "#" + member;
        if (annotatedNode instanceof MethodDeclaration method) {
            memberIdentity += method.getParameters().stream()
                    .map(this::canonicalParameterType)
                    .collect(java.util.stream.Collectors.joining(",", "(", ")"));
        }
        String id = "java:" + qualifiedName + memberIdentity;
        Node node = (Node) annotatedNode;
        List<String> annotations = annotatedNode.getAnnotations().stream().map(AnnotationExpr::toString).toList();
        return new JavaSymbolDescriptor(
                id,
                qualifiedName,
                member,
                kind,
                signature,
                javadocSummary(documentedNode),
                annotations,
                position(file, node));
    }

    private String canonicalParameterType(Parameter parameter) {
        String type = parameter.getTypeAsString().replaceAll("\\s+", "");
        return parameter.isVarArgs() ? type + "..." : type;
    }

    private String javadocSummary(Node node) {
        String text = node.getComment()
                .filter(comment -> comment.isJavadocComment())
                .map(comment -> comment.asJavadocComment().parse().getDescription().toText().trim())
                .orElse("");
        if (text.isEmpty()) {
            return "";
        }
        int period = text.indexOf('.');
        int japanesePeriod = text.indexOf('。');
        int end = period < 0 ? japanesePeriod : japanesePeriod < 0 ? period : Math.min(period, japanesePeriod);
        return end < 0 ? text : text.substring(0, end + 1);
    }

    private Optional<String> responseStatus(MethodDeclaration method) {
        Optional<String> annotated = JavaAnnotationValues.annotation(method, "ResponseStatus").map(annotation -> {
            String status = JavaAnnotationValues.enumValue(annotation, "code");
            if (status.isEmpty()) {
                status = JavaAnnotationValues.enumValue(annotation, "value");
            }
            return status.isEmpty() ? "INTERNAL_SERVER_ERROR" : status;
        });
        if (annotated.isPresent()) return annotated;

        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("status") && !call.getArguments().isEmpty()) {
                Optional<String> status = statusExpression(call.getArgument(0));
                if (status.isPresent()) return status;
            }
            String builderStatus = RESPONSE_BUILDER_STATUSES.get(call.getNameAsString());
            if (builderStatus != null && call.getScope().map(Expression::toString)
                    .filter(scope -> scope.endsWith("ResponseEntity") || scope.endsWith("ServerResponse"))
                    .isPresent()) {
                return Optional.of(builderStatus);
            }
        }
        return method.findAll(FieldAccessExpr.class).stream()
                .map(this::statusExpression)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<String> statusExpression(Expression expression) {
        if (expression instanceof IntegerLiteralExpr integer) {
            String value = integer.getValue().replace("_", "");
            return value.matches("[1-5][0-9]{2}") ? Optional.of(value) : Optional.empty();
        }
        if (expression instanceof FieldAccessExpr field) {
            String scope = field.getScope().toString();
            if (scope.endsWith("HttpStatus") || scope.endsWith("HttpStatusCode")) {
                return Optional.of(field.getNameAsString());
            }
        }
        return Optional.empty();
    }

    private boolean hasMapping(MethodDeclaration method) {
        return method.getAnnotations().stream().map(JavaAnnotationValues::simpleName)
                .anyMatch(name -> name.equals("RequestMapping") || MAPPING_METHODS.containsKey(name));
    }

    private boolean hasAnnotation(NodeWithAnnotations<?> node, String name) {
        return JavaAnnotationValues.annotation(node, name).isPresent();
    }

    private String qualifiedName(CompilationUnit unit, ClassOrInterfaceDeclaration type) {
        List<String> names = new ArrayList<>();
        Node current = type;
        while (current instanceof ClassOrInterfaceDeclaration declaration) {
            names.addFirst(declaration.getNameAsString());
            current = declaration.getParentNode().orElse(null);
        }
        String prefix = unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString() + ".").orElse("");
        return prefix + String.join(".", names);
    }

    private SourcePosition position(Path file, Node node) {
        var begin = node.getBegin().orElseThrow();
        return new SourcePosition(file, begin.line, begin.column);
    }

    private String combinePath(String left, String right) {
        return EndpointDescriptor.normalizePath(left + "/" + right);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private static String firstOrEmpty(Set<String> values) {
        return values.stream().findFirst().orElse("");
    }

    private record Mapping(
            List<String> paths,
            Set<String> methods,
            Set<String> consumes,
            Set<String> produces,
            Set<String> params,
            Set<String> headers) {
        private Mapping {
            paths = List.copyOf(paths);
            methods = immutableSortedSet(methods);
            consumes = immutableSortedSet(consumes);
            produces = immutableSortedSet(produces);
            params = immutableSortedSet(params);
            headers = immutableSortedSet(headers);
        }

        static Mapping empty() {
            return new Mapping(List.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }

        private static Set<String> immutableSortedSet(Set<String> values) {
            if (values.isEmpty()) return Set.of();
            return java.util.Collections.unmodifiableSet(
                    new java.util.LinkedHashSet<>(new java.util.TreeSet<>(values)));
        }
    }

    private record ParsedUnit(Path file, CompilationUnit unit) {}
}
