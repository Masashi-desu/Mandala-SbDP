package io.github.mandala.sbdp.opentelemetry;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record RuntimeTrace(String traceId, List<RuntimeSpan> spans) {
    public RuntimeTrace {
        traceId = Objects.requireNonNull(traceId, "traceId");
        spans = List.copyOf(spans == null ? List.of() : spans);
    }

    public List<RuntimeSpan> rootSpans() {
        Set<String> spanIds = spans.stream().map(RuntimeSpan::spanId).collect(Collectors.toSet());
        return spans.stream()
                .filter(span -> span.parentSpanId().isBlank() || !spanIds.contains(span.parentSpanId()))
                .toList();
    }
}
