package io.github.mandala.sbdp.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Canonical JSON codec shared by the CLI, cache and renderers. */
public final class DocumentationGraphJson {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private DocumentationGraphJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER.copy();
    }

    public static DocumentationGraph read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return read(input);
        }
    }

    public static DocumentationGraph read(InputStream input) throws IOException {
        return MAPPER.readValue(input, DocumentationGraph.class);
    }

    public static DocumentationGraph fromJson(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, DocumentationGraph.class);
    }

    public static void write(Path path, DocumentationGraph graph) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                write(output, graph);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void write(OutputStream output, DocumentationGraph graph) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(output, graph);
    }

    public static String toJson(DocumentationGraph graph) throws JsonProcessingException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(graph);
    }
}
