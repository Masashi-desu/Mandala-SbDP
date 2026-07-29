package io.github.mandala.sbdp.opentelemetry;

public enum SpanBoundary {
    HTTP_SERVER,
    CONTROLLER,
    APPLICATION_SERVICE,
    USE_CASE,
    DOMA_DAO,
    JDBC,
    R2DBC,
    EXTERNAL_HTTP_CLIENT,
    ASYNC,
    MESSAGE,
    OTHER
}
