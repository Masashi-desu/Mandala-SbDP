package io.github.mandala.sbdp.spring;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Normalizes Spring {@code HttpStatus} enum names and OpenAPI numeric status keys. */
public final class HttpStatusNormalizer {
    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("CONTINUE", "100"),
            Map.entry("SWITCHING_PROTOCOLS", "101"),
            Map.entry("PROCESSING", "102"),
            Map.entry("EARLY_HINTS", "103"),
            Map.entry("OK", "200"),
            Map.entry("CREATED", "201"),
            Map.entry("ACCEPTED", "202"),
            Map.entry("NON_AUTHORITATIVE_INFORMATION", "203"),
            Map.entry("NO_CONTENT", "204"),
            Map.entry("RESET_CONTENT", "205"),
            Map.entry("PARTIAL_CONTENT", "206"),
            Map.entry("MULTI_STATUS", "207"),
            Map.entry("ALREADY_REPORTED", "208"),
            Map.entry("IM_USED", "226"),
            Map.entry("MULTIPLE_CHOICES", "300"),
            Map.entry("MOVED_PERMANENTLY", "301"),
            Map.entry("FOUND", "302"),
            Map.entry("SEE_OTHER", "303"),
            Map.entry("NOT_MODIFIED", "304"),
            Map.entry("TEMPORARY_REDIRECT", "307"),
            Map.entry("PERMANENT_REDIRECT", "308"),
            Map.entry("BAD_REQUEST", "400"),
            Map.entry("UNAUTHORIZED", "401"),
            Map.entry("PAYMENT_REQUIRED", "402"),
            Map.entry("FORBIDDEN", "403"),
            Map.entry("NOT_FOUND", "404"),
            Map.entry("METHOD_NOT_ALLOWED", "405"),
            Map.entry("NOT_ACCEPTABLE", "406"),
            Map.entry("PROXY_AUTHENTICATION_REQUIRED", "407"),
            Map.entry("REQUEST_TIMEOUT", "408"),
            Map.entry("CONFLICT", "409"),
            Map.entry("GONE", "410"),
            Map.entry("LENGTH_REQUIRED", "411"),
            Map.entry("PRECONDITION_FAILED", "412"),
            Map.entry("PAYLOAD_TOO_LARGE", "413"),
            Map.entry("URI_TOO_LONG", "414"),
            Map.entry("UNSUPPORTED_MEDIA_TYPE", "415"),
            Map.entry("REQUESTED_RANGE_NOT_SATISFIABLE", "416"),
            Map.entry("EXPECTATION_FAILED", "417"),
            Map.entry("I_AM_A_TEAPOT", "418"),
            Map.entry("UNPROCESSABLE_ENTITY", "422"),
            Map.entry("LOCKED", "423"),
            Map.entry("FAILED_DEPENDENCY", "424"),
            Map.entry("TOO_EARLY", "425"),
            Map.entry("UPGRADE_REQUIRED", "426"),
            Map.entry("PRECONDITION_REQUIRED", "428"),
            Map.entry("TOO_MANY_REQUESTS", "429"),
            Map.entry("REQUEST_HEADER_FIELDS_TOO_LARGE", "431"),
            Map.entry("UNAVAILABLE_FOR_LEGAL_REASONS", "451"),
            Map.entry("INTERNAL_SERVER_ERROR", "500"),
            Map.entry("NOT_IMPLEMENTED", "501"),
            Map.entry("BAD_GATEWAY", "502"),
            Map.entry("SERVICE_UNAVAILABLE", "503"),
            Map.entry("GATEWAY_TIMEOUT", "504"),
            Map.entry("HTTP_VERSION_NOT_SUPPORTED", "505"),
            Map.entry("VARIANT_ALSO_NEGOTIATES", "506"),
            Map.entry("INSUFFICIENT_STORAGE", "507"),
            Map.entry("LOOP_DETECTED", "508"),
            Map.entry("BANDWIDTH_LIMIT_EXCEEDED", "509"),
            Map.entry("NOT_EXTENDED", "510"),
            Map.entry("NETWORK_AUTHENTICATION_REQUIRED", "511"));

    private HttpStatusNormalizer() {}

    public static String normalize(String status) {
        String value = Objects.requireNonNullElse(status, "").strip();
        if (value.isEmpty()) return "UNSPECIFIED";
        int separator = value.lastIndexOf('.');
        if (separator >= 0) value = value.substring(separator + 1);
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.endsWith("_VALUE")) upper = upper.substring(0, upper.length() - 6);
        if (upper.matches("[1-5][0-9]{2}") || upper.matches("[1-5]XX")) return upper;
        if (upper.equals("DEFAULT")) return "default";
        return CODES.getOrDefault(upper, upper);
    }

    public static boolean isSuccess(String status) {
        String normalized = normalize(status);
        return normalized.length() == 3 && normalized.charAt(0) == '2';
    }
}
