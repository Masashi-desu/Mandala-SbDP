package io.github.mandala.sbdp.postgres;

import java.util.List;
import java.util.Objects;

public record EnumDefinition(String name, List<String> values, String comment) {
    public EnumDefinition {
        name = Objects.requireNonNull(name, "name");
        values = List.copyOf(values == null ? List.of() : values);
        comment = Objects.requireNonNullElse(comment, "");
    }
}
