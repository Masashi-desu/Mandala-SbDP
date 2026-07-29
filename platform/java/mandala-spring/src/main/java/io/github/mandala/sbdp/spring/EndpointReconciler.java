package io.github.mandala.sbdp.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Merges source, OpenAPI and runtime mapping declarations while retaining disagreements. */
public final class EndpointReconciler {
    public List<ReconciledEndpoint> reconcile(Collection<EndpointDescriptor> declarations) {
        Map<String, List<EndpointDescriptor>> grouped = declarations.stream()
                .collect(Collectors.groupingBy(this::matchKey, LinkedHashMap::new, Collectors.toList()));
        List<ReconciledEndpoint> result = new ArrayList<>();
        grouped.values().forEach(group -> result.add(reconcileGroup(group)));
        result.sort(Comparator.comparing(ReconciledEndpoint::stableId));
        return List.copyOf(result);
    }

    private ReconciledEndpoint reconcileGroup(List<EndpointDescriptor> group) {
        List<EndpointDescriptor> declarations = group.stream().sorted(
                Comparator.comparingInt(this::priority)
                        .thenComparing(EndpointDescriptor::controllerClass)
                        .thenComparing(EndpointDescriptor::handlerMethod)
                        .thenComparing(EndpointDescriptor::operationId)
                        .thenComparing(endpoint -> new TreeSet<>(endpoint.consumes()).toString())
                        .thenComparing(endpoint -> new TreeSet<>(endpoint.produces()).toString())
                        .thenComparing(endpoint -> new TreeMap<>(endpoint.attributes()).toString()))
                .toList();
        EndpointDescriptor preferred = declarations.getFirst();
        Set<String> consumes = union(declarations, EndpointDescriptor::consumes);
        Set<String> produces = union(declarations, EndpointDescriptor::produces);
        List<EndpointParameter> parameters = declarations.stream()
                .map(EndpointDescriptor::parameters)
                .max(Comparator.comparingInt(List::size))
                .orElse(List.of());
        List<EndpointResponse> responses = declarations.stream()
                .flatMap(endpoint -> endpoint.responses().stream())
                .distinct()
                .sorted(Comparator.comparing(EndpointResponse::status)
                        .thenComparing(EndpointResponse::mediaType)
                        .thenComparing(EndpointResponse::type)
                        .thenComparing(EndpointResponse::description))
                .toList();
        Map<String, Object> attributes = new LinkedHashMap<>(preferred.attributes());
        attributes.put("declarationSources", declarations.stream().map(endpoint -> endpoint.source().name()).distinct().toList());
        EndpointDescriptor openapi = firstOf(declarations, EndpointSource.OPENAPI);
        EndpointDescriptor javaSource = firstOf(declarations, EndpointSource.JAVA_SOURCE);
        String controller = firstNonBlank(javaSource.controllerClass(), preferred.controllerClass());
        String handler = firstNonBlank(javaSource.handlerMethod(), preferred.handlerMethod());
        String body = firstNonBlank(javaSource.requestBodyType(), openapi.requestBodyType(), preferred.requestBodyType());
        String summary = firstNonBlank(openapi.summary(), javaSource.summary(), preferred.summary());
        String description = firstNonBlank(openapi.description(), javaSource.description(), preferred.description());
        String operationId = firstNonBlank(openapi.operationId(), javaSource.operationId(), preferred.operationId());
        EndpointDescriptor canonical = new EndpointDescriptor(
                EndpointDescriptor.stableId(preferred.httpMethod(), preferred.path()),
                preferred.httpMethod(),
                preferred.path(),
                controller,
                handler,
                consumes,
                produces,
                parameters,
                body,
                responses,
                operationId,
                summary,
                description,
                preferred.source(),
                preferred.sourcePosition(),
                attributes);
        EnumSet<EndpointSource> sources = EnumSet.noneOf(EndpointSource.class);
        declarations.forEach(endpoint -> sources.add(endpoint.source()));
        return new ReconciledEndpoint(canonical.stableId(), canonical, sources, declarations, conflicts(declarations));
    }

    private List<String> conflicts(List<EndpointDescriptor> declarations) {
        List<String> conflicts = new ArrayList<>();
        Set<String> controllers = declarations.stream().map(EndpointDescriptor::controllerClass)
                .filter(value -> !value.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
        if (controllers.size() > 1) {
            conflicts.add("Controller declarations disagree: " + controllers);
        }
        Set<String> requestBodies = declarations.stream().map(EndpointDescriptor::requestBodyType)
                .filter(value -> !value.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestBodies.size() > 1 && !compatibleTypes(requestBodies)) {
            conflicts.add("Request body declarations disagree: " + requestBodies);
        }
        Map<EndpointSource, Set<String>> successStatuses = declarations.stream()
                .collect(Collectors.groupingBy(EndpointDescriptor::source, LinkedHashMap::new,
                        Collectors.flatMapping(endpoint -> endpoint.responses().stream()
                                        .map(EndpointResponse::status)
                                        .map(HttpStatusNormalizer::normalize)
                                        .filter(HttpStatusNormalizer::isSuccess),
                                Collectors.toCollection(LinkedHashSet::new))));
        Set<String> javaStatuses = successStatuses.getOrDefault(EndpointSource.JAVA_SOURCE, Set.of());
        Set<String> openApiStatuses = successStatuses.getOrDefault(EndpointSource.OPENAPI, Set.of());
        if (!javaStatuses.isEmpty() && !openApiStatuses.isEmpty()
                && javaStatuses.stream().noneMatch(openApiStatuses::contains)) {
            conflicts.add("Successful response status declarations disagree: Java " + javaStatuses
                    + ", OpenAPI " + openApiStatuses);
        }
        return List.copyOf(conflicts);
    }

    private boolean compatibleTypes(Set<String> types) {
        if (types.size() < 2) {
            return true;
        }
        Set<String> simple = types.stream()
                .map(value -> value.substring(value.lastIndexOf('.') + 1))
                .map(value -> value.replaceAll("^(Mono|Flux|ResponseEntity)<(.+)>$", "$2"))
                .collect(Collectors.toSet());
        return simple.size() == 1;
    }

    private String matchKey(EndpointDescriptor endpoint) {
        return endpoint.httpMethod() + ":" + endpoint.path().replaceAll("\\{[^/}]+}", "{}");
    }

    private int priority(EndpointDescriptor endpoint) {
        return switch (endpoint.source()) {
            case JAVA_SOURCE -> 0;
            case ACTUATOR -> 1;
            case OPENAPI -> 2;
        };
    }

    private EndpointDescriptor firstOf(List<EndpointDescriptor> declarations, EndpointSource source) {
        return declarations.stream().filter(endpoint -> endpoint.source() == source).findFirst()
                .orElse(declarations.getFirst());
    }

    private Set<String> union(
            List<EndpointDescriptor> endpoints,
            Function<EndpointDescriptor, Set<String>> extractor) {
        Set<String> result = new TreeSet<>();
        endpoints.forEach(endpoint -> result.addAll(extractor.apply(endpoint)));
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
