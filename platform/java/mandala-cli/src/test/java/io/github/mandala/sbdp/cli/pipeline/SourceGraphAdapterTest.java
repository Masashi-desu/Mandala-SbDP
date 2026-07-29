package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.cli.RepositoryContext;
import io.github.mandala.sbdp.cli.config.MandalaConfig;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.StableId;
import io.github.mandala.sbdp.renderer.PagePaths;
import io.github.mandala.sbdp.renderer.RenderOptions;
import io.github.mandala.sbdp.renderer.StaticSiteRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceGraphAdapterTest {
    @TempDir
    Path root;

    @Test
    void attachesGlobalAndControllerLocalErrorResponsesToGraphAndRenderedEndpoint() throws Exception {
        Path sourceRoot = root.resolve("src/main/java/example");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("ProjectController.java"), """
                package example;

                @RestController
                class ProjectController {
                    @GetMapping("/projects")
                    Project list() { return null; }

                    @ExceptionHandler(ArchivedProject.class)
                    @ResponseStatus(HttpStatus.GONE)
                    LocalProblem archived(ArchivedProject failure) { return null; }
                }
                """);
        Files.writeString(sourceRoot.resolve("TaskController.java"), """
                package example;

                @RestController
                class TaskController {
                    @GetMapping("/tasks")
                    Task list() { return null; }
                }
                """);
        Files.writeString(sourceRoot.resolve("GlobalErrors.java"), """
                package example;

                @RestControllerAdvice
                class GlobalErrors {
                    /** Requested resource was not found. */
                    @ExceptionHandler(MissingResource.class)
                    ResponseEntity<ApiProblem> missing(MissingResource failure) {
                        return respond(HttpStatus.NOT_FOUND, failure);
                    }
                }
                """);

        MandalaConfig config = new MandalaConfig();
        config.mandala.project.id = "error-response-test";
        config.mandala.source.java.roots = List.of("src/main/java");
        Instant analyzedAt = Instant.parse("2026-07-22T00:00:00Z");
        RepositoryContext repository = new RepositoryContext(root, root.resolve("mandala.yml"), config,
                "commit-a", analyzedAt);
        DocumentationGraph graph = new SourceGraphAdapter(repository).analyze(new RefreshContext(
                "error-response-test", "commit-a", "config-a", root, analyzedAt, Map.of()));

        Node projects = graph.node(StableId.of("endpoint:GET:/projects")).orElseThrow();
        Node tasks = graph.node(StableId.of("endpoint:GET:/tasks")).orElseThrow();
        List<?> projectErrors = (List<?>) projects.attributes().get("errorResponses");
        List<?> taskErrors = (List<?>) tasks.attributes().get("errorResponses");
        assertEquals(2, projectErrors.size(), "global and controller-local handlers apply to ProjectController");
        assertEquals(1, taskErrors.size(), "only global advice applies to TaskController");
        assertTrue(projects.metadata().evidence().stream()
                .anyMatch(evidence -> evidence.description().contains("Global @ControllerAdvice")
                        && evidence.description().contains("HTTP 404")));

        Node notFound = graph.node(StableId.of("response:endpoint:GET:/projects:404")).orElseThrow();
        assertEquals(NodeType.RESPONSE_SCHEMA, notFound.type());
        assertEquals(true, notFound.attributes().get("errorResponse"));
        assertEquals("ApiProblem", notFound.attributes().get("javaType"));
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.type() == EdgeType.RETURNS
                && edge.from().equals(projects.id()) && edge.to().equals(notFound.id())));
        assertFalse(graph.node(StableId.of("response:endpoint:GET:/tasks:410")).isPresent(),
                "controller-local handlers must not leak to unrelated endpoints");

        Path site = root.resolve("rendered");
        StaticSiteRenderer.RenderResult rendered = new StaticSiteRenderer().render(
                graph, site, root.resolve("custom"), RenderOptions.defaults());
        assertTrue(rendered.brokenLinks().isEmpty(), rendered.brokenLinks().toString());
        String endpointPage = Files.readString(site.resolve(PagePaths.forNode(projects)));
        assertTrue(endpointPage.contains("errorResponses"));
        assertTrue(endpointPage.contains("ApiProblem"));
        assertTrue(endpointPage.contains("Requested resource was not found."));
        assertTrue(endpointPage.contains("GlobalErrors#missing"));
    }
}
