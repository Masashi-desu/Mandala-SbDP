package io.github.mandala.sbdp.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public record Evidence(
        StableId id,
        EvidenceType type,
        EvidenceScope scope,
        String source,
        String description,
        SourceLocation sourceLocation,
        String targetCommit,
        Instant observedAt,
        String adapter,
        Confidence confidence,
        Map<String, Object> details
) implements Comparable<Evidence> {
    public Evidence {
        type = Objects.requireNonNull(type, "type");
        scope = scope == null ? EvidenceScope.TECHNICAL_FACT : scope;
        source = Objects.requireNonNullElse(source, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
        targetCommit = Objects.requireNonNullElse(targetCommit, "").strip();
        adapter = Objects.requireNonNullElse(adapter, "").strip();
        confidence = confidence == null ? defaultConfidence(type) : confidence;
        details = ModelValues.immutableMap(details);
        validateConfidence(type, confidence);
        if (source.isBlank()) throw new IllegalArgumentException("Evidence source must not be blank");
        if (description.isBlank() && details.isEmpty()) {
            throw new IllegalArgumentException("Evidence requires a description or structured details");
        }
        id = id == null ? generatedId(type, scope, source, description, sourceLocation,
                semanticDetails(type, details)) : id;
    }

    public static Evidence of(EvidenceType type, String source, String description) {
        return new Evidence(null, type, EvidenceScope.TECHNICAL_FACT, source, description,
                null, "", null, "", null, Map.of());
    }

    public static Evidence humanReviewed(String source, String description) {
        return new Evidence(null, EvidenceType.HUMAN_INPUT, EvidenceScope.DESIGN_INTENT, source, description,
                null, "", null, "", Confidence.HUMAN_REVIEWED, Map.of());
    }

    private static Confidence defaultConfidence(EvidenceType type) {
        return switch (type) {
            case RUNTIME_OBSERVATION, PLAYWRIGHT_OBSERVATION -> Confidence.OBSERVED;
            case SPRING_MAPPING, OPENAPI, DATABASE_INTROSPECTION -> Confidence.DECLARED;
            case HUMAN_INPUT -> Confidence.HUMAN_REVIEWED;
            default -> Confidence.INFERRED;
        };
    }

    private static void validateConfidence(EvidenceType type, Confidence confidence) {
        Confidence expected = defaultConfidence(type);
        if (confidence != expected) {
            throw new IllegalArgumentException("Evidence type " + type + " must use " + expected
                    + " confidence, not " + confidence);
        }
    }

    private static Map<String, Object> semanticDetails(EvidenceType type, Map<String, Object> details) {
        boolean runtime = type == EvidenceType.RUNTIME_OBSERVATION || type == EvidenceType.PLAYWRIGHT_OBSERVATION;
        return normalizeDetails(details, runtime);
    }

    private static Map<String, Object> normalizeDetails(Map<String, Object> details, boolean runtime) {
        Set<String> universal = Set.of("capturedat", "analyzedat", "generatedat", "observedat", "sourcefingerprint");
        Set<String> operational = Set.of("traceid", "spanid", "parentspanid", "tracestate",
                "starttime", "endtime", "starttimeunixnano", "endtimeunixnano", "durationmillis",
                "timestamp", "observedtimestamp", "eventtime", "eventtimeunixnano", "time",
                "timeunixnano", "serviceinstanceid", "processpid", "threadid");
        Map<String, Object> result = new TreeMap<>();
        details.forEach((key, value) -> {
            String normalizedKey = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!universal.contains(normalizedKey) && !(runtime && operational.contains(normalizedKey))) {
                result.put(key, normalizeDetailValue(value, runtime));
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static Object normalizeDetailValue(Object value, boolean runtime) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, nested) -> converted.put(String.valueOf(key), nested));
            return normalizeDetails(converted, runtime);
        }
        if (value instanceof Collection<?> values) {
            List<Object> result = new ArrayList<>(values.size());
            values.forEach(item -> result.add(normalizeDetailValue(item, runtime)));
            return Collections.unmodifiableList(result);
        }
        return value;
    }

    private static StableId generatedId(EvidenceType type, EvidenceScope scope, String source,
                                        String description, SourceLocation location, Map<String, Object> details) {
        String semanticLocation = location == null ? "" : location.path() + "\u0000" + location.symbol();
        String material = type + "\u0000" + scope + "\u0000" + source + "\u0000" + description
                + "\u0000" + semanticLocation + "\u0000" + details;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return StableId.of("evidence:" + HexFormat.of().formatHex(digest, 0, 12));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public int compareTo(Evidence other) {
        return id.compareTo(other.id);
    }
}
