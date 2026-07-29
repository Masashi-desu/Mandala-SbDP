package io.github.mandala.sbdp.model;

import java.util.Map;
import java.util.Objects;

public record Node(
        StableId id,
        NodeType type,
        String displayName,
        String description,
        ElementMetadata metadata,
        Map<String, Object> attributes
) implements Comparable<Node> {
    public Node {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        displayName = Objects.requireNonNullElse(displayName, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
        metadata = metadata == null ? ElementMetadata.empty() : metadata;
        attributes = ModelValues.immutableMap(attributes);
        if (displayName.isBlank()) throw new IllegalArgumentException("Node display name must not be blank");
    }

    public static Node of(String id, NodeType type, String displayName) {
        return new Node(StableId.of(id), type, displayName, "", ElementMetadata.empty(), Map.of());
    }

    public static Builder builder(StableId id, NodeType type, String displayName) {
        return new Builder(id, type, displayName);
    }

    public Builder toBuilder() {
        return new Builder(id, type, displayName).description(description).metadata(metadata).attributes(attributes);
    }

    @Override
    public int compareTo(Node other) {
        return id.compareTo(other.id);
    }

    public static final class Builder {
        private final StableId id;
        private final NodeType type;
        private final String displayName;
        private String description = "";
        private ElementMetadata metadata = ElementMetadata.empty();
        private Map<String, ?> attributes = Map.of();

        private Builder(StableId id, NodeType type, String displayName) {
            this.id = id;
            this.type = type;
            this.displayName = displayName;
        }

        public Builder description(String value) { description = value; return this; }
        public Builder metadata(ElementMetadata value) { metadata = value; return this; }
        public Builder attributes(Map<String, ?> value) { attributes = value; return this; }

        public Node build() {
            @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) attributes;
            return new Node(id, type, displayName, description, metadata, values);
        }
    }
}
