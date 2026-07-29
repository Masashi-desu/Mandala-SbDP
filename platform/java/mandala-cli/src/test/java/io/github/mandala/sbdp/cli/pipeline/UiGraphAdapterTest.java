package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.cli.RepositoryContext;
import io.github.mandala.sbdp.cli.config.MandalaConfig;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.StableId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiGraphAdapterTest {
    @TempDir Path root;

    @Test
    void importsActionLevelStateTransitionsConditionsAndRelatedHttp() throws Exception {
        Path observations = root.resolve("mandala/snapshots/ui");
        Files.createDirectories(observations);
        Files.writeString(observations.resolve("project-create-success.json"), """
                {
                  "schemaVersion": "1.1",
                  "id": "project-create-success",
                  "title": "プロジェクト作成成功",
                  "route": "/projects/new",
                  "pageUrl": "http://127.0.0.1:5173/projects/7",
                  "state": "success",
                  "screenName": "プロジェクト詳細",
                  "screenshot": "mandala/generated/sample-app/screenshots/project-create-success.png",
                  "ariaSnapshot": "Project 7",
                  "domSnapshot": "Project 7",
                  "actions": [
                    {"kind":"fill","label":"プロジェクト名","value":"Mandala"},
                    {"kind":"click","role":"button","name":"プロジェクトを作成"}
                  ],
                  "transitions": [
                    {
                      "sequence": 0,
                      "from": {"route":"/projects/new","state":"normal","name":"プロジェクトを作成","screenshot":"mandala/generated/sample-app/screenshots/project-create-success-step-0.png"},
                      "action": {"kind":"fill","label":"プロジェクト名","value":"Mandala"},
                      "to": {"route":"/projects/new","state":"normal","name":"プロジェクトを作成","screenshot":"mandala/generated/sample-app/screenshots/project-create-success-step-1.png"},
                      "condition": {"role":"ADMIN","featureFlags":{"projectCreation":true},"outcome":"normal","scenarioOutcome":"success"},
                      "relatedHttp": []
                    },
                    {
                      "sequence": 1,
                      "from": {"route":"/projects/new","state":"normal","name":"プロジェクトを作成","screenshot":"mandala/generated/sample-app/screenshots/project-create-success-step-1.png"},
                      "action": {"kind":"click","role":"button","name":"プロジェクトを作成"},
                      "to": {"route":"/projects/7","state":"success","name":"プロジェクト詳細","screenshot":"mandala/generated/sample-app/screenshots/project-create-success.png"},
                      "condition": {"role":"ADMIN","featureFlags":{"projectCreation":true},"outcome":"success","scenarioOutcome":"success"},
                      "relatedHttp": [
                        {"method":"POST","path":"/api/projects","status":201,"mockId":"project-create-success:0","undefined":false}
                      ]
                    }
                  ],
                  "requests": [
                    {"method":"GET","path":"/api/bootstrap","status":200,"mockId":"project-create-success:initial","undefined":false},
                    {"method":"POST","path":"/api/projects","status":201,"mockId":"project-create-success:0","undefined":false,"actionSequence":1},
                    {"method":"GET","path":"/api/projects/{id}","status":200,"mockId":"project-create-success:1","undefined":false,"actionSequence":1}
                  ],
                  "consoleErrors": [],
                  "environment": {"role":"ADMIN","featureFlags":{"projectCreation":true}},
                  "capturedAt": "2026-07-28T00:00:00Z"
                }
                """);
        MandalaConfig config = new MandalaConfig();
        config.mandala.project.id = "ui-transition-test";
        RepositoryContext repository = new RepositoryContext(root, root.resolve("mandala.yml"), config,
                "commit-a", Instant.parse("2026-07-28T00:00:00Z"));

        DocumentationGraph graph = new UiGraphAdapter(repository).analyze(new RefreshContext(
                "ui-transition-test", "commit-a", "config-a", root,
                Instant.parse("2026-07-28T00:00:00Z"), Map.of()));

        StableId sourceState = StableId.of("screen-state:/projects/new:normal.project-create-success");
        StableId targetState = StableId.of("screen-state:/projects/{id}:success.project-create-success");
        StableId fill = StableId.of("action:project-create-success:0");
        StableId submit = StableId.of("action:project-create-success:1");
        Node submitNode = graph.node(submit).orElseThrow();
        assertEquals("/projects/new", submitNode.attributes().get("fromRoute"));
        assertEquals("normal", submitNode.attributes().get("fromState"));
        assertEquals("/projects/{id}", submitNode.attributes().get("toRoute"));
        assertEquals("success", submitNode.attributes().get("toState"));
        assertEquals("ADMIN", submitNode.attributes().get("role"));
        assertEquals(Map.of("projectCreation", true), submitNode.attributes().get("featureFlags"));
        assertEquals("success", submitNode.attributes().get("scenarioOutcome"));
        assertTrue(graph.node(sourceState).isPresent());
        assertTrue(graph.node(targetState).isPresent());
        StableId sourceScreenshot = StableId.of("screenshot:project-create-success:step:0");
        StableId finalScreenshot = StableId.of("screenshot:project-create-success");
        assertTrue(graph.node(sourceScreenshot).isPresent());
        assertTrue(graph.node(finalScreenshot).isPresent());
        assertEquals("プロジェクトを作成",
                graph.node(sourceScreenshot).orElseThrow().attributes().get("screenName"));
        assertEquals("プロジェクト詳細",
                graph.node(finalScreenshot).orElseThrow().attributes().get("screenName"));
        assertTrue(hasEdge(graph, EdgeType.CAPTURED_AS, sourceState, sourceScreenshot));
        assertTrue(hasEdge(graph, EdgeType.CAPTURED_AS, targetState, finalScreenshot));

        assertTrue(hasEdge(graph, EdgeType.PERFORMED_ON, sourceState, submit));
        assertTrue(hasEdge(graph, EdgeType.TRANSITIONS_TO, submit, targetState));
        assertTrue(hasEdge(graph, EdgeType.PERFORMED_ON, sourceState, fill));
        assertTrue(hasEdge(graph, EdgeType.TRANSITIONS_TO, fill, sourceState),
                "Non-navigation actions must still be represented as state-preserving transitions");
        assertTrue(hasEdge(graph, EdgeType.NAVIGATES_TO,
                StableId.of("screen:/projects/new"), StableId.of("screen:/projects/{id}")));

        long submitHttp = graph.edges().stream().filter(edge -> edge.type() == EdgeType.CALLS_HTTP
                && edge.from().equals(submit)).count();
        assertEquals(2, submitHttp);
        assertFalse(graph.edges().stream().anyMatch(edge -> edge.type() == EdgeType.CALLS_HTTP
                && edge.from().equals(fill)),
                "HTTP calls must be associated with the action that triggered them");
        assertFalse(graph.edges().stream().anyMatch(edge -> edge.type() == EdgeType.CALLS_HTTP
                && edge.to().equals(StableId.of("client:GET:/api/bootstrap"))
                && !edge.from().equals(StableId.of("flow:project.create.success"))),
                "Initial page-load HTTP must remain flow-level instead of being attributed to an action");
        Edge post = graph.edges().stream().filter(edge -> edge.type() == EdgeType.CALLS_HTTP
                && edge.from().equals(submit)
                && edge.to().equals(StableId.of("client:POST:/api/projects"))).findFirst().orElseThrow();
        assertEquals(1, ((Number) post.attributes().get("actionSequence")).intValue());
        assertEquals(201, ((Number) post.attributes().get("status")).intValue());
    }

    private boolean hasEdge(DocumentationGraph graph, EdgeType type, StableId from, StableId to) {
        return graph.edges().stream().anyMatch(edge -> edge.type() == type
                && edge.from().equals(from) && edge.to().equals(to));
    }
}
