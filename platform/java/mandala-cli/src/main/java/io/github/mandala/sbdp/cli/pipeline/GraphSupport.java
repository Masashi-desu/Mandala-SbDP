package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.core.StableIdGenerator;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.EvidenceType;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.ReviewState;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class GraphSupport {
    static final StableIdGenerator IDS = new StableIdGenerator();
    private GraphSupport() {}

    static ElementMetadata metadata(EvidenceType type, String source, String description, String adapter,
                                    String commit, Instant time, Collection<String> warnings,
                                    Collection<String> scenarios, SourceLocation... locations) {
        Evidence evidence = Evidence.of(type, source, description);
        return ElementMetadata.builder().evidence(List.of(evidence)).sourceLocations(List.of(locations))
                .targetCommit(commit).analyzedAt(time).adapter(adapter).confidence(evidence.confidence())
                .reviewState(ReviewState.UNREVIEWED).warnings(warnings == null ? List.of() : warnings)
                .relatedScenarios(scenarios == null ? Set.of() : Set.copyOf(scenarios)).build();
    }

    static Edge edge(EdgeType type, StableId from, StableId to, ElementMetadata metadata) {
        return Edge.builder(IDS.edge(type, from, to), type, from, to).metadata(metadata).build();
    }

    static Edge edge(EdgeType type, StableId from, StableId to, ElementMetadata metadata, Map<String, ?> attributes) {
        return Edge.builder(IDS.edge(type, from, to), type, from, to).metadata(metadata)
                .attributes((Map<String, ?>) serializable(attributes)).build();
    }

    static DocumentationGraph graph(String project, String commit, Instant time, Collection<Node> nodes, Collection<Edge> edges) {
        return DocumentationGraph.of(project, commit, time, new ArrayList<>(nodes), distinctEdges(edges));
    }

    static List<Edge> distinctEdges(Collection<Edge> edges) {
        Map<String, Edge> unique = new LinkedHashMap<>();
        for (Edge edge : edges) unique.putIfAbsent(edge.type() + "\u0000" + edge.from() + "\u0000" + edge.to(), edge);
        return List.copyOf(unique.values());
    }

    static String fingerprint(Object... values) {
        try {
            Object[] normalized = java.util.Arrays.stream(values).map(GraphSupport::serializable).toArray();
            String material = java.util.Arrays.deepToString(normalized);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static Map<String, Object> attributes(Map<String, ?> source, Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> values.put(key, serializable(value)));
        for (int index = 0; index + 1 < pairs.length; index += 2) values.put(String.valueOf(pairs[index]), serializable(pairs[index + 1]));
        return Collections.unmodifiableMap(values);
    }

    static Object serializable(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof StableId) return value;
        if (value instanceof java.nio.file.Path path) return path.toString().replace('\\', '/');
        if (value instanceof java.time.temporal.TemporalAccessor temporal) return temporal.toString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new TreeMap<>();
            map.forEach((key, item) -> converted.put(String.valueOf(key), serializable(item)));
            return converted;
        }
        if (value instanceof Set<?> set) return set.stream().map(GraphSupport::serializable)
                .sorted(Comparator.comparing(GraphSupport::sortKey)).toList();
        if (value instanceof Collection<?> collection) return collection.stream().map(GraphSupport::serializable).toList();
        if (value.getClass().isArray()) { int size = java.lang.reflect.Array.getLength(value); List<Object> items = new ArrayList<>(); for (int i = 0; i < size; i++) items.add(serializable(java.lang.reflect.Array.get(value, i))); return items; }
        if (value.getClass().isRecord()) {
            Map<String, Object> record = new LinkedHashMap<>();
            for (var component : value.getClass().getRecordComponents()) try { record.put(component.getName(), serializable(component.getAccessor().invoke(value))); } catch (ReflectiveOperationException error) { throw new IllegalArgumentException("Cannot serialize record attribute " + value.getClass(), error); }
            return record;
        }
        return String.valueOf(value);
    }

    private static String sortKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + sortKey(entry.getValue()))
                    .reduce((left, right) -> left + "\u0000" + right).orElse("");
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(GraphSupport::sortKey)
                    .reduce((left, right) -> left + "\u0000" + right).orElse("");
        }
        return String.valueOf(value);
    }

    static String simpleName(String qualified) {
        int separator = qualified.lastIndexOf('.'); return separator < 0 ? qualified : qualified.substring(separator + 1);
    }
}
