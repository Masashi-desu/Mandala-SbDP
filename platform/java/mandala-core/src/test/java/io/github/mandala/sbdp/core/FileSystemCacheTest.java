package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemCacheTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesMetadataAndValidatesCommitConfigAndAdapterVersion() throws Exception {
        FileSystemCache cache = cache();
        CacheDescriptor descriptor = new CacheDescriptor(CacheKind.OPENAPI, "sample", "resolved");
        byte[] content = "{\"openapi\":\"3.1.0\"}".getBytes(StandardCharsets.UTF_8);

        CacheMetadata metadata = cache.put(descriptor, content, "commit-1", "config-1", "spring", "2.0");

        assertEquals("commit-1", metadata.targetCommit());
        assertArrayEquals(content, cache.get(descriptor,
                new CacheRequirements("commit-1", "config-1", "spring", "2.0")).orElseThrow().content());
        assertFalse(cache.get(descriptor,
                new CacheRequirements("commit-2", "config-1", "spring", "2.0")).isPresent());
        assertFalse(cache.get(descriptor,
                new CacheRequirements("commit-1", "other", "spring", "2.0")).isPresent());
        assertFalse(cache.get(descriptor,
                new CacheRequirements("commit-1", "config-1", "spring", "3.0")).isPresent());
    }

    @Test
    void roundTripsGraphAndRejectsCorruptPayload() throws Exception {
        FileSystemCache cache = cache();
        CacheDescriptor descriptor = new CacheDescriptor(CacheKind.DOCUMENTATION_GRAPH, "sample", "latest");
        DocumentationGraph graph = DocumentationGraph.of("sample", "c", Instant.EPOCH,
                List.of(Node.of("table:public.projects", NodeType.DB_TABLE, "projects")), List.of());
        cache.putGraph(descriptor, graph, "c", "cfg", "core", "1");
        CacheRequirements requirements = new CacheRequirements("c", "cfg", "core", "1");

        assertEquals(graph, cache.getGraph(descriptor, requirements).orElseThrow());
        Path payload = Files.walk(temporaryDirectory).filter(path -> path.getFileName().toString().equals("payload.bin"))
                .findFirst().orElseThrow();
        Files.writeString(payload, "corrupt");
        assertTrue(cache.getGraph(descriptor, requirements).isEmpty());
    }

    @Test
    void refusesToStoreGraphUnderMismatchedProjectOrCommitMetadata() {
        FileSystemCache cache = cache();
        DocumentationGraph graph = DocumentationGraph.of("sample", "c1", Instant.EPOCH, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> cache.putGraph(
                new CacheDescriptor(CacheKind.DOCUMENTATION_GRAPH, "other", "latest"), graph,
                "c1", "cfg", "core", FileSystemCache.GRAPH_CODEC_VERSION));
        assertThrows(IllegalArgumentException.class, () -> cache.putGraph(
                new CacheDescriptor(CacheKind.DOCUMENTATION_GRAPH, "sample", "latest"), graph,
                "c2", "cfg", "core", FileSystemCache.GRAPH_CODEC_VERSION));
    }

    private FileSystemCache cache() {
        return new FileSystemCache(temporaryDirectory,
                Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
    }
}
