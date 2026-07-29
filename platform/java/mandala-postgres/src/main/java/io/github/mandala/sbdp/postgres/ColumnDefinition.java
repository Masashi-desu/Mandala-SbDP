package io.github.mandala.sbdp.postgres;

import java.util.Objects;

public record ColumnDefinition(
        String name,
        int ordinal,
        String formattedType,
        String typeSchema,
        String typeName,
        boolean nullable,
        String defaultExpression,
        String identityKind,
        String generatedKind,
        String comment) {
    public ColumnDefinition {
        name = Objects.requireNonNull(name, "name");
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal must be positive");
        }
        formattedType = value(formattedType);
        typeSchema = value(typeSchema);
        typeName = value(typeName);
        defaultExpression = value(defaultExpression);
        identityKind = value(identityKind);
        generatedKind = value(generatedKind);
        comment = value(comment);
    }

    private static String value(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
