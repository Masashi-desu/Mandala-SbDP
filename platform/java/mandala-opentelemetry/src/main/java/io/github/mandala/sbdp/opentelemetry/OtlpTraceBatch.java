package io.github.mandala.sbdp.opentelemetry;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record OtlpTraceBatch(Instant importedAt, List<RuntimeTrace> traces, List<String> warnings) {
    public OtlpTraceBatch {
        importedAt = Objects.requireNonNull(importedAt, "importedAt");
        traces = List.copyOf(traces == null ? List.of() : traces);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
