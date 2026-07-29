package io.github.mandala.sbdp.cli.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public MandalaConfig load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Mandala configuration does not exist: " + path);
        MandalaConfig config = mapper.readValue(path.toFile(), MandalaConfig.class);
        normalize(config);
        validate(config, path);
        return config;
    }

    private void normalize(MandalaConfig config) {
        if (config.mandala == null || config.mandala.playwright == null || config.mandala.playwright.output == null) return;
        MandalaConfig.Playwright playwright = config.mandala.playwright;
        if (!blank(playwright.output.observations)) playwright.observations = playwright.output.observations;
        if (!blank(playwright.output.screenshots)) playwright.screenshots = playwright.output.screenshots;
    }

    private void validate(MandalaConfig config, Path path) throws IOException {
        if (config.mandala == null) throw new IOException("Missing root 'mandala' section: " + path);
        if (config.mandala.project == null || blank(config.mandala.project.id)) throw new IOException("mandala.project.id is required");
        if (config.mandala.output == null || blank(config.mandala.output.graph) || blank(config.mandala.output.site)) throw new IOException("mandala.output.graph and mandala.output.site are required");
        if (config.mandala.database != null && !"postgresql".equalsIgnoreCase(config.mandala.database.type)) throw new IOException("Initial release supports database.type=postgresql only");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
