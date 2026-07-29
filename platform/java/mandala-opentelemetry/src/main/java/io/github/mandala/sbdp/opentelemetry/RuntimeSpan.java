package io.github.mandala.sbdp.opentelemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RuntimeSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        RuntimeSpanKind kind,
        SpanBoundary boundary,
        Instant startTime,
        Instant endTime,
        RuntimeStatus status,
        Map<String, Object> attributes,
        Map<String, Object> resourceAttributes,
        String scopeName,
        String scopeVersion,
        List<RuntimeEvent> events,
        List<RuntimeLink> links) {
    public RuntimeSpan {
        traceId = Objects.requireNonNull(traceId, "traceId");
        spanId = Objects.requireNonNull(spanId, "spanId");
        parentSpanId = Objects.requireNonNullElse(parentSpanId, "");
        name = Objects.requireNonNullElse(name, "");
        kind = Objects.requireNonNull(kind, "kind");
        boundary = Objects.requireNonNull(boundary, "boundary");
        startTime = Objects.requireNonNull(startTime, "startTime");
        endTime = Objects.requireNonNull(endTime, "endTime");
        status = Objects.requireNonNull(status, "status");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        resourceAttributes = Map.copyOf(resourceAttributes == null ? Map.of() : resourceAttributes);
        scopeName = Objects.requireNonNullElse(scopeName, "");
        scopeVersion = Objects.requireNonNullElse(scopeVersion, "");
        events = List.copyOf(events == null ? List.of() : events);
        links = List.copyOf(links == null ? List.of() : links);
    }

    public Duration duration() {
        return endTime.isBefore(startTime) ? Duration.ZERO : Duration.between(startTime, endTime);
    }
}
