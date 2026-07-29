package io.github.mandala.sbdp.core;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record ChangedFile(String path, String previousPath, FileChangeType type, ChangeCategory category)
        implements Comparable<ChangedFile> {
    public ChangedFile {
        path = normalize(Objects.requireNonNull(path, "path"));
        previousPath = normalize(Objects.requireNonNullElse(previousPath, ""));
        type = type == null ? FileChangeType.MODIFIED : type;
        category = category == null ? classify(path) : category;
        if (!isRepositoryRelative(path)) {
            throw new IllegalArgumentException("Changed file must be a repository-relative path");
        }
        if (!previousPath.isBlank() && !isRepositoryRelative(previousPath)) {
            throw new IllegalArgumentException("Previous file must be a repository-relative path");
        }
        if (type == FileChangeType.RENAMED && previousPath.isBlank()) {
            throw new IllegalArgumentException("A renamed file requires its previous path");
        }
    }

    public ChangedFile(String path, FileChangeType type, ChangeCategory category) {
        this(path, "", type, category);
    }

    public static ChangedFile of(String path) {
        return new ChangedFile(path, "", FileChangeType.MODIFIED, null);
    }

    public static ChangedFile renamed(String previousPath, String path) {
        return new ChangedFile(path, previousPath, FileChangeType.RENAMED, null);
    }

    public ChangeCategory previousCategory() {
        return previousPath.isBlank() ? category : classify(previousPath);
    }

    public static ChangeCategory classify(String path) {
        String lower = normalize(path).toLowerCase(Locale.ROOT);
        if (lower.startsWith("mandala/snapshots/ui/")) return ChangeCategory.UI_CAPTURE;
        if (lower.startsWith("mandala/traces/")) return ChangeCategory.RUNTIME_CAPTURE;
        if (lower.startsWith("mandala/snapshots/db/")) return ChangeCategory.DATABASE_CAPTURE;
        if (lower.startsWith("mandala/snapshots/runtime/")
                || lower.startsWith("mandala/snapshots/spring/")) return ChangeCategory.SPRING_CAPTURE;
        if (lower.equals("mandala.yml") || lower.equals("mandala.yaml") || lower.endsWith("/mandala.yml")
                || lower.endsWith("/mandala.yaml") || lower.endsWith("build.gradle.kts")
                || lower.endsWith("settings.gradle.kts") || lower.equals("gradle/libs.versions.toml")) {
            return ChangeCategory.CONFIGURATION;
        }
        if (lower.contains("/db/migration/") || lower.contains("/migrations/")) return ChangeCategory.MIGRATION;
        if (lower.endsWith(".sql")) return ChangeCategory.SQL;
        if (lower.endsWith(".java")) return ChangeCategory.JAVA;
        if (lower.endsWith("openapi.json") || lower.endsWith("openapi.yaml") || lower.endsWith("openapi.yml")) {
            return ChangeCategory.OPENAPI;
        }
        if (lower.contains("mandala/custom/") && (lower.endsWith(".html") || lower.endsWith(".css"))) {
            return ChangeCategory.CUSTOM_HTML;
        }
        if (lower.contains("scenario") && (lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".spec.ts") || lower.endsWith(".test.ts"))) return ChangeCategory.PLAYWRIGHT_SCENARIO;
        if (lower.contains("fixture") || lower.contains("__mocks__")) return ChangeCategory.FIXTURE;
        if (lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".js") || lower.endsWith(".jsx")
                || lower.endsWith(".vue") || lower.endsWith(".svelte") || lower.endsWith(".css")
                || lower.endsWith(".html")) return ChangeCategory.FRONTEND;
        return ChangeCategory.UNKNOWN;
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNull(value, "path").strip().replace('\\', '/');
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        if (normalized.isBlank()) return "";
        return Path.of(normalized).normalize().toString().replace('\\', '/');
    }

    private static boolean isRepositoryRelative(String path) {
        return !path.isBlank()
                && !path.startsWith("/")
                && !path.matches("^[A-Za-z]:/.*")
                && !Path.of(path).isAbsolute()
                && !path.equals("..")
                && !path.startsWith("../");
    }

    @Override
    public int compareTo(ChangedFile other) {
        int byPath = path.compareTo(other.path);
        if (byPath != 0) return byPath;
        int byPrevious = previousPath.compareTo(other.previousPath);
        if (byPrevious != 0) return byPrevious;
        int byType = type.compareTo(other.type);
        return byType != 0 ? byType : category.compareTo(other.category);
    }
}
