package io.github.mandala.sbdp.spring;

import java.util.List;
import java.util.Objects;

public record JavaSymbolDescriptor(
        String stableId,
        String qualifiedName,
        String memberName,
        String kind,
        String signature,
        String javadocSummary,
        List<String> annotations,
        SourcePosition sourcePosition) {

    public JavaSymbolDescriptor {
        stableId = Objects.requireNonNull(stableId, "stableId");
        qualifiedName = Objects.requireNonNull(qualifiedName, "qualifiedName");
        memberName = Objects.requireNonNullElse(memberName, "");
        kind = Objects.requireNonNull(kind, "kind");
        signature = Objects.requireNonNullElse(signature, "");
        javadocSummary = Objects.requireNonNullElse(javadocSummary, "");
        annotations = List.copyOf(annotations == null ? List.of() : annotations);
    }
}
