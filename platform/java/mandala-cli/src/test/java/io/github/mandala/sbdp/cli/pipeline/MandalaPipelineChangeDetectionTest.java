package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.cli.RepositoryContext;
import io.github.mandala.sbdp.cli.config.MandalaConfig;
import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.ChangeSet;
import io.github.mandala.sbdp.core.ChangedFile;
import io.github.mandala.sbdp.core.FileChangeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MandalaPipelineChangeDetectionTest {
    @TempDir Path root;

    @Test
    void externalCaptureInvalidatesEveryLiveInputAdapterEvenWhenGitDiffIsEmpty() {
        ChangeSet changes = MandalaPipeline.detectChanges(true, "", true, "", true);

        assertEquals(Set.of(ChangeCategory.UI_CAPTURE, ChangeCategory.RUNTIME_CAPTURE,
                ChangeCategory.DATABASE_CAPTURE, ChangeCategory.SPRING_CAPTURE), changes.categories());

        RepositoryContext repository = repository();
        assertTrue(new UiGraphAdapter(repository).affectedBy(changes));
        assertTrue(new RuntimeGraphAdapter(repository).affectedBy(changes));
        assertTrue(new PostgresGraphAdapter(repository, true).affectedBy(changes));
        assertTrue(new SourceGraphAdapter(repository).affectedBy(changes));
        assertTrue(new ConnectionGraphAdapter(repository).affectedBy(changes));
        assertFalse(new CustomGraphAdapter(repository).affectedBy(changes));
    }

    @Test
    void captureCategoriesOnlyInvalidateTheirAuthoritativeAdapterAndConnections() {
        RepositoryContext repository = repository();
        ChangeSet runtime = changes(ChangeCategory.RUNTIME_CAPTURE);
        ChangeSet ui = changes(ChangeCategory.UI_CAPTURE);
        ChangeSet database = changes(ChangeCategory.DATABASE_CAPTURE);
        ChangeSet spring = changes(ChangeCategory.SPRING_CAPTURE);

        assertTrue(new RuntimeGraphAdapter(repository).affectedBy(runtime));
        assertFalse(new UiGraphAdapter(repository).affectedBy(runtime));
        assertTrue(new UiGraphAdapter(repository).affectedBy(ui));
        assertFalse(new RuntimeGraphAdapter(repository).affectedBy(ui));
        assertTrue(new PostgresGraphAdapter(repository, false).affectedBy(database));
        assertFalse(new SourceGraphAdapter(repository).affectedBy(database));
        assertTrue(new SourceGraphAdapter(repository).affectedBy(spring));
        assertFalse(new PostgresGraphAdapter(repository, false).affectedBy(spring));
        assertTrue(new ConnectionGraphAdapter(repository).affectedBy(runtime));
    }

    @Test
    void failedDiffOrUntrackedFilesInvalidateTheIncrementalBaseline() {
        ChangeSet failedDiff = MandalaPipeline.detectChanges(false, "fatal: bad revision", true, "", false);
        ChangeSet failedUntrackedQuery = MandalaPipeline.detectChanges(true, "", false, "fatal", false);
        ChangeSet untracked = MandalaPipeline.detectChanges(true, "", true, "src/New.java", false);

        assertTrue(failedDiff.categories().contains(ChangeCategory.UNSAFE_GIT_STATE));
        assertTrue(failedUntrackedQuery.categories().contains(ChangeCategory.UNSAFE_GIT_STATE));
        assertTrue(untracked.categories().contains(ChangeCategory.UNSAFE_GIT_STATE));
    }

    @Test
    void parsesTrackedModificationsAndRenamesWithoutLosingEitherCategory() {
        ChangeSet changes = MandalaPipeline.detectChanges(true,
                "M\tsrc/main/java/example/Project.java\n"
                        + "R100\tsrc/main/java/example/Old.java\tfrontend/src/New.ts",
                true, "", false);

        assertEquals(Set.of(ChangeCategory.JAVA, ChangeCategory.FRONTEND), changes.categories());
        assertEquals(2, changes.files().size());
    }

    private ChangeSet changes(ChangeCategory category) {
        return new ChangeSet(List.of(new ChangedFile("mandala/.refresh/" + category.name().toLowerCase(), "",
                FileChangeType.MODIFIED, category)));
    }

    private RepositoryContext repository() {
        return new RepositoryContext(root, root.resolve("mandala.yml"), new MandalaConfig(),
                "commit", Instant.EPOCH);
    }
}
