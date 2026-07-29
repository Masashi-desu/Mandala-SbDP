package io.github.mandala.sbdp.spring;

import java.util.List;
import java.util.Objects;

public record ErrorResponseDescriptor(
        String handlerClass,
        String handlerMethod,
        List<String> exceptionTypes,
        String status,
        String responseType,
        String description,
        boolean globalAdvice,
        SourcePosition sourcePosition) {
    public ErrorResponseDescriptor {
        handlerClass = Objects.requireNonNull(handlerClass, "handlerClass");
        handlerMethod = Objects.requireNonNull(handlerMethod, "handlerMethod");
        exceptionTypes = List.copyOf(exceptionTypes == null ? List.of() : exceptionTypes);
        status = Objects.requireNonNullElse(status, "").strip();
        if (status.isBlank()) status = "UNSPECIFIED";
        responseType = Objects.requireNonNullElse(responseType, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
    }

    /** Backwards-compatible constructor for callers that model a controller-local handler. */
    public ErrorResponseDescriptor(
            String handlerClass,
            String handlerMethod,
            List<String> exceptionTypes,
            String status,
            String responseType,
            SourcePosition sourcePosition) {
        this(handlerClass, handlerMethod, exceptionTypes, status, responseType, "", false, sourcePosition);
    }
}
