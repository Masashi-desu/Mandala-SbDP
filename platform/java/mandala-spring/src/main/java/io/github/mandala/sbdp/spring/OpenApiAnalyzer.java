package io.github.mandala.sbdp.spring;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts an OpenAPI 3 JSON or YAML document into the common endpoint representation. */
public final class OpenApiAnalyzer {
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    public EndpointDiscovery analyze(Path document) throws IOException {
        String name = document.getFileName().toString().toLowerCase(Locale.ROOT);
        JsonFactory factory = name.endsWith(".yaml") || name.endsWith(".yml")
                ? new YAMLFactory()
                : new JsonFactory();
        try (InputStream input = Files.newInputStream(document)) {
            return analyze(new ObjectMapper(factory).readTree(input), document);
        }
    }

    public EndpointDiscovery analyze(JsonNode root, Path source) {
        List<EndpointDescriptor> endpoints = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            return new EndpointDiscovery(List.of(), List.of("OpenAPI document has no paths object"));
        }
        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            List<JsonNode> commonParameters = elements(pathItem.path("parameters"));
            pathItem.fields().forEachRemaining(operationEntry -> {
                String method = operationEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    return;
                }
                JsonNode operation = operationEntry.getValue();
                List<JsonNode> parameters = new ArrayList<>(commonParameters);
                parameters.addAll(elements(operation.path("parameters")));
                List<EndpointParameter> normalizedParameters = parameters.stream()
                        .map(parameter -> parameter(parameter, root))
                        .toList();

                JsonNode requestBody = dereference(operation.path("requestBody"), root);
                Set<String> consumes = fieldNames(requestBody.path("content"));
                String requestType = firstSchemaType(requestBody.path("content"), root);
                List<EndpointResponse> responses = responses(operation.path("responses"), root);
                Set<String> produces = new LinkedHashSet<>();
                responses.stream().map(EndpointResponse::mediaType)
                        .filter(mediaType -> !mediaType.isBlank())
                        .forEach(produces::add);
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("tags", textValues(operation.path("tags")));
                attributes.put("deprecated", operation.path("deprecated").asBoolean(false));
                if (operation.has("security")) {
                    attributes.put("security", operation.path("security").toString());
                }
                String controller = text(operation, "x-spring-controller");
                String operationId = text(operation, "operationId");
                endpoints.add(new EndpointDescriptor(
                        EndpointDescriptor.stableId(method, path),
                        method,
                        path,
                        controller,
                        operationId,
                        consumes,
                        produces,
                        normalizedParameters,
                        requestType,
                        responses,
                        operationId,
                        text(operation, "summary"),
                        text(operation, "description"),
                        EndpointSource.OPENAPI,
                        source == null ? null : new SourcePosition(source, 1, 1),
                        attributes));
                if (operation.has("callbacks")) {
                    warnings.add(method.toUpperCase(Locale.ROOT) + " " + path
                            + ": callbacks are retained only in the source OpenAPI document");
                }
            });
        });
        return new EndpointDiscovery(endpoints, warnings);
    }

    private EndpointParameter parameter(JsonNode raw, JsonNode root) {
        JsonNode parameter = dereference(raw, root);
        String in = text(parameter, "in").toUpperCase(Locale.ROOT);
        ParameterLocation location = switch (in) {
            case "PATH" -> ParameterLocation.PATH;
            case "QUERY" -> ParameterLocation.QUERY;
            case "HEADER" -> ParameterLocation.HEADER;
            case "COOKIE" -> ParameterLocation.COOKIE;
            default -> ParameterLocation.UNANNOTATED;
        };
        JsonNode schema = dereference(parameter.path("schema"), root);
        List<String> validation = validations(schema);
        boolean required = location == ParameterLocation.PATH || parameter.path("required").asBoolean(false);
        return new EndpointParameter(
                text(parameter, "name"),
                location,
                schemaType(schema, root),
                required,
                schema.has("default") ? schema.path("default").asText() : "",
                validation,
                text(parameter, "description"));
    }

    private List<EndpointResponse> responses(JsonNode node, JsonNode root) {
        List<EndpointResponse> responses = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode response = dereference(entry.getValue(), root);
            JsonNode content = response.path("content");
            if (!content.isObject() || content.isEmpty()) {
                responses.add(new EndpointResponse(entry.getKey(), "", "", text(response, "description")));
                return;
            }
            content.fields().forEachRemaining(media -> responses.add(new EndpointResponse(
                    entry.getKey(),
                    schemaType(media.getValue().path("schema"), root),
                    media.getKey(),
                    text(response, "description"))));
        });
        return responses;
    }

    private String firstSchemaType(JsonNode content, JsonNode root) {
        if (!content.isObject()) {
            return "";
        }
        Iterator<JsonNode> values = content.elements();
        if (!values.hasNext()) {
            return "";
        }
        return schemaType(values.next().path("schema"), root);
    }

    private String schemaType(JsonNode schema, JsonNode root) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return "";
        }
        if (schema.has("$ref")) {
            return referenceName(schema.path("$ref").asText());
        }
        if (schema.has("oneOf") || schema.has("anyOf") || schema.has("allOf")) {
            String keyword = schema.has("oneOf") ? "oneOf" : schema.has("anyOf") ? "anyOf" : "allOf";
            return keyword + "<" + elements(schema.path(keyword)).stream()
                    .map(item -> schemaType(item, root))
                    .reduce((left, right) -> left + "," + right)
                    .orElse("") + ">";
        }
        String type = text(schema, "type");
        if (type.equals("array")) {
            return "array<" + schemaType(dereference(schema.path("items"), root), root) + ">";
        }
        return type.isEmpty() ? "object" : type + (schema.has("format") ? ":" + text(schema, "format") : "");
    }

    private List<String> validations(JsonNode schema) {
        List<String> result = new ArrayList<>();
        for (String key : List.of(
                "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "minLength", "maxLength",
                "minItems", "maxItems", "pattern", "format", "multipleOf")) {
            if (schema.has(key)) {
                result.add(key + "=" + schema.path(key).asText());
            }
        }
        if (schema.path("nullable").asBoolean(false)) {
            result.add("nullable=true");
        }
        return result;
    }

    private JsonNode dereference(JsonNode node, JsonNode root) {
        JsonNode current = node;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && current.has("$ref")) {
            String reference = current.path("$ref").asText();
            if (!reference.startsWith("#/") || !visited.add(reference)) {
                break;
            }
            JsonNode resolved = root;
            for (String segment : reference.substring(2).split("/")) {
                resolved = resolved.path(segment.replace("~1", "/").replace("~0", "~"));
            }
            if (resolved.isMissingNode()) {
                break;
            }
            current = resolved;
        }
        return current;
    }

    private String referenceName(String reference) {
        int slash = reference.lastIndexOf('/');
        return slash < 0 ? reference : reference.substring(slash + 1);
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> result = new LinkedHashSet<>();
        if (object.isObject()) {
            object.fieldNames().forEachRemaining(result::add);
        }
        return result;
    }

    private static List<JsonNode> elements(JsonNode array) {
        List<JsonNode> result = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(result::add);
        }
        return result;
    }

    private static List<String> textValues(JsonNode array) {
        List<String> result = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(value -> result.add(value.asText()));
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) && !node.path(field).isNull() ? node.path(field).asText() : "";
    }
}
