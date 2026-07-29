package io.github.mandala.sbdp.postgres;

import java.util.List;
import java.util.Objects;

public record PolicyDefinition(
        String name,
        String command,
        boolean permissive,
        List<String> roles,
        String usingExpression,
        String checkExpression) {
    public PolicyDefinition {
        name = Objects.requireNonNull(name, "name");
        command = Objects.requireNonNullElse(command, "ALL");
        roles = List.copyOf(roles == null ? List.of() : roles);
        usingExpression = Objects.requireNonNullElse(usingExpression, "");
        checkExpression = Objects.requireNonNullElse(checkExpression, "");
    }
}
