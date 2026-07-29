package io.github.mandala.sbdp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Removes capture-instance metadata before semantic comparison.
 *
 * <p>The normalizer is public so collection adapters can use exactly the same
 * definition of "semantic" when they have to assign deterministic identities
 * to repeated observations. Keeping one definition avoids a subtle class of
 * bugs where the adapter labels two observations differently even though the
 * graph differ correctly considers them equivalent.</p>
 */
public final class SemanticAttributes {
    private static final Set<String> UNIVERSAL_VOLATILE_KEYS = Set.of(
            "capturedat", "analyzedat", "generatedat", "observedat", "sourcefingerprint"
    );
    private static final Set<String> RUNTIME_VOLATILE_KEYS = Set.of(
            "traceid", "spanid", "parentspanid", "tracestate", "starttime", "endtime",
            "starttimeunixnano", "endtimeunixnano", "durationmillis", "timestamp",
            "observedtimestamp", "eventtime", "eventtimeunixnano", "time", "timeunixnano",
            "serviceinstanceid", "processpid", "threadid", "urlpath"
    );

    private SemanticAttributes() {
    }

    public static Map<String, Object> normalize(Map<String, Object> attributes, boolean runtimeObservation) {
        Map<String, Object> result = new TreeMap<>();
        attributes.forEach((key, value) -> {
            if (!volatileKey(key, runtimeObservation)) result.put(key, normalizeValue(value, runtimeObservation));
        });
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    public static boolean volatileKey(String key, boolean runtimeObservation) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return UNIVERSAL_VOLATILE_KEYS.contains(normalized)
                || runtimeObservation && RUNTIME_VOLATILE_KEYS.contains(normalized);
    }

    private static Object normalizeValue(Object value, boolean runtimeObservation) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, nested) -> converted.put(String.valueOf(key), nested));
            return normalize(converted, runtimeObservation);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            collection.forEach(item -> result.add(normalizeValue(item, runtimeObservation)));
            return java.util.Collections.unmodifiableList(result);
        }
        return value;
    }
}
