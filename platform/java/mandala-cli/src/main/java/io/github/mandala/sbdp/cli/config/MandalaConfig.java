package io.github.mandala.sbdp.cli.config;

import java.util.ArrayList;
import java.util.List;

public final class MandalaConfig {
    public MandalaSection mandala = new MandalaSection();

    public static final class MandalaSection {
        public Project project = new Project();
        public Source source = new Source();
        public Spring spring = new Spring();
        public Doma doma = new Doma();
        public Database database = new Database();
        public Telemetry telemetry = new Telemetry();
        public Playwright playwright = new Playwright();
        public Custom custom = new Custom();
        public Output output = new Output();
        public Refresh refresh = new Refresh();
        public Security security = new Security();
    }

    public static final class Project { public String id = "application"; public String name = "Application Mandala"; }
    public static final class Source {
        public JavaSource java = new JavaSource(); public Resources resources = new Resources(); public Frontend frontend = new Frontend();
    }
    public static final class JavaSource { public List<String> roots = new ArrayList<>(); }
    public static final class Resources { public List<String> roots = new ArrayList<>(); }
    public static final class Frontend { public String root = ""; }
    public static final class Spring {
        public String actuatorMappingsUrl = ""; public String openApiUrl = "";
        public List<String> mappingSnapshots = new ArrayList<>(); public List<String> openApiSnapshots = new ArrayList<>();
    }
    public static final class Doma { public List<String> sqlRoots = new ArrayList<>(); }
    public static final class Database {
        public String type = "postgresql"; public Connection connection = new Connection(); public List<String> schemas = new ArrayList<>(List.of("public"));
        public List<String> excludeTables = new ArrayList<>(List.of("flyway_schema_history")); public String snapshot = "mandala/snapshots/db/schema.json";
    }
    public static final class Connection {
        public String url = ""; public String usernameEnv = "MANDALA_DB_USERNAME"; public String passwordEnv = "MANDALA_DB_PASSWORD";
    }
    public static final class Telemetry { public List<String> traces = new ArrayList<>(); public List<String> captureCommand = new ArrayList<>(); }
    public static final class Playwright {
        public String baseUrl = "http://localhost:5173"; public List<String> scenarios = new ArrayList<>();
        public String observations = "mandala/snapshots/ui/**/*.json";
        public String screenshots = "mandala/snapshots/screenshots";
        public PlaywrightOutput output = new PlaywrightOutput();
        public PlaywrightWebServer webServer = new PlaywrightWebServer();
        public List<String> captureCommand = new ArrayList<>(List.of("npm", "run", "capture:ui"));
    }
    public static final class PlaywrightOutput { public String observations = ""; public String screenshots = ""; }
    public static final class PlaywrightWebServer {
        public String command = ""; public String url = ""; public boolean reuseExistingServer = true; public long timeoutMs = 120_000;
    }
    public static final class Custom { public String root = "mandala/custom"; public boolean allowJavaScript = false; }
    public static final class Output {
        public String graph = "mandala/generated/mandala/graph/mandala.json";
        public String previousGraph = "mandala/cache/previous-graph.json";
        public String site = "mandala/generated/mandala/site";
        public String diff = "mandala/generated/mandala/reports/diff.json";
    }
    public static final class Refresh { public String mode = "incremental"; public boolean fallbackToFull = true; public String baseRef = "HEAD~1"; }
    public static final class Security {
        public List<String> maskKeys = new ArrayList<>(List.of("password", "authorization", "cookie", "token", "sessionId", "email"));
        public List<String> excludedPaths = new ArrayList<>(List.of("**/node_modules/**", "**/build/**", "**/.git/**"));
    }
}
