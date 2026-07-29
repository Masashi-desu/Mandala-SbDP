package io.github.mandala.sbdp.postgres;

import java.util.List;
import java.util.Objects;

public record DomainDefinition(
        String name,
        String baseType,
        boolean notNull,
        String defaultExpression,
        List<String> checkConstraints,
        String comment) {
    public DomainDefinition {
        name = Objects.requireNonNull(name, "name");
        baseType = Objects.requireNonNullElse(baseType, "");
        defaultExpression = Objects.requireNonNullElse(defaultExpression, "");
        checkConstraints = List.copyOf(checkConstraints == null ? List.of() : checkConstraints);
        comment = Objects.requireNonNullElse(comment, "");
    }
}
