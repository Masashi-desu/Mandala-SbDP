FROM otel/opentelemetry-collector-contrib:0.128.0

COPY otel/collector.yaml /mandala-config.yaml
