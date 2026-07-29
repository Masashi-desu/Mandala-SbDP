package io.github.mandala.sbdp.cli.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @TempDir Path root;

    @Test
    void loadsTrackedSampleConfiguration() throws Exception {
        Path current = Path.of("").toAbsolutePath();
        Path configPath = null;
        while (current != null) {
            Path candidate = current.resolve("mandala/config/mandala.yml");
            if (Files.isRegularFile(candidate)) {
                configPath = candidate;
                break;
            }
            current = current.getParent();
        }
        if (configPath == null) throw new AssertionError("Cannot locate tracked mandala/config/mandala.yml");

        MandalaConfig config = new ConfigLoader().load(configPath);

        assertEquals("sample-app/frontend/src", config.mandala.source.frontend.root);
        assertEquals("mandala/generated/sample-app/screenshots", config.mandala.playwright.screenshots);
        assertEquals("npm run dev --workspace @mandala/sample-frontend", config.mandala.playwright.webServer.command);
        assertTrue(config.mandala.playwright.webServer.reuseExistingServer);
    }

    @Test
    void loadsReusablePlaywrightCaptureSettingsWithoutUnknownProperties() throws Exception {
        Path configPath = root.resolve("mandala.yml");
        Files.writeString(configPath, """
                mandala:
                  project:
                    id: config-test
                  source:
                    frontend:
                      root: web/src
                  playwright:
                    baseUrl: http://127.0.0.1:4300
                    scenarios: [scenarios/**/*.yaml]
                    observations: mandala/snapshots/ui/**/*.json
                    screenshots: mandala/generated/config-test/screenshots
                    output:
                      observations: alternate/observations/**/*.json
                      screenshots: alternate/screenshots
                    webServer:
                      command: npm run dev
                      url: http://127.0.0.1:4300/ready
                      reuseExistingServer: true
                      timeoutMs: 90000
                  output:
                    graph: mandala/generated/config-test/graph/mandala.json
                    site: mandala/generated/config-test/site
                """);

        MandalaConfig config = new ConfigLoader().load(configPath);

        assertEquals("web/src", config.mandala.source.frontend.root);
        assertEquals("http://127.0.0.1:4300", config.mandala.playwright.baseUrl);
        assertEquals("alternate/observations/**/*.json", config.mandala.playwright.observations);
        assertEquals("alternate/screenshots", config.mandala.playwright.screenshots);
        assertEquals("alternate/observations/**/*.json", config.mandala.playwright.output.observations);
        assertEquals("alternate/screenshots", config.mandala.playwright.output.screenshots);
        assertEquals("npm run dev", config.mandala.playwright.webServer.command);
        assertEquals("http://127.0.0.1:4300/ready", config.mandala.playwright.webServer.url);
        assertTrue(config.mandala.playwright.webServer.reuseExistingServer);
        assertEquals(90_000, config.mandala.playwright.webServer.timeoutMs);
    }
}
