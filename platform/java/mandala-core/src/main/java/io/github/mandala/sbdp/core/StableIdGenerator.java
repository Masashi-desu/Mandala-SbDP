package io.github.mandala.sbdp.core;

import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.StableId;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Generates ids from semantic identities; source line numbers and transient trace ids are never inputs. */
public final class StableIdGenerator {
    private static final Pattern REPEATED_SLASH = Pattern.compile("/{2,}");

    public StableId screen(String route) {
        return StableId.of("screen:" + normalizePath(route));
    }

    public StableId screenState(String route, String state) {
        return StableId.of("screen-state:" + normalizePath(route) + ":" + normalizeKey(state));
    }

    public StableId endpoint(String httpMethod, String path) {
        String method = Objects.requireNonNull(httpMethod, "httpMethod").strip().toUpperCase(Locale.ROOT);
        if (!method.matches("[A-Z]+")) throw new IllegalArgumentException("Invalid HTTP method: " + httpMethod);
        return StableId.of("endpoint:" + method + ":" + normalizePath(path));
    }

    public StableId javaSymbol(String qualifiedClassName) {
        return javaSymbol(qualifiedClassName, "");
    }

    public StableId javaSymbol(String qualifiedClassName, String memberSignature) {
        String owner = normalizeJavaName(qualifiedClassName);
        String member = normalizeMemberSignature(memberSignature);
        return StableId.of("java:" + owner + (member.isBlank() ? "" : "#" + member));
    }

    public StableId dao(String qualifiedClassName, String methodSignature) {
        String owner = normalizeJavaName(qualifiedClassName);
        String member = normalizeMemberSignature(methodSignature);
        return StableId.of("dao:" + owner + (member.isBlank() ? "" : "#" + member));
    }

    public StableId sql(String resourcePath) {
        return StableId.of("sql:" + normalizeResourcePath(resourcePath));
    }

    public StableId table(String schema, String table) {
        return StableId.of("table:" + normalizeDbIdentifier(schema) + "." + normalizeDbIdentifier(table));
    }

    public StableId column(String schema, String table, String column) {
        return StableId.of("column:" + normalizeDbIdentifier(schema) + "." + normalizeDbIdentifier(table)
                + "." + normalizeDbIdentifier(column));
    }

    public StableId flow(String flowKey) {
        return StableId.of("flow:" + normalizeKey(flowKey));
    }

    public StableId custom(String namespace, String semanticKey) {
        String normalizedNamespace = Objects.requireNonNull(namespace, "namespace").strip().toLowerCase(Locale.ROOT);
        if (!normalizedNamespace.matches("[a-z][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Invalid id namespace: " + namespace);
        }
        return StableId.of(normalizedNamespace + ":" + normalizeKey(semanticKey));
    }

    public StableId edge(EdgeType type, StableId from, StableId to) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        String material = type.name() + "\u0000" + from.value() + "\u0000" + to.value();
        return StableId.of("edge:" + type.name().toLowerCase(Locale.ROOT) + ":" + digest(material));
    }

    public String normalizePath(String input) {
        String value = Objects.requireNonNull(input, "path").strip().replace('\\', '/');
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            int authorityEnd = value.indexOf('/', value.indexOf("://") + 3);
            value = authorityEnd < 0 ? "/" : value.substring(authorityEnd);
        }
        value = removeTopLevelQueryAndFragment(value);
        value = normalizeSpringVariables(value);
        value = REPEATED_SLASH.matcher("/" + value).replaceAll("/");
        if (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Paths used in stable ids must not contain whitespace");
        }
        return value;
    }

    private String normalizeJavaName(String name) {
        String normalized = Objects.requireNonNull(name, "qualifiedClassName").strip().replace('$', '.');
        if (normalized.isBlank() || java.util.Arrays.stream(normalized.split("\\.", -1))
                .anyMatch(segment -> !segment.matches("[A-Za-z_$][A-Za-z0-9_$]*"))) {
            throw new IllegalArgumentException("Invalid Java symbol: " + name);
        }
        return normalized;
    }

    private String normalizeResourcePath(String path) {
        String normalized = Objects.requireNonNull(path, "resourcePath").strip().replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")
                || Path.of(normalized).isAbsolute()) {
            throw new IllegalArgumentException("Resource path must be repository-relative: " + path);
        }
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        Path candidate = Path.of(normalized).normalize();
        if (candidate.toString().isBlank() || candidate.startsWith("..")) {
            throw new IllegalArgumentException("Resource path escapes its root: " + path);
        }
        return candidate.toString().replace('\\', '/');
    }

    private String removeTopLevelQueryAndFragment(String path) {
        int braceDepth = 0;
        for (int index = 0; index < path.length(); index++) {
            char current = path.charAt(index);
            if (current == '{') braceDepth++;
            else if (current == '}') {
                if (--braceDepth < 0) throw new IllegalArgumentException("Unbalanced path variable: " + path);
            } else if ((current == '?' || current == '#') && braceDepth == 0) {
                if (braceDepth != 0) throw new IllegalArgumentException("Unbalanced path variable: " + path);
                return path.substring(0, index);
            }
        }
        if (braceDepth != 0) throw new IllegalArgumentException("Unbalanced path variable: " + path);
        return path;
    }

    private String normalizeSpringVariables(String path) {
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < path.length();) {
            if (path.charAt(index) != '{') {
                normalized.append(path.charAt(index++));
                continue;
            }
            int start = ++index;
            int nested = 0;
            while (index < path.length()) {
                char current = path.charAt(index);
                if (current == '{') nested++;
                else if (current == '}') {
                    if (nested == 0) break;
                    nested--;
                }
                index++;
            }
            if (index >= path.length()) throw new IllegalArgumentException("Unbalanced path variable: " + path);
            String expression = path.substring(start, index);
            int separator = topLevelColon(expression);
            String variable = (separator < 0 ? expression : expression.substring(0, separator)).strip();
            if (!variable.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
                throw new IllegalArgumentException("Invalid path variable: " + expression);
            }
            normalized.append('{').append(variable).append('}');
            index++;
        }
        return normalized.toString();
    }

    private int topLevelColon(String expression) {
        int nested = 0;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (current == '{') nested++;
            else if (current == '}') nested--;
            else if (current == ':' && nested == 0) return index;
        }
        return -1;
    }

    private String normalizeMemberSignature(String signature) {
        String value = Objects.requireNonNullElse(signature, "").strip();
        if (value.isBlank()) return "";
        int open = value.indexOf('(');
        if (open < 0) return value.replaceAll("\\s+", "");
        int close = value.lastIndexOf(')');
        if (close < open || !value.substring(close + 1).isBlank()) {
            throw new IllegalArgumentException("Invalid Java member signature: " + signature);
        }
        String prefix = value.substring(0, open).replaceAll("\\s+", "");
        if (prefix.isBlank()) throw new IllegalArgumentException("Invalid Java member signature: " + signature);
        List<String> parameters = splitParameters(value.substring(open + 1, close));
        return prefix + "(" + parameters.stream().map(this::normalizeParameter).reduce((a, b) -> a + "," + b).orElse("") + ")";
    }

    private List<String> splitParameters(String parameters) {
        if (parameters.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        int genericDepth = 0;
        int annotationDepth = 0;
        int start = 0;
        for (int index = 0; index < parameters.length(); index++) {
            char current = parameters.charAt(index);
            if (current == '<') genericDepth++;
            else if (current == '>') genericDepth--;
            else if (current == '(') annotationDepth++;
            else if (current == ')') annotationDepth--;
            else if (current == ',' && genericDepth == 0 && annotationDepth == 0) {
                result.add(parameters.substring(start, index));
                start = index + 1;
            }
            if (genericDepth < 0 || annotationDepth < 0) {
                throw new IllegalArgumentException("Invalid Java parameter list: " + parameters);
            }
        }
        if (genericDepth != 0 || annotationDepth != 0) {
            throw new IllegalArgumentException("Invalid Java parameter list: " + parameters);
        }
        result.add(parameters.substring(start));
        return result;
    }

    private String normalizeParameter(String parameter) {
        String value = stripParameterDecorators(parameter.strip());
        if (value.isBlank()) throw new IllegalArgumentException("Blank Java parameter");
        java.util.regex.Matcher named = Pattern.compile("^(.*\\S)\\s+([A-Za-z_$][A-Za-z0-9_$]*)$").matcher(value);
        if (named.matches() && !named.group(1).matches(".*(?:\\?|\\bextends|\\bsuper)$")) value = named.group(1);
        return value.replaceAll("\\s+", "");
    }

    private String stripParameterDecorators(String parameter) {
        String value = parameter;
        boolean changed;
        do {
            changed = false;
            if (value.matches("^final(?:\\s+).*")) {
                value = value.replaceFirst("^final\\s+", "").stripLeading();
                changed = true;
            }
            if (value.startsWith("@")) {
                int index = 1;
                while (index < value.length() && (Character.isJavaIdentifierPart(value.charAt(index))
                        || value.charAt(index) == '.')) index++;
                if (index == 1) throw new IllegalArgumentException("Invalid parameter annotation: " + parameter);
                if (index < value.length() && value.charAt(index) == '(') {
                    int depth = 1;
                    boolean quoted = false;
                    char quote = 0;
                    for (index++; index < value.length() && depth > 0; index++) {
                        char current = value.charAt(index);
                        if (quoted) {
                            if (current == quote && value.charAt(index - 1) != '\\') quoted = false;
                        } else if (current == '\'' || current == '"') {
                            quoted = true;
                            quote = current;
                        } else if (current == '(') depth++;
                        else if (current == ')') depth--;
                    }
                    if (depth != 0) throw new IllegalArgumentException("Unbalanced parameter annotation: " + parameter);
                }
                value = value.substring(index).stripLeading();
                changed = true;
            }
        } while (changed);
        return value;
    }

    private String normalizeDbIdentifier(String value) {
        String identifier = Objects.requireNonNull(value, "database identifier").strip();
        boolean quoted = identifier.length() >= 2 && identifier.startsWith("\"") && identifier.endsWith("\"");
        if (quoted) identifier = identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"");
        if (identifier.isBlank()) throw new IllegalArgumentException("Invalid database identifier: " + value);
        return encodeComponent(quoted ? identifier : identifier.toLowerCase(Locale.ROOT), "A-Za-z0-9_$-");
    }

    private String normalizeKey(String value) {
        String normalized = Objects.requireNonNull(value, "semanticKey").strip().replace('\\', '/');
        if (normalized.isBlank()) throw new IllegalArgumentException("Semantic key must not be blank");
        return encodeComponent(normalized, "A-Za-z0-9_./{}#(),:@+-");
    }

    private String encodeComponent(String value, String safeCharacters) {
        StringBuilder encoded = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            if (character.matches("[" + safeCharacters + "]")) {
                encoded.append(character);
            } else {
                byte[] bytes = character.getBytes(StandardCharsets.UTF_8);
                for (byte current : bytes) encoded.append('%').append(String.format("%02X", current & 0xff));
            }
        });
        return encoded.toString();
    }

    static String digest(String material) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
