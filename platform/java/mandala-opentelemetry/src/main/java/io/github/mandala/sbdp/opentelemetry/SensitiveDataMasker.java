package io.github.mandala.sbdp.opentelemetry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies key-based redaction and value-free SQL normalization at the trace ingestion boundary. */
public final class SensitiveDataMasker {
    private static final Set<String> SQL_KEYS = Set.of(
            "dbstatement", "dbquerytext", "dbquerysummary", "sqlquery");

    private final MaskingConfiguration configuration;
    private final Set<String> exactKeys;
    private final List<String> fragments;

    public SensitiveDataMasker() {
        this(MaskingConfiguration.secureDefaults());
    }

    public SensitiveDataMasker(MaskingConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.exactKeys = configuration.sensitiveKeys().stream().map(this::normalizeKey).collect(Collectors.toSet());
        this.fragments = configuration.sensitiveKeyFragments().stream().map(this::normalizeKey).toList();
    }

    public Map<String, Object> maskAttributes(Map<String, ?> attributes) {
        Map<String, Object> masked = new LinkedHashMap<>();
        attributes.forEach((key, value) -> masked.put(key, maskValue(key, value)));
        return Map.copyOf(masked);
    }

    public Object maskValue(String key, Object value) {
        String normalized = normalizeKey(key);
        if (isSensitive(normalized)) {
            return configuration.replacement();
        }
        if (configuration.maskSqlLiterals() && SQL_KEYS.contains(normalized) && value instanceof String sql) {
            return sanitizeSql(sql);
        }
        return maskNested(value);
    }

    private Object maskNested(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> result.put(String.valueOf(key), maskValue(String.valueOf(key), nested)));
            return Map.copyOf(result);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(maskNested(item)));
            return List.copyOf(result);
        }
        return value;
    }

    private boolean isSensitive(String normalizedKey) {
        return exactKeys.contains(normalizedKey) || fragments.stream().anyMatch(normalizedKey::contains);
    }

    private String normalizeKey(String key) {
        StringBuilder normalized = new StringBuilder();
        String lower = Objects.requireNonNullElse(key, "").toLowerCase(Locale.ROOT);
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    /** SQL literal lexer; identifiers and PostgreSQL operators remain available for CRUD analysis. */
    private String sanitizeSql(String sql) {
        StringBuilder output = new StringBuilder(sql.length());
        int cursor = 0;
        while (cursor < sql.length()) {
            char character = sql.charAt(cursor);
            if (character == '"') {
                int end = quotedEnd(sql, cursor, '"');
                output.append(sql, cursor, end);
                cursor = end;
                continue;
            }
            if (character == '\'') {
                if (output.length() > 0
                        && (output.charAt(output.length() - 1) == 'E' || output.charAt(output.length() - 1) == 'e')
                        && (output.length() == 1 || !identifier(output.charAt(output.length() - 2)))) {
                    output.setLength(output.length() - 1);
                }
                output.append('?');
                cursor = quotedEnd(sql, cursor, '\'');
                continue;
            }
            String delimiter = dollarDelimiter(sql, cursor);
            if (delimiter != null) {
                int end = sql.indexOf(delimiter, cursor + delimiter.length());
                output.append('?');
                cursor = end < 0 ? sql.length() : end + delimiter.length();
                continue;
            }
            if (Character.isDigit(character)
                    && (cursor == 0 || (!identifier(sql.charAt(cursor - 1)) && sql.charAt(cursor - 1) != '$'))) {
                output.append('?');
                cursor = numberEnd(sql, cursor);
                continue;
            }
            output.append(character);
            cursor++;
        }
        return output.toString();
    }

    private int quotedEnd(String sql, int start, char quote) {
        int cursor = start + 1;
        while (cursor < sql.length()) {
            if (sql.charAt(cursor) == quote) {
                if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == quote) {
                    cursor += 2;
                    continue;
                }
                return cursor + 1;
            }
            if (sql.charAt(cursor) == '\\' && quote == '\'' && cursor + 1 < sql.length()) {
                cursor += 2;
            } else {
                cursor++;
            }
        }
        return cursor;
    }

    private int numberEnd(String sql, int cursor) {
        int index = cursor;
        boolean exponent = false;
        while (index < sql.length()) {
            char character = sql.charAt(index);
            if (Character.isDigit(character) || character == '.' || character == '_') {
                index++;
            } else if ((character == 'e' || character == 'E') && !exponent) {
                exponent = true;
                index++;
                if (index < sql.length() && (sql.charAt(index) == '+' || sql.charAt(index) == '-')) {
                    index++;
                }
            } else {
                break;
            }
        }
        return index;
    }

    private String dollarDelimiter(String sql, int cursor) {
        if (sql.charAt(cursor) != '$') {
            return null;
        }
        int end = sql.indexOf('$', cursor + 1);
        if (end < 0) {
            return null;
        }
        for (int index = cursor + 1; index < end; index++) {
            char character = sql.charAt(index);
            if (!(Character.isLetterOrDigit(character) || character == '_')) {
                return null;
            }
        }
        return sql.substring(cursor, end + 1);
    }

    private boolean identifier(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }
}
