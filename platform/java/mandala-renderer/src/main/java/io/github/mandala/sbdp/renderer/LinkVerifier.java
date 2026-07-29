package io.github.mandala.sbdp.renderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class LinkVerifier {
    private static final Pattern REFERENCE = Pattern.compile("(?:href|src)=\"([^\"]+)\"");

    public List<String> verify(Path root) throws IOException {
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(root)) return List.of("Site directory does not exist: " + root);
        try (var files = Files.walk(root)) {
            for (Path html : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                String content = Files.readString(html, StandardCharsets.UTF_8);
                var matcher = REFERENCE.matcher(content);
                while (matcher.find()) {
                    String href = matcher.group(1);
                    if (href.isBlank() || href.startsWith("#") || href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:")) continue;
                    String pathPart = href.split("#", 2)[0].split("\\?", 2)[0];
                    Path target = html.getParent().resolve(pathPart).normalize();
                    if (!target.startsWith(root.normalize()) || !Files.exists(target)) errors.add(root.relativize(html) + " -> " + href);
                }
                if (content.contains("class=\"broken-ref\"")) errors.add(root.relativize(html) + " contains an unresolved stable-id reference");
            }
        }
        return List.copyOf(errors);
    }
}
