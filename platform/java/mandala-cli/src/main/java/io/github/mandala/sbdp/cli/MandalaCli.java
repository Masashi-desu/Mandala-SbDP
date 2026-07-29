package io.github.mandala.sbdp.cli;

import io.github.mandala.sbdp.cli.pipeline.MandalaPipeline;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "mandala", mixinStandardHelpOptions = true, version = "Mandala SbDP 0.1.0",
        description = "Continuously connects UI, Spring, Doma, SQL and PostgreSQL as a Documentation Graph.",
        subcommands = {MandalaCli.Init.class, MandalaCli.Discover.class, MandalaCli.CaptureUi.class,
                MandalaCli.CaptureRuntime.class, MandalaCli.AnalyzeDb.class, MandalaCli.Reconcile.class,
                MandalaCli.Refresh.class, MandalaCli.Render.class, MandalaCli.Verify.class,
                MandalaCli.DiffCommand.class, MandalaCli.Serve.class})
public final class MandalaCli implements Runnable {
    @Option(names = {"-c", "--config"}, defaultValue = "mandala/config/mandala.yml",
            description = "Configuration file (default: ${DEFAULT-VALUE})")
    Path config;

    @Option(names = {"-r", "--repository"}, defaultValue = ".",
            description = "Repository root (default: current directory)")
    Path repository;

    @Override public void run() { CommandLine.usage(this, System.out); }

    public static int execute(String... args) {
        return new CommandLine(new MandalaCli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    }

    public static void main(String[] args) { System.exit(execute(args)); }

    abstract static class PipelineCommand implements Callable<Integer> {
        @CommandLine.ParentCommand MandalaCli root;
        MandalaPipeline pipeline() throws Exception { return MandalaPipeline.open(root.repository, root.config); }
    }

    @Command(name = "init", mixinStandardHelpOptions = true, description = "Create configuration, custom-content and cache directories without overwriting user content.")
    static final class Init extends PipelineCommand {
        @Option(names = "--force-config", description = "Replace only the generated configuration template after backing it up.") boolean force;
        @Override public Integer call() throws Exception { return MandalaPipeline.initialize(root.repository, root.config, force); }
    }

    @Command(name = "discover", mixinStandardHelpOptions = true, description = "Discover frontend routes, client calls, Spring endpoints, Java symbols, Doma DAOs and SQL.")
    static final class Discover extends PipelineCommand {
        @Override public Integer call() throws Exception { pipeline().discover(); return 0; }
    }

    @Command(name = "capture-ui", mixinStandardHelpOptions = true, description = "Run deterministic Playwright capture with API interception, then import observations.")
    static final class CaptureUi extends PipelineCommand {
        @Option(names = "--import-only", description = "Do not invoke Playwright; import existing observations.") boolean importOnly;
        @Override public Integer call() throws Exception { pipeline().captureUi(!importOnly); return 0; }
    }

    @Command(name = "capture-runtime", mixinStandardHelpOptions = true, description = "Run configured API scenarios and import sanitized OpenTelemetry traces.")
    static final class CaptureRuntime extends PipelineCommand {
        @Option(names = "--import-only", description = "Do not invoke runtime scenario command; import existing traces.") boolean importOnly;
        @Override public Integer call() throws Exception { pipeline().captureRuntime(!importOnly); return 0; }
    }

    @Command(name = "analyze-db", mixinStandardHelpOptions = true, description = "Introspect PostgreSQL with JDBC, information_schema and pg_catalog and classify SQL CRUD.")
    static final class AnalyzeDb extends PipelineCommand {
        @Option(names = "--snapshot-only", description = "Use the saved schema snapshot instead of connecting to PostgreSQL.") boolean snapshotOnly;
        @Override public Integer call() throws Exception { pipeline().analyzeDb(!snapshotOnly); return 0; }
    }

    @Command(name = "reconcile", mixinStandardHelpOptions = true, description = "Merge sources and detect conflicts, stale content, unconnected boundaries and low confidence.")
    static final class Reconcile extends PipelineCommand {
        @Override public Integer call() throws Exception { pipeline().reconcile(); return 0; }
    }

    @Command(name = "refresh", mixinStandardHelpOptions = true, description = "Run discovery, capture/import, reconciliation, rendering and verification.")
    static final class Refresh extends PipelineCommand {
        @Option(names = "--mode", defaultValue = "INCREMENTAL", description = "FULL or INCREMENTAL (default: ${DEFAULT-VALUE})") RefreshMode mode;
        @Option(names = "--offline", description = "Do not start external capture commands or connect to the database.") boolean offline;
        @Override public Integer call() throws Exception { pipeline().refresh(mode, !offline); return 0; }
    }

    @Command(name = "render", mixinStandardHelpOptions = true, description = "Regenerate static HTML from the saved Documentation Graph while preserving custom HTML.")
    static final class Render extends PipelineCommand {
        @Override public Integer call() throws Exception { pipeline().render(); return 0; }
    }

    @Command(name = "verify", mixinStandardHelpOptions = true, description = "Validate graph integrity, bidirectional links, custom references and secret masking.")
    static final class Verify extends PipelineCommand {
        @Option(names = "--strict-review", description = "Fail when unresolved conflicts or stale nodes exist.") boolean strictReview;
        @Override public Integer call() throws Exception { return pipeline().verify(strictReview) ? 0 : 4; }
    }

    @Command(name = "diff", mixinStandardHelpOptions = true, description = "Compare current and previous semantic graphs, ignoring timestamps and ordering.")
    static final class DiffCommand extends PipelineCommand {
        @Option(names = "--fail-on-change", description = "Exit 4 when a meaningful change exists.") boolean failOnChange;
        @Override public Integer call() throws Exception { boolean changed = pipeline().diff(); return changed && failOnChange ? 4 : 0; }
    }

    @Command(name = "serve", mixinStandardHelpOptions = true, description = "Serve a generated static site or an explicitly selected published bundle.")
    static final class Serve extends PipelineCommand {
        @Option(names = {"-p", "--port"}, defaultValue = "4174", description = "Port (default: ${DEFAULT-VALUE})") int port;
        @Option(names = "--bind", defaultValue = "127.0.0.1", description = "Bind address (default: ${DEFAULT-VALUE})") String bind;
        @Option(names = "--root",
                description = "Repository-relative site root (default: configured mandala.output.site)") Path publishedRoot;
        @Override public Integer call() throws Exception { pipeline().serve(publishedRoot, bind, port); return 0; }
    }

    public enum RefreshMode { FULL, INCREMENTAL }
}
