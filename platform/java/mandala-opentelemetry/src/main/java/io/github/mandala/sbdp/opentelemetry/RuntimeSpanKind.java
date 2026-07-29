package io.github.mandala.sbdp.opentelemetry;

public enum RuntimeSpanKind {
    INTERNAL,
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER,
    UNSPECIFIED
}
