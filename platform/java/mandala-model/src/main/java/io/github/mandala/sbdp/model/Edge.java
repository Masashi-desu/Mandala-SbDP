package io.github.mandala.sbdp.model;

import java.util.Map;
import java.util.Objects;

public record Edge(
        StableId id,
        EdgeType type,
        StableId from,
        StableId to,
        String description,
        ElementMetadata metadata,
        Map<String, Object> attributes
) implements Comparable<Edge> {
    public Edge {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        description = Objects.requireNonNullElse(description, "").strip();
        metadata = metadata == null ? ElementMetadata.empty() : metadata;
        attributes = ModelValues.immutableMap(attributes);
    }

    public static Edge of(String id, EdgeType type, StableId from, StableId to) {
        return new Edge(StableId.of(id), type, from, to, "", ElementMetadata.empty(), Map.of());
    }

    public static Builder builder(StableId id, EdgeType type, StableId from, StableId to) {
        return new Builder(id, type, from, to);
    }

    public Builder toBuilder() {
        return new Builder(id, type, from, to).description(description).metadata(metadata).attributes(attributes);
    }

    @Override
    public int compareTo(Edge other) {
        return id.compareTo(other.id);
    }

    public static final class Builder {
        private final StableId id;
        private final EdgeType type;
        private final StableId from;
        private final StableId to;
        private String description = "";
        private ElementMetadata metadata = ElementMetadata.empty();
        private Map<String, ?> attributes = Map.of();

        private Builder(StableId id, EdgeType type, StableId from, StableId to) {
            this.id = id;
            this.type = type;
            this.from = from;
            this.to = to;
        }

        public Builder description(String value) { description = value; return this; }
        public Builder metadata(ElementMetadata value) { metadata = value; return this; }
        public Builder attributes(Map<String, ?> value) { attributes = value; return this; }

        public Edge build() {
            @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) attributes;
            return new Edge(id, type, from, to, description, metadata, values);
        }
    }
}
