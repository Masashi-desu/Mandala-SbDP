package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshPlannerTest {
    @Test
    void classifiesRequiredIncrementalInputs() {
        assertEquals(ChangeCategory.JAVA, ChangedFile.classify("src/main/java/A.java"));
        assertEquals(ChangeCategory.SQL, ChangedFile.classify("src/main/resources/META-INF/A/select.sql"));
        assertEquals(ChangeCategory.MIGRATION, ChangedFile.classify("src/main/resources/db/migration/V2__x.sql"));
        assertEquals(ChangeCategory.FRONTEND, ChangedFile.classify("frontend/src/App.tsx"));
        assertEquals(ChangeCategory.FIXTURE, ChangedFile.classify("frontend/fixtures/projects.json"));
        assertEquals(ChangeCategory.PLAYWRIGHT_SCENARIO, ChangedFile.classify("scenarios/create.yaml"));
        assertEquals(ChangeCategory.UI_CAPTURE,
                ChangedFile.classify("mandala/snapshots/ui/project-create.json"));
        assertEquals(ChangeCategory.RUNTIME_CAPTURE,
                ChangedFile.classify("mandala/traces/runtime/otlp.json"));
        assertEquals(ChangeCategory.DATABASE_CAPTURE,
                ChangedFile.classify("mandala/snapshots/db/schema.json"));
        assertEquals(ChangeCategory.SPRING_CAPTURE,
                ChangedFile.classify("mandala/snapshots/runtime/openapi.json"));
        assertEquals(ChangeCategory.SPRING_CAPTURE,
                ChangedFile.classify("mandala/snapshots/spring/mappings.json"));
        assertEquals(ChangeCategory.OPENAPI, ChangedFile.classify("api/openapi.yaml"));
        assertEquals(ChangeCategory.CUSTOM_HTML, ChangedFile.classify("mandala/custom/a/details.html"));
        assertEquals(ChangeCategory.CONFIGURATION, ChangedFile.classify("mandala.yml"));
        ChangeSet rename = new ChangeSet(List.of(ChangedFile.renamed("src/A.java", "frontend/A.ts")));
        assertEquals(Set.of(ChangeCategory.JAVA, ChangeCategory.FRONTEND), rename.categories());
    }

    @Test
    void fallsBackForConfigurationAndUnsupportedAffectedAdapter() {
        GraphAdapter adapter = adapter(false);
        DocumentationGraph previous = DocumentationGraph.empty("p");
        RefreshRequest config = new RefreshRequest("p", "c", "cfg", Path.of("."), RefreshMode.INCREMENTAL,
                ChangeSet.ofPaths(List.of("mandala.yml")), true, previous, Map.of());
        RefreshRequest java = new RefreshRequest("p", "c", "cfg", Path.of("."), RefreshMode.INCREMENTAL,
                ChangeSet.ofPaths(List.of("src/A.java")), true, previous, Map.of());

        RefreshPlan configPlan = new RefreshPlanner().plan(config, List.of(adapter));
        RefreshPlan javaPlan = new RefreshPlanner().plan(java, List.of(adapter));

        assertTrue(configPlan.fallback());
        assertEquals(RefreshMode.FULL, configPlan.executionMode());
        assertTrue(javaPlan.fallback());
    }

    @Test
    void limitsSafeIncrementalRunToAffectedAdapters() {
        GraphAdapter java = adapter(true);
        GraphAdapter sql = new GraphAdapter() {
            public String name() { return "sql"; }
            public String version() { return "1"; }
            public DocumentationGraph analyze(RefreshContext context) { return DocumentationGraph.empty(context.projectId()); }
            public Set<ChangeCategory> changeCategories() { return Set.of(ChangeCategory.SQL); }
            public boolean supportsIncremental() { return true; }
        };
        RefreshRequest request = new RefreshRequest("p", "c", "cfg", Path.of("."), RefreshMode.INCREMENTAL,
                ChangeSet.ofPaths(List.of("src/A.java")), true, DocumentationGraph.empty("p"), Map.of());

        RefreshPlan plan = new RefreshPlanner().plan(request, List.of(java, sql));

        assertFalse(plan.fallback());
        assertEquals(Set.of("java"), plan.affectedAdapters());
    }

    @Test
    void rejectsUnsafePathsAndFallsBackWhenNoAdapterCoversAKnownCategory() {
        assertThrows(IllegalArgumentException.class, () -> ChangedFile.of("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> ChangedFile.of("a/../../secret"));
        assertThrows(IllegalArgumentException.class, () -> ChangedFile.of("C:/Users/secret"));

        RefreshRequest request = new RefreshRequest("p", "c", "cfg", Path.of("."), RefreshMode.INCREMENTAL,
                ChangeSet.ofPaths(List.of("frontend/App.tsx")), true, DocumentationGraph.empty("p"), Map.of());
        RefreshPlan plan = new RefreshPlanner().plan(request, List.of(adapter(true)));

        assertTrue(plan.fallback());
        assertTrue(plan.reasons().stream().anyMatch(reason -> reason.contains("FRONTEND")));
    }

    @Test
    void fallsBackWhenGitStateCannotProvideATrustworthyIncrementalBaseline() {
        ChangedFile unsafe = new ChangedFile("mandala/.refresh/unsafe-git-state", "",
                FileChangeType.MODIFIED, ChangeCategory.UNSAFE_GIT_STATE);
        RefreshRequest request = new RefreshRequest("p", "c", "cfg", Path.of("."), RefreshMode.INCREMENTAL,
                new ChangeSet(List.of(unsafe)), true, DocumentationGraph.empty("p"), Map.of());

        RefreshPlan plan = new RefreshPlanner().plan(request, List.of(adapter(true)));

        assertTrue(plan.fallback());
        assertEquals(RefreshMode.FULL, plan.executionMode());
        assertTrue(plan.reasons().stream().anyMatch(reason -> reason.contains("Git diff")));
    }

    private GraphAdapter adapter(boolean incremental) {
        return new GraphAdapter() {
            public String name() { return "java"; }
            public String version() { return "1"; }
            public DocumentationGraph analyze(RefreshContext context) { return DocumentationGraph.empty(context.projectId()); }
            public Set<ChangeCategory> changeCategories() { return Set.of(ChangeCategory.JAVA); }
            public boolean supportsIncremental() { return incremental; }
        };
    }
}
