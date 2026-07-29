package io.github.mandala.sbdp.cli;

import io.github.mandala.sbdp.cli.config.MandalaConfig;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record RepositoryContext(Path root, Path configPath, MandalaConfig config, String commit, Instant analyzedAt) {
    public Path resolve(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Repository-relative path is required");
        Path resolved = root.resolve(path).toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Configured path escapes repository root: " + path);
        }
        return resolved;
    }

    public List<Path> glob(String pattern) throws IOException {
        String normalized = pattern.replace('\\', '/');
        Set<String> variants = new LinkedHashSet<>(); variants.add(normalized);
        boolean added;
        do {
            added = false;
            for (String variant : List.copyOf(variants)) {
                int marker = variant.indexOf("**/");
                if (marker >= 0 && variants.add(variant.substring(0, marker) + variant.substring(marker + 3))) added = true;
            }
        } while (added);
        List<PathMatcher> matchers = variants.stream()
                .map(value -> FileSystems.getDefault().getPathMatcher("glob:" + value)).toList();
        List<Path> matches = new ArrayList<>();
        if (!Files.exists(root)) return matches;
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> matchers.stream().anyMatch(matcher -> matcher.matches(root.relativize(path))))
                    .forEach(matches::add);
        }
        matches.sort(Comparator.naturalOrder());
        return List.copyOf(matches);
    }
}
