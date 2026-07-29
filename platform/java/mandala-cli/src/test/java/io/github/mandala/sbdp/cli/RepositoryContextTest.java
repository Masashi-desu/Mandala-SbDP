package io.github.mandala.sbdp.cli;

import io.github.mandala.sbdp.cli.config.MandalaConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryContextTest {
    @TempDir Path root;

    @Test
    void resolvesOnlyRepositoryContainedPaths() {
        RepositoryContext context = new RepositoryContext(root, root.resolve("mandala.yml"),
                new MandalaConfig(), "commit", Instant.EPOCH);

        assertEquals(root.resolve("mandala/generated/site").normalize(), context.resolve("mandala/generated/site"));
        assertThrows(IllegalArgumentException.class, () -> context.resolve("../outside"));
        assertThrows(IllegalArgumentException.class, () -> context.resolve(root.resolveSibling("outside").toString()));
    }
}
