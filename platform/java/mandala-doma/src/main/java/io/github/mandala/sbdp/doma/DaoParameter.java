package io.github.mandala.sbdp.doma;

import java.util.List;
import java.util.Objects;

public record DaoParameter(String name, String type, List<String> annotations) {
    public DaoParameter {
        name = Objects.requireNonNull(name, "name");
        type = Objects.requireNonNull(type, "type");
        annotations = List.copyOf(annotations == null ? List.of() : annotations);
    }
}
