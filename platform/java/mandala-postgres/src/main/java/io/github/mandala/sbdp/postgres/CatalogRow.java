package io.github.mandala.sbdp.postgres;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable, JDBC-driver-neutral result row used by the catalog converter. */
public final class CatalogRow {
    private final Map<String, Object> values;

    public CatalogRow(Map<String, ?> values) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        // Catalog NULLs are semantically meaningful, while Map.copyOf rejects null values.
        this.values = Collections.unmodifiableMap(normalized);
    }

    public String string(String column) {
        Object value = values.get(column.toLowerCase(Locale.ROOT));
        return value == null ? "" : String.valueOf(value);
    }

    public boolean bool(String column) {
        Object value = values.get(column.toLowerCase(Locale.ROOT));
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && (String.valueOf(value).equalsIgnoreCase("true")
                || String.valueOf(value).equalsIgnoreCase("t")
                || String.valueOf(value).equals("1"));
    }

    public int integer(String column) {
        Object value = values.get(column.toLowerCase(Locale.ROOT));
        return value instanceof Number number ? number.intValue() : value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    public long longValue(String column) {
        Object value = values.get(column.toLowerCase(Locale.ROOT));
        return value instanceof Number number ? number.longValue() : value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    public List<String> strings(String column) throws SQLException {
        Object value = values.get(column.toLowerCase(Locale.ROOT));
        if (value == null) {
            return List.of();
        }
        if (value instanceof Array array) {
            return strings(array.getArray());
        }
        return strings(value);
    }

    private List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Object[] array) {
            for (Object item : array) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return List.copyOf(result);
        }
        if (value instanceof Collection<?> collection) {
            collection.stream().filter(item -> item != null).map(String::valueOf).forEach(result::add);
            return List.copyOf(result);
        }
        String text = String.valueOf(value);
        if (text.startsWith("{") && text.endsWith("}")) {
            return parsePostgresArray(text);
        }
        return List.of(text);
    }

    private List<String> parsePostgresArray(String text) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 1; index < text.length() - 1; index++) {
            char character = text.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                if (!current.toString().equals("NULL")) {
                    values.add(current.toString());
                }
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (!current.isEmpty() && !current.toString().equals("NULL")) {
            values.add(current.toString());
        }
        return List.copyOf(values);
    }
}
