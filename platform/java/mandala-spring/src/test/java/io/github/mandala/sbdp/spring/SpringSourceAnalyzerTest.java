package io.github.mandala.sbdp.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringSourceAnalyzerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsMvcMappingsParametersValidationStatusAndJavadoc() throws Exception {
        Path sourceRoot = temporaryDirectory.resolve("src/main/java");
        Path source = sourceRoot.resolve("com/example/ProjectController.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                @RestController
                @RequestMapping(path = ApiPaths.ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
                class ProjectController {
                    /** Creates a project. Additional implementation detail. */
                    @PostMapping(path = {"/projects", "/work"}, consumes = "application/json")
                    @ResponseStatus(HttpStatus.CREATED)
                    @Operation(operationId = "createProject", summary = "Create project")
                    ResponseEntity<ProjectResponse> create(
                            @Valid @RequestBody CreateProjectRequest request,
                            @RequestHeader(name = "X-Tenant", required = true) String tenant) {
                        return null;
                    }

                    @ExceptionHandler(NotFoundException.class)
                    @ResponseStatus(HttpStatus.NOT_FOUND)
                    ErrorResponse notFound(NotFoundException exception) { return null; }
                }

                @Service
                class ProjectService {
                    public void createProject() {}
                }
                """);
        Files.writeString(source.getParent().resolve("ApiPaths.java"), """
                package com.example;
                final class ApiPaths {
                    static final String PREFIX = "/api";
                    static final String ROOT = PREFIX;
                }
                """);

        SpringSourceAnalysis analysis = new SpringSourceAnalyzer().analyze(sourceRoot);

        assertEquals(2, analysis.endpoints().size());
        EndpointDescriptor endpoint = analysis.endpoints().stream()
                .filter(candidate -> candidate.path().equals("/api/projects"))
                .findFirst()
                .orElseThrow();
        assertEquals("POST", endpoint.httpMethod());
        assertEquals("CreateProjectRequest", endpoint.requestBodyType());
        assertEquals("CREATED", endpoint.responses().getFirst().status());
        assertEquals("createProject", endpoint.operationId());
        assertEquals(java.util.Set.of("application/json"), endpoint.produces());
        assertTrue(endpoint.parameters().stream()
                .anyMatch(parameter -> parameter.location() == ParameterLocation.BODY
                        && parameter.validation().contains("@Valid")));
        assertTrue(endpoint.parameters().stream()
                .anyMatch(parameter -> parameter.name().equals("X-Tenant")
                        && parameter.location() == ParameterLocation.HEADER));
        assertTrue(analysis.symbols().stream()
                .anyMatch(symbol -> symbol.kind().equals("APPLICATION_SERVICE")
                        && symbol.qualifiedName().equals("com.example.ProjectService")));
        assertEquals(1, analysis.errorResponses().size());
        assertTrue(analysis.warnings().isEmpty(), () -> String.join("\n", analysis.warnings()));
    }

    @Test
    void retainsOverloadedMethodsUsingCanonicalParameterTypesInStableIds() throws Exception {
        Path sourceRoot = temporaryDirectory.resolve("overloads/src/main/java");
        Path source = sourceRoot.resolve("com/example/SearchService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                @Service
                class SearchService {
                    public void find(String query) {}
                    public void find(long id) {}
                    public void find(java.util.Map<String, Long> filters, int limit) {}
                }
                """);

        SpringSourceAnalysis analysis = new SpringSourceAnalyzer().analyze(sourceRoot);
        java.util.Set<String> ids = analysis.symbols().stream()
                .filter(symbol -> symbol.memberName().equals("find"))
                .map(JavaSymbolDescriptor::stableId)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(java.util.Set.of(
                "java:com.example.SearchService#find(String)",
                "java:com.example.SearchService#find(long)",
                "java:com.example.SearchService#find(java.util.Map<String,Long>,int)"), ids);
    }

    @Test
    void extractsGlobalAdvicePayloadStatusDescriptionAndConservativelyRetainsScopedAdvice() throws Exception {
        Path sourceRoot = temporaryDirectory.resolve("advice/src/main/java");
        Path source = sourceRoot.resolve("com/example/GlobalErrors.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                @RestControllerAdvice
                class GlobalErrors {
                    /** Resource lookup failed. */
                    @ExceptionHandler(MissingResource.class)
                    ResponseEntity<ApiProblem> missing(MissingResource failure) {
                        return respond(HttpStatus.NOT_FOUND, failure);
                    }

                    @ExceptionHandler(InvalidInput.class)
                    Mono<ResponseEntity<ApiProblem>> invalid(InvalidInput failure) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                }

                @ControllerAdvice(basePackages = "com.example.internal")
                class InternalErrors {
                    @ExceptionHandler(IllegalStateException.class)
                    @ResponseStatus(HttpStatus.CONFLICT)
                    ApiProblem conflict(IllegalStateException failure) { return null; }
                }
                """);

        SpringSourceAnalysis analysis = new SpringSourceAnalyzer().analyze(sourceRoot);

        ErrorResponseDescriptor missing = analysis.errorResponses().stream()
                .filter(error -> error.handlerMethod().equals("missing")).findFirst().orElseThrow();
        assertTrue(missing.globalAdvice());
        assertEquals("NOT_FOUND", missing.status());
        assertEquals("ApiProblem", missing.responseType());
        assertEquals("Resource lookup failed.", missing.description());

        ErrorResponseDescriptor invalid = analysis.errorResponses().stream()
                .filter(error -> error.handlerMethod().equals("invalid")).findFirst().orElseThrow();
        assertTrue(invalid.globalAdvice());
        assertEquals("400", invalid.status());
        assertEquals("ApiProblem", invalid.responseType());
        assertTrue(invalid.description().contains("InvalidInput"));

        ErrorResponseDescriptor scoped = analysis.errorResponses().stream()
                .filter(error -> error.handlerMethod().equals("conflict")).findFirst().orElseThrow();
        assertTrue(!scoped.globalAdvice(), "selector-scoped advice must not be applied to every endpoint");
        assertEquals("CONFLICT", scoped.status());
    }
}
