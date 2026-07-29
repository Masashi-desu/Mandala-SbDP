package io.github.mandala.sbdp.opentelemetry;

import java.util.Map;
import java.util.Objects;

public record RuntimeLink(String traceId, String spanId, String traceState, Map<String, Object> attributes) {
    public RuntimeLink {
        traceId = Objects.requireNonNullElse(traceId, "");
        spanId = Objects.requireNonNullElse(spanId, "");
        traceState = Objects.requireNonNullElse(traceState, "");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
