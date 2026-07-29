package io.github.mandala.sbdp.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads Spring Boot 2/3 Actuator {@code /actuator/mappings} JSON without loading the app. */
public final class ActuatorMappingsAnalyzer {
    private final ObjectMapper mapper = new ObjectMapper();

    public EndpointDiscovery analyze(Path mappingsJson) throws IOException {
        return analyze(mapper.readTree(mappingsJson.toFile()), mappingsJson);
    }

    public EndpointDiscovery analyze(JsonNode root, Path source) {
        List<EndpointDescriptor> endpoints = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        visit(root, source, endpoints, warnings);
        if (endpoints.isEmpty()) {
            warnings.add("No dispatcher handler mappings were found in Actuator JSON");
        }
        return new EndpointDiscovery(endpoints, warnings);
    }

    private void visit(
            JsonNode node,
            Path source,
            List<EndpointDescriptor> endpoints,
            List<String> warnings) {
        if (node.isObject() && node.has("handlerMethod") && node.has("requestMappingConditions")) {
            convert(node, source, endpoints, warnings);
        }
        if (node.isContainerNode()) {
            node.elements().forEachRemaining(child -> visit(child, source, endpoints, warnings));
        }
    }

    private void convert(
            JsonNode details,
            Path source,
            List<EndpointDescriptor> endpoints,
            List<String> warnings) {
        JsonNode handler = details.path("handlerMethod");
        JsonNode conditions = details.path("requestMappingConditions");
        String controller = firstText(handler, "className", "beanType");
        String handlerMethod = firstText(handler, "name", "methodName");
        Set<String> paths = conditionValues(conditions, "patterns", "pathPatterns");
        Set<String> methods = conditionValues(conditions, "methods");
        if (methods.isEmpty()) {
            methods = Set.of("ANY");
        }
        Set<String> consumes = mediaTypes(conditions.path("consumes"));
        Set<String> produces = mediaTypes(conditions.path("produces"));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("handlerDescriptor", firstText(handler, "descriptor"));
        attributes.put("mappingName", firstText(details, "name"));
        attributes.put("params", expressionValues(conditions.path("params")));
        attributes.put("headers", expressionValues(conditions.path("headers")));
        if (paths.isEmpty()) {
            warnings.add(controller + "#" + handlerMethod + ": mapping has no path pattern");
            return;
        }
        for (String method : methods) {
            for (String path : paths) {
                endpoints.add(new EndpointDescriptor(
                        EndpointDescriptor.stableId(method, path),
                        method.toUpperCase(Locale.ROOT),
                        path,
                        controller,
                        handlerMethod,
                        consumes,
                        produces,
                        List.of(),
                        "",
                        List.of(),
                        "",
                        "",
                        "",
                        EndpointSource.ACTUATOR,
                        source == null ? null : new SourcePosition(source, 1, 1),
                        attributes));
            }
        }
    }

    private Set<String> conditionValues(JsonNode conditions, String... names) {
        for (String name : names) {
            JsonNode value = conditions.path(name);
            Set<String> result = new LinkedHashSet<>();
            collectStrings(value, result, "pattern", "value", "method");
            if (!result.isEmpty()) {
                return result;
            }
        }
        return Set.of();
    }

    private Set<String> mediaTypes(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        collectStrings(node, result, "mediaType", "value");
        return result;
    }

    private List<String> expressionValues(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        collectStrings(node, result, "expression", "value");
        return List.copyOf(result);
    }

    private void collectStrings(JsonNode node, Set<String> target, String... preferredFields) {
        if (node.isTextual()) {
            target.add(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> collectStrings(value, target, preferredFields));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (String field : preferredFields) {
            if (node.has(field)) {
                collectStrings(node.path(field), target, preferredFields);
            }
        }
        // Some Spring Boot versions wrap values in a "values" member.
        if (node.has("values")) {
            collectStrings(node.path("values"), target, preferredFields);
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.path(field).isValueNode()) {
                return node.path(field).asText();
            }
        }
        return "";
    }
}
