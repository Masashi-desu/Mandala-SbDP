package io.github.mandala.sbdp.model;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class ModelValues {
    private ModelValues() {
    }

    static Map<String, Object> immutableMap(Map<String, ?> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Map<String, Object> sorted = new TreeMap<>();
        input.forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Attribute keys must not be blank");
            sorted.put(key, immutableValue(value));
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    @SuppressWarnings("unchecked")
    static Object immutableValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger integer) {
            try {
                return integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("JSON integer exceeds the supported signed 64-bit range", exception);
            }
        }
        if (value instanceof Float floating) {
            if (!Float.isFinite(floating)) throw new IllegalArgumentException("Non-finite JSON number: " + floating);
            return canonicalDecimal(new BigDecimal(floating.toString()));
        }
        if (value instanceof Double floating) {
            if (!Double.isFinite(floating)) throw new IllegalArgumentException("Non-finite JSON number: " + floating);
            return canonicalDecimal(new BigDecimal(floating.toString()));
        }
        if (value instanceof BigDecimal decimal) return canonicalDecimal(decimal);
        if (value instanceof Character character) return character.toString();
        if (value instanceof Enum<?> enumeration) return enumeration.name();
        if (value instanceof StableId stableId) return stableId.value();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String text)) {
                    throw new IllegalArgumentException("JSON object keys must be strings, found: " + key);
                }
                if (converted.putIfAbsent(text, nested) != null) {
                    throw new IllegalArgumentException("Duplicate JSON object key: " + text);
                }
            });
            return immutableMap(converted);
        }
        if (value instanceof Set<?> set) {
            List<Object> values = set.stream().map(ModelValues::immutableValue)
                    .distinct().sorted(Comparator.comparing(ModelValues::canonicalSortKey)).toList();
            return Collections.unmodifiableList(values);
        }
        if (value instanceof Collection<?> collection) {
            return Collections.unmodifiableList(collection.stream().map(ModelValues::immutableValue).toList());
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>(Array.getLength(value));
            for (int index = 0; index < Array.getLength(value); index++) {
                result.add(immutableValue(Array.get(value, index)));
            }
            return Collections.unmodifiableList(result);
        }
        throw new IllegalArgumentException("Unsupported attribute value type: " + value.getClass().getName());
    }

    private static Object canonicalDecimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            try {
                return normalized.longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Integral JSON number exceeds the supported signed 64-bit range",
                        exception);
            }
        }
        return normalized;
    }

    private static String canonicalSortKey(Object value) {
        if (value == null) return "0:null";
        if (value instanceof Boolean) return "1:" + value;
        if (value instanceof Long) return "2:" + value;
        if (value instanceof BigDecimal) return "3:" + value;
        if (value instanceof String) return "4:" + value;
        if (value instanceof Map<?, ?>) return "5:" + value;
        if (value instanceof List<?>) return "6:" + value;
        return "9:" + value.getClass().getName() + ":" + value;
    }
}
