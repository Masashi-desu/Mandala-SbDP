package io.github.mandala.sbdp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Content-verified, atomic filesystem cache safe for interrupted refreshes. */
public final class FileSystemCache {
    public static final String GRAPH_CODEC_VERSION = "1";
    private final Path root;
    private final Clock clock;
    private final ObjectMapper mapper = DocumentationGraphJson.mapper();

    public FileSystemCache(Path root) {
        this(root, Clock.systemUTC());
    }

    public FileSystemCache(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
    }

    public synchronized CacheMetadata put(CacheDescriptor descriptor, byte[] content, String targetCommit,
                                          String configurationHash, String adapterName, String adapterVersion)
            throws IOException {
        java.util.Objects.requireNonNull(descriptor, "descriptor");
        java.util.Objects.requireNonNull(content, "content");
        CacheMetadata metadata = new CacheMetadata(descriptor.kind(), descriptor.projectId(), descriptor.name(),
                targetCommit, configurationHash, adapterName, adapterVersion, Instant.now(clock), sha256(content));
        Path directory = directory(descriptor);
        Files.createDirectories(directory);
        String suffix = ".tmp-" + UUID.randomUUID();
        Path payloadTemp = directory.resolve("payload.bin" + suffix);
        Path metadataTemp = directory.resolve("metadata.json" + suffix);
        Files.write(payloadTemp, content);
        mapper.writerWithDefaultPrettyPrinter().writeValue(metadataTemp.toFile(), metadata);
        atomicMove(payloadTemp, directory.resolve("payload.bin"));
        atomicMove(metadataTemp, directory.resolve("metadata.json"));
        return metadata;
    }

    public synchronized Optional<CachedValue> get(CacheDescriptor descriptor, CacheRequirements requirements) {
        Path directory = directory(descriptor);
        Path metadataPath = directory.resolve("metadata.json");
        Path payloadPath = directory.resolve("payload.bin");
        if (!Files.isRegularFile(metadataPath) || !Files.isRegularFile(payloadPath)) return Optional.empty();
        try {
            CacheMetadata metadata = mapper.readValue(metadataPath.toFile(), CacheMetadata.class);
            if (metadata.kind() != descriptor.kind() || !metadata.projectId().equals(descriptor.projectId())
                    || !metadata.name().equals(descriptor.name()) || !requirements.accepts(metadata)) {
                return Optional.empty();
            }
            byte[] content = Files.readAllBytes(payloadPath);
            if (!sha256(content).equals(metadata.contentSha256())) return Optional.empty();
            return Optional.of(new CachedValue(metadata, content));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public CacheMetadata putGraph(CacheDescriptor descriptor, DocumentationGraph graph, String targetCommit,
                                  String configurationHash, String adapterName, String adapterVersion) throws IOException {
        java.util.Objects.requireNonNull(graph, "graph");
        String normalizedCommit = targetCommit == null ? "" : targetCommit.strip();
        if (!descriptor.projectId().equals(graph.projectId())) {
            throw new IllegalArgumentException("Cache descriptor project " + descriptor.projectId()
                    + " does not match graph project " + graph.projectId());
        }
        if (!normalizedCommit.equals(graph.targetCommit())) {
            throw new IllegalArgumentException("Cache target commit " + normalizedCommit
                    + " does not match graph target commit " + graph.targetCommit());
        }
        return put(descriptor, DocumentationGraphJson.toJson(graph).getBytes(StandardCharsets.UTF_8), targetCommit,
                configurationHash, adapterName, adapterVersion);
    }

    public Optional<DocumentationGraph> getGraph(CacheDescriptor descriptor, CacheRequirements requirements) {
        return get(descriptor, requirements).flatMap(value -> {
            try {
                DocumentationGraph graph = DocumentationGraphJson.fromJson(
                        new String(value.content(), StandardCharsets.UTF_8));
                if (!graph.projectId().equals(descriptor.projectId())
                        || !graph.targetCommit().equals(value.metadata().targetCommit())) return Optional.empty();
                return Optional.of(graph);
            } catch (IOException | RuntimeException ignored) {
                return Optional.empty();
            }
        });
    }

    private Path directory(CacheDescriptor descriptor) {
        String key = descriptor.projectId() + "\u0000" + descriptor.name();
        return root.resolve(descriptor.kind().name().toLowerCase()).resolve(StableIdGenerator.digest(key));
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
