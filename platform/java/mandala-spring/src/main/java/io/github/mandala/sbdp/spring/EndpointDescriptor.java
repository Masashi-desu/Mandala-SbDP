package io.github.mandala.sbdp.spring;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Framework-neutral normalized representation of a Spring HTTP handler. */
public record EndpointDescriptor(
        String stableId,
        String httpMethod,
        String path,
        String controllerClass,
        String handlerMethod,
        Set<String> consumes,
        Set<String> produces,
        List<EndpointParameter> parameters,
        String requestBodyType,
        List<EndpointResponse> responses,
        String operationId,
        String summary,
        String description,
        EndpointSource source,
        SourcePosition sourcePosition,
        Map<String, Object> attributes) {

    public EndpointDescriptor {
        stableId = Objects.requireNonNull(stableId, "stableId");
        httpMethod = Objects.requireNonNull(httpMethod, "httpMethod").toUpperCase();
        path = normalizePath(path);
        controllerClass = Objects.requireNonNullElse(controllerClass, "");
        handlerMethod = Objects.requireNonNullElse(handlerMethod, "");
        consumes = immutableSortedSet(consumes);
        produces = immutableSortedSet(produces);
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        requestBodyType = Objects.requireNonNullElse(requestBodyType, "");
        responses = List.copyOf(responses == null ? List.of() : responses);
        operationId = Objects.requireNonNullElse(operationId, "");
        summary = Objects.requireNonNullElse(summary, "");
        description = Objects.requireNonNullElse(description, "");
        source = Objects.requireNonNull(source, "source");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    private static Set<String> immutableSortedSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
    }

    public static String stableId(String method, String path) {
        return "endpoint:" + Objects.requireNonNull(method).toUpperCase() + ":" + normalizePath(path);
    }

    public static String normalizePath(String value) {
        String path = Objects.requireNonNullElse(value, "").trim();
        if (path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        path = path.replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
