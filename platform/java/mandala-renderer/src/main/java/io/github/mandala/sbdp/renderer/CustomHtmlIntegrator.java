package io.github.mandala.sbdp.renderer;

import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.StableId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CustomHtmlIntegrator {
    private static final String PALETTE_STYLESHEET = "palette.css";
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b[^>]*>.*?</script\\s*>");
    private static final Pattern SCRIPT_TAG = Pattern.compile("(?is)</?script\\b[^>]*>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style\\b[^>]*>.*?</style\\s*>");
    private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("(?i)\\s+style\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?i)\\s+[a-z_:][-a-z0-9_:.]*\\s*=\\s*(?:\"\\s*javascript\\s*:[^\"]*\"|'\\s*javascript\\s*:[^']*'|javascript\\s*:[^\\s>]*)");
    private static final Pattern ACTIVE_CONTAINER = Pattern.compile("(?is)</?(?:iframe|object|embed|base|meta)\\b[^>]*>");
    private static final Pattern SRCDOC = Pattern.compile("(?i)\\s+srcdoc\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern DANGEROUS_MARKUP = Pattern.compile("(?is)<\\s*script\\b|<[^>]*(?:\\son[a-z]+\\s*=|\\ssrcdoc\\s*=|javascript\\s*:)");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("(?i)&#(?:x([0-9a-f]+)|([0-9]+));?");
    private static final Pattern REF = Pattern.compile("(?is)<mandala-(endpoint|table|symbol)-ref\\s+id=\"([^\"]+)\"\\s*(?:/>|>\\s*</mandala-\\1-ref>)");

    private final Path root;
    private final boolean allowJavaScript;
    private final Map<StableId, Node> nodes;

    CustomHtmlIntegrator(Path root, boolean allowJavaScript, DocumentationGraph graph) {
        this.root = root;
        this.allowJavaScript = allowJavaScript;
        this.nodes = graph.nodeMap();
    }

    String sectionsFor(Node node) throws IOException {
        if (root == null || !Files.isDirectory(root)) return "";
        Optional<Path> directory = candidateDirectories(node).filter(Files::isDirectory).findFirst();
        if (directory.isEmpty()) return "";
        try (var files = Files.list(directory.get())) {
            String sections = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> read(path, node)).collect(Collectors.joining("\n"));
            if (sections.isBlank()) return "";
            return "<section class=\"custom-section\"><div class=\"section-label\">CUSTOM HTML · TRUSTED REPOSITORY CONTENT</div>" + sections + "</section>";
        }
    }

    /** Collects repository-owned custom styles into one CSP-compatible external asset. */
    String stylesheet() throws IOException {
        if (root == null || !Files.isDirectory(root)) return "";
        Path palette = root.resolve(PALETTE_STYLESHEET);
        String paletteStylesheet = Files.isRegularFile(palette) ? readPaletteCss(palette) : "";
        try (var files = Files.walk(root)) {
            String scopedStylesheet = files.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(palette))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".css"))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .map(this::readCss)
                    .collect(Collectors.joining("\n"));
            return paletteStylesheet + scopedStylesheet;
        }
    }

    private java.util.stream.Stream<Path> candidateDirectories(Node node) {
        String category = switch (node.type()) {
            case E2E_FLOW, SCREEN, SCREEN_STATE, UI_ENTRY -> "entries";
            case HTTP_ENDPOINT, HTTP_CLIENT_CALL, OPENAPI_OPERATION -> "endpoints";
            case JAVA_CLASS, JAVA_METHOD, CONTROLLER, APPLICATION_SERVICE, DOMA_DAO, DOMA_DAO_METHOD -> "symbols";
            case DB_TABLE, DB_COLUMN, DB_SCHEMA, DB_VIEW, DB_MATERIALIZED_VIEW -> "tables";
            default -> PagePaths.directory(node.type());
        };
        String simple = node.id().value().substring(node.id().value().indexOf(':') + 1)
                .replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase(Locale.ROOT);
        return java.util.stream.Stream.of(root.resolve(category).resolve(simple), root.resolve(category).resolve(PagePaths.slug(node.id().value())));
    }

    private String read(Path path, Node node) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!allowJavaScript) {
                content = SCRIPT.matcher(content).replaceAll("");
                content = SCRIPT_TAG.matcher(content).replaceAll("");
                content = EVENT_HANDLER.matcher(content).replaceAll("");
                content = JAVASCRIPT_URL.matcher(content).replaceAll("");
                content = ACTIVE_CONTAINER.matcher(content).replaceAll("");
                content = SRCDOC.matcher(content).replaceAll("");
                String canonical = decodeEntities(content).replace("&colon;", ":").replace("&COLON;", ":");
                if (DANGEROUS_MARKUP.matcher(canonical).find()) {
                    throw new CustomReadException("Custom HTML contains active content while JavaScript is disabled: " + path, null);
                }
            }
            content = STYLE.matcher(content).replaceAll("");
            content = STYLE_ATTRIBUTE.matcher(content).replaceAll("");
            content = replaceReferences(content);
            return "<div class=\"custom-html\" data-source=\"" + Html.attribute(root.relativize(path)) + "\">" + content + "</div>";
        } catch (IOException error) {
            throw new CustomReadException("Cannot read custom HTML for " + node.id() + ": " + path, error);
        }
    }

    private String decodeEntities(String content) {
        Matcher matcher = NUMERIC_ENTITY.matcher(content);
        StringBuffer decoded = new StringBuffer();
        while (matcher.find()) {
            try {
                int codePoint = Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2),
                        matcher.group(1) != null ? 16 : 10);
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(Character.toString(codePoint)));
            } catch (RuntimeException invalid) {
                matcher.appendReplacement(decoded, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private String replaceReferences(String content) {
        Matcher matcher = REF.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            StableId id;
            try { id = StableId.of(matcher.group(2)); } catch (IllegalArgumentException invalid) { id = null; }
            Node target = id == null ? null : nodes.get(id);
            String replacement = target == null
                    ? "<span class=\"broken-ref\" data-stable-id=\"" + Html.attribute(matcher.group(2)) + "\">unresolved: " + Html.escape(matcher.group(2)) + "</span>"
                    : "<a class=\"stable-ref\" href=\"../" + PagePaths.forNode(target) + "\" data-stable-id=\"" + Html.attribute(target.id()) + "\">" + Html.escape(target.displayName()) + "</a>";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String readCss(Path path) {
        try {
            String css = Files.readString(path, StandardCharsets.UTF_8);
            if (css.toLowerCase(Locale.ROOT).contains("@import") || css.toLowerCase(Locale.ROOT).contains("javascript:")) {
                throw new CustomReadException("Custom CSS may not import remote content: " + path, null);
            }
            StringBuilder scoped = new StringBuilder();
            Matcher blocks = Pattern.compile("(?s)([^{}]+)\\{([^{}]*)}").matcher(css);
            while (blocks.find()) {
                String selectors = blocks.group(1).strip();
                if (selectors.startsWith("@")) continue;
                String prefixed = java.util.Arrays.stream(selectors.split(","))
                        .map(String::strip).filter(value -> !value.isBlank())
                        .map(value -> ".custom-section .custom-html " + value).collect(Collectors.joining(","));
                if (!prefixed.isBlank()) scoped.append(prefixed).append('{').append(blocks.group(2)).append("}\n");
            }
            return scoped.toString();
        } catch (IOException error) {
            throw new CustomReadException("Cannot read custom CSS: " + path, error);
        }
    }

    /**
     * Keeps global customization deliberately narrow: the root palette file may only
     * provide public Mandala color tokens. All other custom CSS remains scoped to the
     * custom-content container.
     */
    private String readPaletteCss(Path path) {
        try {
            String css = Files.readString(path, StandardCharsets.UTF_8);
            String withoutComments = css.replaceAll("(?s)/\\*.*?\\*/", "").strip();
            Matcher blocks = Pattern.compile("(?s):root\\s*\\{([^{}]*)}").matcher(withoutComments);
            StringBuilder sanitized = new StringBuilder();
            int cursor = 0;
            while (blocks.find()) {
                if (!withoutComments.substring(cursor, blocks.start()).isBlank()) {
                    throw invalidPalette(path);
                }
                StringBuilder declarations = new StringBuilder();
                for (String declaration : blocks.group(1).split(";")) {
                    String value = declaration.strip();
                    if (value.isBlank()) continue;
                    int separator = value.indexOf(':');
                    if (separator < 1) throw invalidPalette(path);
                    String property = value.substring(0, separator).strip();
                    String propertyValue = value.substring(separator + 1).strip();
                    String normalized = propertyValue.toLowerCase(Locale.ROOT);
                    if (!property.matches("--mandala-(?:light|dark)-[a-z0-9-]+")
                            || propertyValue.isBlank()
                            || normalized.contains("url(")
                            || normalized.contains("javascript:")
                            || normalized.contains("expression(")
                            || propertyValue.contains("@")) {
                        throw invalidPalette(path);
                    }
                    declarations.append(property).append(':').append(propertyValue).append(';');
                }
                if (!declarations.isEmpty()) sanitized.append(":root{").append(declarations).append("}\n");
                cursor = blocks.end();
            }
            if (cursor == 0 || !withoutComments.substring(cursor).isBlank()) throw invalidPalette(path);
            return sanitized.toString();
        } catch (IOException error) {
            throw new CustomReadException("Cannot read custom palette CSS: " + path, error);
        }
    }

    private CustomReadException invalidPalette(Path path) {
        return new CustomReadException("Custom palette may only contain :root declarations for "
                + "--mandala-light-* and --mandala-dark-* color tokens: " + path, null);
    }

    private static final class CustomReadException extends RuntimeException {
        CustomReadException(String message, Throwable cause) { super(message, cause); }
    }
}
