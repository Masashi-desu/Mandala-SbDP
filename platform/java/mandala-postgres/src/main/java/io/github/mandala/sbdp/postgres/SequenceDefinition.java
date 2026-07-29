package io.github.mandala.sbdp.postgres;

import java.util.Objects;

public record SequenceDefinition(
        String name,
        String dataType,
        long startValue,
        long minimumValue,
        long maximumValue,
        long increment,
        long cacheSize,
        boolean cycle) {
    public SequenceDefinition {
        name = Objects.requireNonNull(name, "name");
        dataType = Objects.requireNonNullElse(dataType, "");
    }
}
