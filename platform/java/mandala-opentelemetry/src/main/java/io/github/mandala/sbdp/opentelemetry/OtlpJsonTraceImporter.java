package io.github.mandala.sbdp.opentelemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Imports OTLP protobuf-JSON export payloads (including gzip files) into normalized runtime traces. */
public final class OtlpJsonTraceImporter {
    private final ObjectMapper mapper;
    private final SensitiveDataMasker masker;
    private final Clock clock;

    public OtlpJsonTraceImporter() {
        this(new SensitiveDataMasker(), Clock.systemUTC());
    }

    public OtlpJsonTraceImporter(SensitiveDataMasker masker, Clock clock) {
        this.mapper = new ObjectMapper();
        this.masker = Objects.requireNonNull(masker, "masker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtlpTraceBatch importFile(Path path) throws IOException {
        try (InputStream file = Files.newInputStream(path);
                InputStream input = decompressIfNeeded(file)) {
            return importJson(mapper.readTree(input));
        }
    }

    public OtlpTraceBatch importStream(InputStream input) throws IOException {
        return importJson(mapper.readTree(input));
    }

    public OtlpTraceBatch importJson(JsonNode root) {
        List<String> warnings = new ArrayList<>();
        List<JsonNode> resourceSpans = new ArrayList<>();
        collectResourceSpans(root, resourceSpans);
        if (resourceSpans.isEmpty()) {
            warnings.add("OTLP payload contains no resourceSpans");
        }

        Map<String, Map<String, RuntimeSpan>> traces = new LinkedHashMap<>();
        for (JsonNode resourceSpan : resourceSpans) {
            Map<String, Object> resourceAttributes = attributes(field(resourceSpan.path("resource"), "attributes"));
            JsonNode scopeSpans = field(resourceSpan, "scopeSpans", "scope_spans", "instrumentationLibrarySpans");
            for (JsonNode scopeSpan : elements(scopeSpans)) {
                JsonNode scope = field(scopeSpan, "scope", "instrumentationLibrary");
                String scopeName = text(scope, "name");
                String scopeVersion = text(scope, "version");
                for (JsonNode spanNode : elements(field(scopeSpan, "spans"))) {
                    RuntimeSpan span = span(spanNode, resourceAttributes, scopeName, scopeVersion, warnings);
                    if (span == null) {
                        continue;
                    }
                    Map<String, RuntimeSpan> trace = traces.computeIfAbsent(span.traceId(), ignored -> new LinkedHashMap<>());
                    if (trace.putIfAbsent(span.spanId(), span) != null) {
                        warnings.add("Duplicate span id " + span.spanId() + " in trace " + span.traceId());
                    }
                }
            }
        }
        List<RuntimeTrace> normalized = traces.entrySet().stream().map(entry -> new RuntimeTrace(
                        entry.getKey(),
                        entry.getValue().values().stream()
                                .sorted(Comparator.comparing(RuntimeSpan::startTime).thenComparing(RuntimeSpan::spanId))
                                .toList()))
                .sorted(Comparator.comparing(RuntimeTrace::traceId))
                .toList();
        return new OtlpTraceBatch(Instant.now(clock), normalized, warnings.stream().distinct().toList());
    }

    private RuntimeSpan span(
            JsonNode node,
            Map<String, Object> resourceAttributes,
            String scopeName,
            String scopeVersion,
            List<String> warnings) {
        String traceId = identifier(text(node, "traceId", "trace_id"));
        String spanId = identifier(text(node, "spanId", "span_id"));
        if (traceId.isBlank() || spanId.isBlank()) {
            warnings.add("Skipped OTLP span without traceId/spanId: " + text(node, "name"));
            return null;
        }
        Map<String, Object> attributes = attributes(field(node, "attributes"));
        String spanName = text(node, "name");
        if (has(attributes, "db.system", "db.system.name", "db.query.text", "db.statement")) {
            spanName = String.valueOf(masker.maskValue("db.statement", spanName));
        }
        Instant start = instant(text(node, "startTimeUnixNano", "start_time_unix_nano"), warnings, spanId);
        String endValue = text(node, "endTimeUnixNano", "end_time_unix_nano");
        Instant end = endValue.isBlank() ? start : instant(endValue, warnings, spanId);
        RuntimeSpanKind kind = kind(field(node, "kind"));
        List<RuntimeEvent> events = elements(field(node, "events")).stream()
                .map(event -> new RuntimeEvent(
                        text(event, "name"),
                        instant(text(event, "timeUnixNano", "time_unix_nano"), warnings, spanId),
                        attributes(field(event, "attributes"))))
                .toList();
        List<RuntimeLink> links = elements(field(node, "links")).stream()
                .map(link -> new RuntimeLink(
                        identifier(text(link, "traceId", "trace_id")),
                        identifier(text(link, "spanId", "span_id")),
                        text(link, "traceState", "trace_state"),
                        attributes(field(link, "attributes"))))
                .toList();
        RuntimeStatus status = status(field(node, "status"));
        return new RuntimeSpan(
                traceId,
                spanId,
                identifier(text(node, "parentSpanId", "parent_span_id")),
                spanName,
                kind,
                boundary(kind, attributes, spanName),
                start,
                end,
                status,
                attributes,
                resourceAttributes,
                scopeName,
                scopeVersion,
                events,
                links);
    }

    private Map<String, Object> attributes(JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node.isArray()) {
            for (JsonNode attribute : node) {
                String key = text(attribute, "key");
                if (!key.isBlank()) {
                    result.put(key, masker.maskValue(key, anyValue(field(attribute, "value"))));
                }
            }
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry -> result.put(
                    entry.getKey(), masker.maskValue(entry.getKey(), anyValue(entry.getValue()))));
        }
        return Map.copyOf(result);
    }

    private Object anyValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.has("stringValue") || node.has("string_value")) {
            return text(node, "stringValue", "string_value");
        }
        if (node.has("boolValue") || node.has("bool_value")) {
            return field(node, "boolValue", "bool_value").asBoolean();
        }
        if (node.has("intValue") || node.has("int_value")) {
            String value = text(node, "intValue", "int_value");
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        if (node.has("doubleValue") || node.has("double_value")) {
            return field(node, "doubleValue", "double_value").asDouble();
        }
        if (node.has("bytesValue") || node.has("bytes_value")) {
            return "[BINARY_REDACTED]";
        }
        JsonNode array = field(node, "arrayValue", "array_value");
        if (!array.isMissingNode()) {
            return elements(field(array, "values")).stream().map(this::anyValue).toList();
        }
        JsonNode keyValues = field(node, "kvlistValue", "kvlist_value");
        if (!keyValues.isMissingNode()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (JsonNode item : elements(field(keyValues, "values"))) {
                String key = text(item, "key");
                result.put(key, masker.maskValue(key, anyValue(field(item, "value"))));
            }
            return Map.copyOf(result);
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node.toString();
    }

    private SpanBoundary boundary(RuntimeSpanKind kind, Map<String, Object> attributes, String name) {
        String layer = value(attributes, "mandala.layer").toLowerCase(Locale.ROOT);
        if (!layer.isBlank()) {
            return switch (layer) {
                case "controller" -> SpanBoundary.CONTROLLER;
                case "service", "application_service", "application-service" -> SpanBoundary.APPLICATION_SERVICE;
                case "usecase", "use_case", "use-case" -> SpanBoundary.USE_CASE;
                case "dao", "doma", "doma_dao", "doma-dao" -> SpanBoundary.DOMA_DAO;
                case "jdbc", "database" -> SpanBoundary.JDBC;
                case "r2dbc" -> SpanBoundary.R2DBC;
                default -> SpanBoundary.OTHER;
            };
        }
        if (has(attributes, "db.system", "db.system.name", "db.namespace", "db.query.text", "db.statement")) {
            String namespace = value(attributes, "code.namespace").toLowerCase(Locale.ROOT);
            return namespace.contains("r2dbc") ? SpanBoundary.R2DBC : SpanBoundary.JDBC;
        }
        if (has(attributes, "messaging.system", "messaging.operation", "messaging.destination.name")) {
            return SpanBoundary.MESSAGE;
        }
        if (kind == RuntimeSpanKind.SERVER
                && has(attributes, "http.route", "http.request.method", "http.method", "url.path")) {
            return SpanBoundary.HTTP_SERVER;
        }
        if (kind == RuntimeSpanKind.CLIENT
                && has(attributes, "http.request.method", "http.method", "server.address", "url.full")) {
            return SpanBoundary.EXTERNAL_HTTP_CLIENT;
        }
        if (kind == RuntimeSpanKind.PRODUCER || kind == RuntimeSpanKind.CONSUMER) {
            return SpanBoundary.ASYNC;
        }
        String namespace = value(attributes, "code.namespace");
        String symbol = (namespace + " " + name).toLowerCase(Locale.ROOT);
        if (symbol.contains("controller")) {
            return SpanBoundary.CONTROLLER;
        }
        if (symbol.contains("dao")) {
            return SpanBoundary.DOMA_DAO;
        }
        if (symbol.contains("service") || symbol.contains("usecase")) {
            return SpanBoundary.APPLICATION_SERVICE;
        }
        return SpanBoundary.OTHER;
    }

    private RuntimeSpanKind kind(JsonNode node) {
        if (node.isNumber()) {
            return switch (node.asInt()) {
                case 1 -> RuntimeSpanKind.INTERNAL;
                case 2 -> RuntimeSpanKind.SERVER;
                case 3 -> RuntimeSpanKind.CLIENT;
                case 4 -> RuntimeSpanKind.PRODUCER;
                case 5 -> RuntimeSpanKind.CONSUMER;
                default -> RuntimeSpanKind.UNSPECIFIED;
            };
        }
        String value = node.asText("").toUpperCase(Locale.ROOT).replace("SPAN_KIND_", "");
        try {
            return RuntimeSpanKind.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return RuntimeSpanKind.UNSPECIFIED;
        }
    }

    private RuntimeStatus status(JsonNode node) {
        JsonNode codeNode = field(node, "code");
        RuntimeStatus.Code code;
        if (codeNode.isNumber()) {
            code = switch (codeNode.asInt()) {
                case 1 -> RuntimeStatus.Code.OK;
                case 2 -> RuntimeStatus.Code.ERROR;
                default -> RuntimeStatus.Code.UNSET;
            };
        } else {
            String value = codeNode.asText("").toUpperCase(Locale.ROOT).replace("STATUS_CODE_", "");
            try {
                code = RuntimeStatus.Code.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                code = RuntimeStatus.Code.UNSET;
            }
        }
        String message = text(node, "message");
        return new RuntimeStatus(
                code, message.isBlank() ? "" : String.valueOf(masker.maskValue("error.message", message)));
    }

    private Instant instant(String unixNanos, List<String> warnings, String spanId) {
        if (unixNanos == null || unixNanos.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            BigInteger nanos = new BigInteger(unixNanos);
            BigInteger billion = BigInteger.valueOf(1_000_000_000L);
            BigInteger[] parts = nanos.divideAndRemainder(billion);
            return Instant.ofEpochSecond(parts[0].longValueExact(), parts[1].longValueExact());
        } catch (ArithmeticException | NumberFormatException exception) {
            warnings.add("Invalid Unix-nanosecond timestamp in span " + spanId + ": " + unixNanos);
            return Instant.EPOCH;
        }
    }

    private void collectResourceSpans(JsonNode node, List<JsonNode> target) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectResourceSpans(child, target));
            return;
        }
        if (node.has("resource")
                && field(node, "scopeSpans", "scope_spans", "instrumentationLibrarySpans").isArray()) {
            target.add(node);
            return;
        }
        JsonNode resources = field(node, "resourceSpans", "resource_spans");
        if (resources.isArray()) {
            resources.forEach(target::add);
            return;
        }
        JsonNode data = field(node, "data");
        if (!data.isMissingNode()) {
            collectResourceSpans(data, target);
        }
    }

    private InputStream decompressIfNeeded(InputStream source) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(source);
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();
        return first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
    }

    private JsonNode field(JsonNode node, String... names) {
        if (node == null) {
            return mapper.missingNode();
        }
        for (String name : names) {
            if (node.has(name)) {
                return node.path(name);
            }
        }
        return mapper.missingNode();
    }

    private String text(JsonNode node, String... fields) {
        JsonNode value = field(node, fields);
        return value.isValueNode() ? value.asText() : "";
    }

    private List<JsonNode> elements(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(result::add);
        }
        return result;
    }

    private String identifier(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("0x") ? normalized.substring(2) : normalized;
    }

    private boolean has(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            if (attributes.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private String value(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
