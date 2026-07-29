package io.github.mandala.sbdp.postgres;

import java.util.Objects;

public record FunctionDefinition(
        String name,
        String identityArguments,
        String resultType,
        String language,
        String kind,
        String volatility,
        boolean securityDefiner,
        String definition,
        String comment) {
    public FunctionDefinition {
        name = Objects.requireNonNull(name, "name");
        identityArguments = value(identityArguments);
        resultType = value(resultType);
        language = value(language);
        kind = value(kind);
        volatility = value(volatility);
        definition = value(definition);
        comment = value(comment);
    }

    private static String value(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
