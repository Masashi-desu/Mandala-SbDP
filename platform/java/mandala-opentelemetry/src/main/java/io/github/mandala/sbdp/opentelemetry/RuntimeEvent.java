package io.github.mandala.sbdp.opentelemetry;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RuntimeEvent(String name, Instant time, Map<String, Object> attributes) {
    public RuntimeEvent {
        name = Objects.requireNonNullElse(name, "");
        time = Objects.requireNonNull(time, "time");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
