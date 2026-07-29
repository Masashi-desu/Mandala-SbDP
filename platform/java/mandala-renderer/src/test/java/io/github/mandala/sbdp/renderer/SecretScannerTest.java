package io.github.mandala.sbdp.renderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretScannerTest {
    @TempDir Path repository;

    @Test
    void rejectsSecretsAndMachineSpecificValuesFromPublishableArtifacts() throws Exception {
        Path output = repository.resolve("generated");
        Files.createDirectories(output);
        Files.writeString(output.resolve("secret.json"), "{\"password\": \"plain-text\"}");
        Files.writeString(output.resolve("local.html"), "<code>/Users/alice/work/app</code>");

        List<String> findings = new SecretScanner().scanPortable(output);

        assertEquals(2, findings.size(), findings.toString());
    }

    @Test
    void honorsRepositoryRelativeExclusionsForInputCaches() throws Exception {
        Path snapshots = repository.resolve("mandala/snapshots");
        Path dependency = snapshots.resolve("node_modules/tool/cache.json");
        Files.createDirectories(dependency.getParent());
        Files.writeString(dependency, "{\"password\": \"dependency-value\"}");

        List<String> findings = new SecretScanner().scan(snapshots, repository,
                List.of("**/node_modules/**"), true);

        assertFalse(findings.stream().findAny().isPresent(), findings.toString());
    }

    @Test
    void scansLargeGraphArtifactsWithoutSilentlySkippingThem() throws Exception {
        Path graph = repository.resolve("mandala.json");
        Files.writeString(graph, "x".repeat(5_100_000) + "\n{\"password\":\"large-file-secret\"}");

        List<String> findings = new SecretScanner().scanPortable(graph);

        assertTrue(findings.stream().anyMatch(item -> item.contains("password")), findings.toString());
    }
}
