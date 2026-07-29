package io.github.mandala.sbdp.renderer;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class SecretScanner {
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)authorization[\\s\"':=]+bearer\\s+[a-z0-9._-]{12,}"),
            Pattern.compile("(?im)(?:^|[,{]\\s*)[\"']?(?:password|passwd|db_password)[\"']?\\s*[:=]\\s*[\"']?(?!\\[REDACTED\\])[^\\s<\"',}]{4,}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
    );
    private static final List<Pattern> LOCAL_VALUE_PATTERNS = List.of(
            Pattern.compile("(?:/Users/|/home/)[A-Za-z0-9._-]+(?:/|\\b)"),
            Pattern.compile("(?i)[A-Z]:\\\\Users\\\\[A-Za-z0-9._-]+(?:\\\\|\\b)"),
            Pattern.compile("(?i)\\b[a-z0-9._-]*(?:macbook|desktop|laptop)[a-z0-9._-]*\\.local\\b")
    );
    private static final int SCAN_CHUNK_CHARS = 64 * 1024;
    private static final int SCAN_OVERLAP_CHARS = 4096;

    public List<String> scan(Path root) throws IOException {
        return scan(root, root, List.of(), false);
    }

    /** Scans publishable artifacts for secrets and machine-specific paths/host names. */
    public List<String> scanPortable(Path root) throws IOException {
        return scan(root, root, List.of(), true);
    }

    /** Applies repository-relative configured exclusions to snapshot/raw-input trees. */
    public List<String> scan(Path root, Path repositoryRoot, List<String> excludedPaths,
                             boolean requirePortableValues) throws IOException {
        List<String> findings = new ArrayList<>();
        if (!Files.exists(root)) return findings;
        List<PathMatcher> exclusions = exclusionMatchers(excludedPaths);
        Path normalizedRepository = repositoryRoot.toAbsolutePath().normalize();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> !excluded(path, normalizedRepository, exclusions)).toList()) {
                scanFile(file, root, requirePortableValues, findings);
            }
        }
        return List.copyOf(findings);
    }

    private static void scanFile(Path file, Path root, boolean requirePortableValues,
                                 List<String> findings) throws IOException {
        Set<Pattern> secretMatches = new LinkedHashSet<>();
        Set<Pattern> localMatches = new LinkedHashSet<>();
        char[] buffer = new char[SCAN_CHUNK_CHARS];
        String overlap = "";
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (read == 0) continue;
                String content = overlap + new String(buffer, 0, read);
                for (Pattern pattern : PATTERNS) {
                    if (!secretMatches.contains(pattern) && pattern.matcher(content).find()) secretMatches.add(pattern);
                }
                if (requirePortableValues) for (Pattern pattern : LOCAL_VALUE_PATTERNS) {
                    if (!localMatches.contains(pattern) && pattern.matcher(content).find()) localMatches.add(pattern);
                }
                overlap = content.substring(Math.max(0, content.length() - SCAN_OVERLAP_CHARS));
            }
        } catch (java.nio.charset.CharacterCodingException binary) {
            return;
        }
        Path relative = root.equals(file) ? file.getFileName() : root.relativize(file);
        secretMatches.forEach(pattern -> findings.add(relative + " matches " + pattern.pattern()));
        localMatches.forEach(pattern -> findings.add(relative + " contains a machine-specific value"));
    }

    private static boolean excluded(Path file, Path repositoryRoot, List<PathMatcher> matchers) {
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(repositoryRoot)) return false;
        Path relative = repositoryRoot.relativize(absolute);
        return matchers.stream().anyMatch(matcher -> matcher.matches(relative));
    }

    private static List<PathMatcher> exclusionMatchers(List<String> patterns) {
        Set<String> variants = new LinkedHashSet<>();
        for (String pattern : patterns == null ? List.<String>of() : patterns) {
            String normalized = pattern.replace('\\', '/');
            variants.add(normalized);
            boolean added;
            do {
                added = false;
                for (String variant : List.copyOf(variants)) {
                    int marker = variant.indexOf("**/");
                    if (marker >= 0 && variants.add(variant.substring(0, marker) + variant.substring(marker + 3))) added = true;
                }
            } while (added);
        }
        return variants.stream().map(value -> FileSystems.getDefault().getPathMatcher("glob:" + value)).toList();
    }

}
