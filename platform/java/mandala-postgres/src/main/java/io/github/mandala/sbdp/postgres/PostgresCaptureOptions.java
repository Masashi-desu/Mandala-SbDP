package io.github.mandala.sbdp.postgres;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public record PostgresCaptureOptions(Set<String> includedSchemas, Set<String> excludedSchemas) {
    public PostgresCaptureOptions {
        includedSchemas = immutableSortedSet(includedSchemas == null ? Set.of() : includedSchemas);
        excludedSchemas = immutableSortedSet(excludedSchemas == null
                ? Set.of("pg_catalog", "information_schema")
                : excludedSchemas);
    }

    public static PostgresCaptureOptions userSchemas() {
        return new PostgresCaptureOptions(Set.of(), null);
    }

    public boolean includes(String schema) {
        if (excludedSchemas.contains(schema) || schema.startsWith("pg_toast") || schema.startsWith("pg_temp_")) {
            return false;
        }
        return includedSchemas.isEmpty() || includedSchemas.contains(schema);
    }

    private static Set<String> immutableSortedSet(Set<String> values) {
        if (values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
    }
}
