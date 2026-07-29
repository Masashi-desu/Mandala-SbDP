package io.github.mandala.sbdp.postgres;

import java.util.Objects;

public record TriggerDefinition(
        String name,
        String definition,
        String functionSchema,
        String functionName,
        String enabled) {
    public TriggerDefinition {
        name = Objects.requireNonNull(name, "name");
        definition = Objects.requireNonNullElse(definition, "");
        functionSchema = Objects.requireNonNullElse(functionSchema, "");
        functionName = Objects.requireNonNullElse(functionName, "");
        enabled = Objects.requireNonNullElse(enabled, "");
    }
}
