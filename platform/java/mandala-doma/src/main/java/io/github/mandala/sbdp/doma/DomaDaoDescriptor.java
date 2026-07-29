package io.github.mandala.sbdp.doma;

import java.util.List;
import java.util.Objects;

public record DomaDaoDescriptor(
        String stableId,
        String qualifiedName,
        boolean configAutowireable,
        List<DomaMethodDescriptor> methods,
        String javadocSummary,
        DomaSourcePosition sourcePosition) {
    public DomaDaoDescriptor {
        stableId = Objects.requireNonNull(stableId, "stableId");
        qualifiedName = Objects.requireNonNull(qualifiedName, "qualifiedName");
        methods = List.copyOf(methods == null ? List.of() : methods);
        javadocSummary = Objects.requireNonNullElse(javadocSummary, "");
        sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition");
    }
}
