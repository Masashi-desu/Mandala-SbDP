package io.github.mandala.sbdp.cli.pipeline;

import io.github.mandala.sbdp.cli.MandalaCli;
import io.github.mandala.sbdp.cli.RepositoryContext;
import io.github.mandala.sbdp.cli.StaticFileServer;
import io.github.mandala.sbdp.cli.config.ConfigLoader;
import io.github.mandala.sbdp.cli.config.MandalaConfig;
import io.github.mandala.sbdp.core.AdapterRun;
import io.github.mandala.sbdp.core.ChangeCategory;
import io.github.mandala.sbdp.core.ChangeSet;
import io.github.mandala.sbdp.core.ChangedFile;
import io.github.mandala.sbdp.core.FileChangeType;
import io.github.mandala.sbdp.core.FileSystemCache;
import io.github.mandala.sbdp.core.GraphAdapter;
import io.github.mandala.sbdp.core.GraphDiffer;
import io.github.mandala.sbdp.core.GraphValidator;
import io.github.mandala.sbdp.core.RefreshContext;
import io.github.mandala.sbdp.core.RefreshEngine;
import io.github.mandala.sbdp.core.RefreshRequest;
import io.github.mandala.sbdp.core.RefreshResult;
import io.github.mandala.sbdp.model.Diff;
import io.github.mandala.sbdp.model.DocumentationGraph;
import io.github.mandala.sbdp.model.DocumentationGraphJson;
import io.github.mandala.sbdp.renderer.LinkVerifier;
import io.github.mandala.sbdp.renderer.RenderOptions;
import io.github.mandala.sbdp.renderer.SecretScanner;
import io.github.mandala.sbdp.renderer.StaticSiteRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** End-to-end orchestration shared by every CLI command and the Gradle plugin. */
public final class MandalaPipeline {
    private static final String DEFAULT_CONFIGURATION = """
            mandala:
              project:
                id: application
                name: Application Mandala
              source:
                java:
                  roots: [src/main/java]
                resources:
                  roots: [src/main/resources]
                frontend:
                  root: frontend
              spring:
                actuatorMappingsUrl: http://localhost:8080/actuator/mappings
                openApiUrl: http://localhost:8080/v3/api-docs
                mappingSnapshots: [mandala/snapshots/spring/mappings.json]
                openApiSnapshots: [mandala/snapshots/spring/openapi.json]
              doma:
                sqlRoots: [src/main/resources/META-INF]
              database:
                type: postgresql
                connection:
                  url: jdbc:postgresql://localhost:5432/application
                  usernameEnv: MANDALA_DB_USERNAME
                  passwordEnv: MANDALA_DB_PASSWORD
                schemas: [public]
                excludeTables: [flyway_schema_history]
                snapshot: mandala/snapshots/db/schema.json
              telemetry:
                traces: [mandala/traces/**/*.json]
                captureCommand: []
              playwright:
                baseUrl: http://localhost:5173
                scenarios: [frontend/scenarios/**/*.yaml]
                observations: mandala/snapshots/ui/**/*.json
                captureCommand: [npm, run, capture:ui]
              custom:
                root: mandala/custom
                allowJavaScript: false
              output:
                graph: mandala/generated/application/graph/mandala.json
                previousGraph: mandala/cache/previous-graph.json
                site: mandala/generated/application/site
                diff: mandala/generated/application/reports/diff.json
              refresh:
                mode: incremental
                fallbackToFull: true
                baseRef: HEAD~1
              security:
                maskKeys: [password, authorization, cookie, token, sessionId, email]
                excludedPaths: ['**/node_modules/**', '**/build/**', '**/.git/**']
            """;

    private final RepositoryContext repository;

    private MandalaPipeline(RepositoryContext repository) {
        this.repository = repository;
    }

    public static MandalaPipeline open(Path repositoryPath, Path configurationPath) throws Exception {
        Path root = repositoryPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Repository root does not exist: " + root);
        Path config = configurationPath.isAbsolute() ? configurationPath.normalize() : root.resolve(configurationPath).normalize();
        if (!config.toAbsolutePath().normalize().startsWith(root)) {
            throw new IOException("Mandala configuration must be inside the repository root: " + config);
        }
        MandalaConfig loaded = new ConfigLoader().load(config);
        Instant analyzedAt = analyzedAt();
        RepositoryContext repository = new RepositoryContext(root, config, loaded, git(root, "rev-parse", "HEAD"), analyzedAt);
        validateOutputPaths(repository);
        return new MandalaPipeline(repository);
    }

    public static int initialize(Path repositoryPath, Path configurationPath, boolean forceConfiguration) throws Exception {
        Path root = repositoryPath.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path config = configurationPath.isAbsolute() ? configurationPath.normalize() : root.resolve(configurationPath).normalize();
        if (Files.exists(config) && forceConfiguration) {
            String stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now());
            Files.copy(config, config.resolveSibling(config.getFileName() + ".bak-" + stamp), StandardCopyOption.COPY_ATTRIBUTES);
        }
        if (!Files.exists(config) || forceConfiguration) {
            Files.createDirectories(config.getParent());
            Files.writeString(config, DEFAULT_CONFIGURATION, StandardCharsets.UTF_8);
        }
        for (String directory : List.of("mandala/custom", "mandala/snapshots/ui", "mandala/snapshots/spring",
                "mandala/snapshots/db", "mandala/traces", "mandala/cache/fragments", "mandala/generated")) {
            Files.createDirectories(root.resolve(directory));
        }
        System.out.println("Mandala workspace initialized at " + root);
        return 0;
    }

    public void discover() throws Exception {
        DocumentationGraph graph = new SourceGraphAdapter(repository).analyze(context());
        System.out.printf("Discovered %d nodes and %d relationships%n", graph.nodes().size(), graph.edges().size());
    }

    public void captureUi(boolean execute) throws Exception {
        if (execute) runConfigured(repository.config().mandala.playwright.captureCommand, "Playwright capture");
        DocumentationGraph graph = new UiGraphAdapter(repository).analyze(context());
        System.out.printf("Imported %d Playwright nodes%n", graph.nodes().size());
    }

    public void captureRuntime(boolean execute) throws Exception {
        if (execute) runConfigured(repository.config().mandala.telemetry.captureCommand, "runtime capture");
        DocumentationGraph graph = new RuntimeGraphAdapter(repository).analyze(context());
        System.out.printf("Imported %d runtime nodes%n", graph.nodes().size());
    }

    public void analyzeDb(boolean connect) throws Exception {
        DocumentationGraph graph = new PostgresGraphAdapter(repository, connect).analyze(context());
        System.out.printf("Analyzed %d PostgreSQL nodes%n", graph.nodes().size());
    }

    public void reconcile() throws Exception {
        executeRefresh(io.github.mandala.sbdp.core.RefreshMode.FULL, false, false);
    }

    public void refresh(MandalaCli.RefreshMode mode, boolean externalCapture) throws Exception {
        executeRefresh(mode == MandalaCli.RefreshMode.FULL
                ? io.github.mandala.sbdp.core.RefreshMode.FULL
                : io.github.mandala.sbdp.core.RefreshMode.INCREMENTAL, externalCapture, true);
    }

    public void render() throws Exception {
        DocumentationGraph graph = readRequired(repository.resolve(repository.config().mandala.output.graph));
        Diff diff = readDiffIfPresent(repository.resolve(repository.config().mandala.output.diff));
        render(graph, diff);
    }

    public boolean verify(boolean strictReview) throws Exception {
        List<String> failures = new ArrayList<>();
        Path graphPath = repository.resolve(repository.config().mandala.output.graph);
        if (!Files.isRegularFile(graphPath)) {
            failures.add("Documentation Graph is missing: " + graphPath);
        } else {
            DocumentationGraph graph = DocumentationGraphJson.read(graphPath);
            var report = new GraphValidator().validate(graph);
            report.errors().forEach(issue -> failures.add("graph: " + issue.message()));
            if (strictReview) {
                long conflicts = graph.nodes().stream().filter(node -> node.metadata().conflicted()).count();
                long stale = graph.nodes().stream().filter(node -> node.metadata().stale().stale()).count();
                if (conflicts > 0) failures.add(conflicts + " unresolved graph conflicts");
                if (stale > 0) failures.add(stale + " stale graph nodes");
            }
        }
        Path site = repository.resolve(repository.config().mandala.output.site);
        failures.addAll(new LinkVerifier().verify(site));
        SecretScanner scanner = new SecretScanner();
        Path diffPath = repository.resolve(repository.config().mandala.output.diff);
        for (Path root : List.of(graphPath, diffPath, site)) if (Files.exists(root)) {
            scanner.scanPortable(root).forEach(item -> failures.add("secret: " + item));
        }
        Path snapshots = repository.resolve("mandala/snapshots");
        if (Files.exists(snapshots)) scanner.scan(snapshots, repository.root(),
                repository.config().mandala.security.excludedPaths, true)
                .forEach(item -> failures.add("secret: " + item));
        Path traces = repository.resolve("mandala/traces");
        if (Files.exists(traces)) scanner.scan(traces, repository.root(),
                repository.config().mandala.security.excludedPaths, false)
                .forEach(item -> failures.add("secret: " + item));
        if (failures.isEmpty()) {
            System.out.println("Mandala verification succeeded");
            return true;
        }
        failures.forEach(item -> System.err.println("verification error: " + item));
        return false;
    }

    public boolean diff() throws Exception {
        Path currentPath = repository.resolve(repository.config().mandala.output.graph);
        Path previousPath = repository.resolve(repository.config().mandala.output.previousGraph);
        DocumentationGraph current = readRequired(currentPath);
        DocumentationGraph previous = Files.isRegularFile(previousPath)
                ? DocumentationGraphJson.read(previousPath)
                : DocumentationGraph.empty(current.projectId());
        Diff diff = new GraphDiffer().diff(previous, current);
        writeDiff(diff);
        System.out.printf("Diff: +%d/-%d/~%d nodes, +%d/-%d/~%d edges%n",
                diff.addedNodes().size(), diff.removedNodes().size(), diff.modifiedNodes().size(),
                diff.addedEdges().size(), diff.removedEdges().size(), diff.modifiedEdges().size());
        return !diff.isEmpty();
    }

    public void serve(Path publishedRoot, String bind, int port) throws Exception {
        String configuredOrExplicitRoot = publishedRoot == null
                ? repository.config().mandala.output.site
                : publishedRoot.toString();
        new StaticFileServer().serve(repository.resolve(configuredOrExplicitRoot), bind, port);
    }

    private void executeRefresh(io.github.mandala.sbdp.core.RefreshMode mode, boolean externalCapture,
                                boolean renderAndVerify) throws Exception {
        if (externalCapture) {
            runConfigured(repository.config().mandala.playwright.captureCommand, "Playwright capture");
            runConfigured(repository.config().mandala.telemetry.captureCommand, "runtime capture");
        }
        boolean connectDatabase = externalCapture;
        List<GraphAdapter> adapters = List.of(
                new SourceGraphAdapter(repository),
                new UiGraphAdapter(repository),
                new RuntimeGraphAdapter(repository),
                new PostgresGraphAdapter(repository, connectDatabase),
                new CustomGraphAdapter(repository),
                new ConnectionGraphAdapter(repository));
        Path currentPath = repository.resolve(repository.config().mandala.output.graph);
        DocumentationGraph previous = Files.isRegularFile(currentPath) ? DocumentationGraphJson.read(currentPath) : null;
        ChangeSet changes = mode == io.github.mandala.sbdp.core.RefreshMode.INCREMENTAL
                ? changes(externalCapture) : ChangeSet.empty();
        RefreshRequest request = new RefreshRequest(repository.config().mandala.project.id, repository.commit(),
                configurationHash(), repository.root(), mode, changes,
                repository.config().mandala.refresh.fallbackToFull, previous,
                Map.of("configuration", repository.root().relativize(repository.configPath()).toString().replace('\\', '/')));
        Clock refreshClock = Clock.fixed(repository.analyzedAt(), ZoneOffset.UTC);
        RefreshResult result = new RefreshEngine(adapters,
                new FileSystemCache(repository.resolve("mandala/cache/core"), refreshClock), refreshClock)
                .refresh(request);
        if (previous != null) {
            Path previousPath = repository.resolve(repository.config().mandala.output.previousGraph);
            Files.createDirectories(previousPath.getParent());
            DocumentationGraphJson.write(previousPath, previous);
        }
        DocumentationGraphJson.write(currentPath, result.graph());
        writeDiff(result.diff());
        System.out.printf("Refresh %s → %s%s: %d nodes, %d edges%n",
                result.plan().requestedMode(), result.plan().executionMode(), result.plan().fallback() ? " (safe fallback)" : "",
                result.graph().nodes().size(), result.graph().edges().size());
        for (AdapterRun run : result.adapterRuns()) {
            System.out.printf("  %-16s %-12s %d ms%n", run.adapterName(), run.status(), run.duration().toMillis());
        }
        if (renderAndVerify) {
            render(result.graph(), result.diff());
            if (!verify(false)) throw new IllegalStateException("Mandala verification failed");
        }
    }

    private void render(DocumentationGraph graph, Diff diff) throws Exception {
        Path output = repository.resolve(repository.config().mandala.output.site);
        var result = new StaticSiteRenderer().render(graph, output,
                repository.resolve(repository.config().mandala.custom.root),
                new RenderOptions(repository.config().mandala.custom.allowJavaScript,
                        repository.config().mandala.project.name), diff, repository.root());
        if (!result.brokenLinks().isEmpty()) throw new IllegalStateException("Broken generated links: " + result.brokenLinks());
        System.out.printf("Rendered %d static pages at %s%n", result.pagesWritten(), output);
    }

    private RefreshContext context() {
        return new RefreshContext(repository.config().mandala.project.id, repository.commit(), configurationHash(),
                repository.root(), repository.analyzedAt(), Map.of("configuration", repository.configPath().toString()));
    }

    private ChangeSet changes(boolean externalCapture) {
        GitCommandResult diff = runGit(repository.root(), "diff", "--name-status", "--find-renames",
                repository.config().mandala.refresh.baseRef, "--");
        GitCommandResult untracked = runGit(repository.root(), "ls-files", "--others", "--exclude-standard", "--");
        return detectChanges(diff.succeeded(), diff.output(), untracked.succeeded(), untracked.output(),
                externalCapture);
    }

    /**
     * Converts Git state and newly collected external inputs into an incremental change set.
     * A failed diff or any untracked file invalidates the commit baseline and deliberately
     * requests the planner's safe Full Refresh fallback.
     */
    static ChangeSet detectChanges(boolean diffSucceeded, String diffOutput,
                                   boolean untrackedSucceeded, String untrackedOutput,
                                   boolean externalCapture) {
        List<ChangedFile> files = new ArrayList<>();
        boolean unsafeGitState = !diffSucceeded || !untrackedSucceeded
                || untrackedOutput != null && !untrackedOutput.isBlank();
        if (diffSucceeded && diffOutput != null) {
            for (String line : diffOutput.lines().filter(value -> !value.isBlank()).toList()) {
                String[] columns = line.split("\\t");
                if (columns.length < 2 || columns[0].isBlank()) {
                    unsafeGitState = true;
                    continue;
                }
                char status = columns[0].charAt(0);
                if (status == 'R' && columns.length >= 3) {
                    files.add(ChangedFile.renamed(columns[1], columns[2]));
                } else {
                    files.add(new ChangedFile(columns[1], "", switch (status) {
                        case 'A' -> FileChangeType.ADDED;
                        case 'D' -> FileChangeType.DELETED;
                        default -> FileChangeType.MODIFIED;
                    }, null));
                }
            }
        }
        if (unsafeGitState) {
            files.add(new ChangedFile("mandala/.refresh/unsafe-git-state", "",
                    FileChangeType.MODIFIED, ChangeCategory.UNSAFE_GIT_STATE));
        }
        if (externalCapture) {
            files.add(new ChangedFile("mandala/.refresh/ui-capture", "",
                    FileChangeType.MODIFIED, ChangeCategory.UI_CAPTURE));
            files.add(new ChangedFile("mandala/.refresh/runtime-capture", "",
                    FileChangeType.MODIFIED, ChangeCategory.RUNTIME_CAPTURE));
            files.add(new ChangedFile("mandala/.refresh/database-capture", "",
                    FileChangeType.MODIFIED, ChangeCategory.DATABASE_CAPTURE));
            files.add(new ChangedFile("mandala/.refresh/spring-capture", "",
                    FileChangeType.MODIFIED, ChangeCategory.SPRING_CAPTURE));
        }
        return new ChangeSet(files);
    }

    private void runConfigured(List<String> command, String label) throws Exception {
        if (command == null || command.isEmpty()) {
            throw new IllegalStateException("No command is configured for " + label);
        }
        System.out.println("Running " + label + ": " + String.join(" ", command));
        Process process = new ProcessBuilder(command).directory(repository.root().toFile()).inheritIO().start();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException(label + " exited with status " + exit);
    }

    private void writeDiff(Diff diff) throws IOException {
        Path target = repository.resolve(repository.config().mandala.output.diff);
        Files.createDirectories(target.getParent());
        DocumentationGraphJson.mapper().writerWithDefaultPrettyPrinter().writeValue(target.toFile(), diff);
    }

    private Diff readDiffIfPresent(Path path) {
        if (!Files.isRegularFile(path)) return emptyDiff();
        try { return DocumentationGraphJson.mapper().readValue(path.toFile(), Diff.class); }
        catch (Exception ignored) { return emptyDiff(); }
    }

    private DocumentationGraph readRequired(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Documentation Graph is missing: " + path);
        return DocumentationGraphJson.read(path);
    }

    private String configurationHash() {
        try {
            byte[] value = Files.readAllBytes(repository.configPath());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot hash configuration", error);
        }
    }

    private static Instant analyzedAt() {
        String fixed = System.getenv("MANDALA_ANALYZED_AT");
        return fixed == null || fixed.isBlank() ? Instant.now() : Instant.parse(fixed);
    }

    private static void validateOutputPaths(RepositoryContext repository) throws IOException {
        Path root = repository.root().toAbsolutePath().normalize();
        Path site = safeResolve(repository, repository.config().mandala.output.site, "mandala.output.site");
        if (site.equals(root)) throw new IOException("mandala.output.site must be a dedicated subdirectory");
        Path custom = safeResolve(repository, repository.config().mandala.custom.root, "mandala.custom.root");
        if (site.equals(custom) || custom.startsWith(site)) {
            throw new IOException("mandala.output.site must not contain mandala.custom.root");
        }
        List<String> sourceRoots = new ArrayList<>();
        sourceRoots.addAll(repository.config().mandala.source.java.roots);
        sourceRoots.addAll(repository.config().mandala.source.resources.roots);
        if (!repository.config().mandala.source.frontend.root.isBlank()) {
            sourceRoots.add(repository.config().mandala.source.frontend.root);
        }
        for (String configured : sourceRoots) {
            Path source = safeResolve(repository, configured, "source root");
            if (source.equals(site) || source.startsWith(site)) {
                throw new IOException("mandala.output.site must not contain a configured source root: " + configured);
            }
        }
        for (Map.Entry<String, String> entry : Map.of(
                "mandala.output.graph", repository.config().mandala.output.graph,
                "mandala.output.previousGraph", repository.config().mandala.output.previousGraph,
                "mandala.output.diff", repository.config().mandala.output.diff).entrySet()) {
            Path target = safeResolve(repository, entry.getValue(), entry.getKey());
            if (target.equals(root) || target.equals(custom) || target.startsWith(custom)) {
                throw new IOException(entry.getKey() + " must not overwrite repository or custom source content");
            }
            if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                throw new IOException(entry.getKey() + " must be a JSON file: " + entry.getValue());
            }
        }
    }

    private static Path safeResolve(RepositoryContext repository, String configured, String label) throws IOException {
        try { return repository.resolve(configured); }
        catch (IllegalArgumentException unsafe) { throw new IOException(label + " is unsafe: " + configured, unsafe); }
    }

    private static Diff emptyDiff() {
        return new Diff("", "", Instant.EPOCH, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Set.of());
    }

    private static String git(Path root, String... arguments) {
        GitCommandResult result = runGit(root, arguments);
        return result.succeeded() ? result.output() : "unknown";
    }

    private static GitCommandResult runGit(Path root, String... arguments) {
        try {
            List<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return new GitCommandResult(process.waitFor(), output);
        } catch (Exception ignored) {
            return new GitCommandResult(-1, "");
        }
    }

    private record GitCommandResult(int exitCode, String output) {
        boolean succeeded() { return exitCode == 0; }
    }
}
