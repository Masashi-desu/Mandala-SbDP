package io.github.mandala.sbdp.spring;

import java.util.List;
import java.util.Objects;

public record EndpointParameter(
        String name,
        ParameterLocation location,
        String javaType,
        boolean required,
        String defaultValue,
        List<String> validation,
        String description) {

    public EndpointParameter {
        name = Objects.requireNonNullElse(name, "");
        location = Objects.requireNonNull(location, "location");
        javaType = Objects.requireNonNullElse(javaType, "");
        defaultValue = Objects.requireNonNullElse(defaultValue, "");
        validation = List.copyOf(validation == null ? List.of() : validation);
        description = Objects.requireNonNullElse(description, "");
    }
}
