package io.github.mandala.sbdp.doma;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DomaMethodDescriptor(
        String stableId,
        String daoClass,
        String methodName,
        String returnType,
        List<DaoParameter> parameters,
        DomaOperation operation,
        boolean sqlFileDeclared,
        Path externalSqlFile,
        String javadocSummary,
        DomaSourcePosition sourcePosition,
        Map<String, Object> attributes) {

    public DomaMethodDescriptor {
        stableId = Objects.requireNonNull(stableId, "stableId");
        daoClass = Objects.requireNonNull(daoClass, "daoClass");
        methodName = Objects.requireNonNull(methodName, "methodName");
        returnType = Objects.requireNonNullElse(returnType, "");
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        operation = Objects.requireNonNull(operation, "operation");
        if (externalSqlFile != null) {
            externalSqlFile = externalSqlFile.toAbsolutePath().normalize();
        }
        javadocSummary = Objects.requireNonNullElse(javadocSummary, "");
        sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
