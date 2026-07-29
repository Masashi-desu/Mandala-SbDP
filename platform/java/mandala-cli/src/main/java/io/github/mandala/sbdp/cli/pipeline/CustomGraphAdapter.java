package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.model.Confidence;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;
import io.github.mandala.sbdp.model.Edge;
import io.github.mandala.sbdp.model.EdgeType;
import io.github.mandala.sbdp.model.ElementMetadata;
import io.github.mandala.sbdp.model.Evidence;
import io.github.mandala.sbdp.model.Node;
import io.github.mandala.sbdp.model.NodeType;
import io.github.mandala.sbdp.model.ReviewState;
import io.github.mandala.sbdp.model.SourceLocation;
import io.github.mandala.sbdp.model.StableId;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CustomGraphAdapter extends AbstractProjectAdapter {
    private static final Pattern REF = Pattern.compile("(?is)<mandala-(?:endpoint|table|symbol)-ref\\s+id=[\"']([^\"']+)[\"']");
    private static final Pattern ASSERT = Pattern.compile("(?is)data-mandala-assert=[\"']([^\"']+)[\"']");

    CustomGraphAdapter(io.github.mandala.sbdp.cli.RepositoryContext repository) { super(repository, Set.of(ChangeCategory.CUSTOM_HTML)); }
    @Override public String name() { return "custom-html"; }

    @Override public DocumentationGraph analyze(RefreshContext context) throws Exception {
        Path root = repository.resolve(repository.config().mandala.custom.root); if (!Files.isDirectory(root)) return persist(DocumentationGraph.empty(context.projectId()));
        Map<StableId, Node> known = knownNodes(); List<Node> nodes = new ArrayList<>(); List<Edge> edges = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path directory : paths.filter(Files::isDirectory).toList()) {
                List<Path> html;
                try (var files = Files.list(directory)) { html = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html")).sorted().toList(); }
                if (html.isEmpty()) continue;
                String key = root.relativize(directory).toString().replace('\\', '/'); StableId customId = StableId.of("custom-html:" + key.replace(' ', '-'));
                String combined = html.stream().map(this::read).collect(Collectors.joining("\n")); Set<String> references = new LinkedHashSet<>(); REF.matcher(combined).results().forEach(match -> references.add(match.group(1)));
                Map<String, Map<String, Object>> assertions = new LinkedHashMap<>(); ASSERT.matcher(combined).results().forEach(match -> parseAssertion(match.group(1), assertions, references));
                List<Path> css; try (var files = Files.list(directory)) { css = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".css")).sorted().toList(); }
                List<String> sourcePaths = new ArrayList<>(); sourcePaths.addAll(html.stream().map(this::relative).toList()); sourcePaths.addAll(css.stream().map(this::relative).toList());
                ElementMetadata metadata = ElementMetadata.builder().evidence(List.of(Evidence.humanReviewed(sourcePaths.getFirst(), "Repository-owned custom documentation"))).sourceLocations(sourcePaths.stream().map(SourceLocation::of).toList()).targetCommit(context.targetCommit()).analyzedAt(context.analyzedAt()).adapter(name()).confidence(Confidence.HUMAN_REVIEWED).reviewState(ReviewState.HUMAN_REVIEWED).build();
                Map<String, Object> attributes = GraphSupport.attributes(Map.of(), "directory", key, "htmlFiles", html.stream().map(path -> path.getFileName().toString()).toList(), "cssFiles", css.stream().map(path -> path.getFileName().toString()).toList(), "references", references, "assertions", assertions, "sourceFingerprint", GraphSupport.fingerprint(combined, css.stream().map(this::read).toList()));
                Node custom = Node.builder(customId, NodeType.CUSTOM_HTML_SECTION, "Custom · " + directory.getFileName()).description("Human-maintained custom HTML section").metadata(metadata).attributes(attributes).build(); nodes.add(custom);
                Node owner = owner(key, known.values()); if (owner != null) edges.add(GraphSupport.edge(EdgeType.DOCUMENTED_BY, owner.id(), customId, metadata));
                for (String reference : references) try { StableId target = StableId.of(reference); if (known.containsKey(target)) edges.add(GraphSupport.edge(EdgeType.REFERENCES, customId, target, metadata)); } catch (IllegalArgumentException ignored) { }
            }
        }
        return persist(GraphSupport.graph(context.projectId(), context.targetCommit(), context.analyzedAt(), nodes, edges));
    }

    private Map<StableId, Node> knownNodes() {
        Map<StableId, Node> nodes = new LinkedHashMap<>(); Path fragments = repository.resolve("mandala/cache/fragments"); if (!Files.isDirectory(fragments)) return nodes;
        try (var files = Files.list(fragments)) { for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) try { DocumentationGraphJson.read(file).nodes().forEach(node -> nodes.put(node.id(), node)); } catch (Exception ignored) { } } catch (Exception ignored) { }
        return nodes;
    }

    private Node owner(String directory, Collection<Node> nodes) {
        String[] parts = directory.split("/"); if (parts.length < 2) return null; String category = parts[0]; String key = parts[1];
        return nodes.stream().filter(node -> switch (category) { case "entries" -> node.type() == NodeType.E2E_FLOW || node.type() == NodeType.SCREEN; case "endpoints" -> node.type() == NodeType.HTTP_ENDPOINT; case "symbols" -> Set.of(NodeType.JAVA_CLASS, NodeType.JAVA_METHOD, NodeType.APPLICATION_SERVICE, NodeType.DOMA_DAO, NodeType.DOMA_DAO_METHOD).contains(node.type()); case "tables" -> node.type() == NodeType.DB_TABLE || node.type() == NodeType.DB_COLUMN; default -> false; }).filter(node -> customKey(node).equals(key)).findFirst().orElse(null);
    }

    private String customKey(Node node) { String value = node.id().localPart().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase(Locale.ROOT); return value; }
    private void parseAssertion(String expression, Map<String, Map<String, Object>> assertions, Set<String> refs) { String[] targetAndClaim = expression.split("\\|", 2); if (targetAndClaim.length != 2) return; String[] fieldValue = targetAndClaim[1].split("=", 2); if (fieldValue.length != 2) return; refs.add(targetAndClaim[0]); assertions.computeIfAbsent(targetAndClaim[0], ignored -> new LinkedHashMap<>()).put(fieldValue[0], fieldValue[1]); }
    private String read(Path path) { try { return Files.readString(path, StandardCharsets.UTF_8); } catch (Exception error) { throw new IllegalStateException("Cannot read " + path, error); } }
    private String relative(Path path) { return repository.root().relativize(path).toString().replace('\\', '/'); }
}
