---
title: Configuration reference
order: 16
description: Required mandala.yml values, defaults, environment variables, exclusions, masking, and cache settings
---
# Configuration reference

## Project and source

`mandala.project.id` is required and defines the Stable ID namespace and output metadata. `name` is the display name. `source.java.roots` and `source.resources.roots` accept multiple entries. `source.frontend.root` is the TypeScript route-discovery starting point. Paths are relative to the repository root, not the configuration file.

## Spring and Doma

`spring.actuatorMappingsUrl` and `spring.openApiUrl` identify runtime capture endpoints. In offline mode, provide `mappingSnapshots` and `openApiSnapshots` globs. `doma.sqlRoots` lists resource directories that contain `META-INF`.

## Database

```yaml
database:
  type: postgresql
  connection:
    url: jdbc:postgresql://localhost:5432/app
    usernameEnv: MANDALA_DB_USERNAME
    passwordEnv: MANDALA_DB_PASSWORD
  schemas: [public]
  excludeTables: [flyway_schema_history]
  snapshot: mandala/snapshots/db/schema.json
```

Store environment-variable names, not secret values. `schemas` defaults to `public`. Exclusions are evaluated after selecting the schema.

## Telemetry and Playwright

`telemetry.traces` is an OTLP JSON glob and `captureCommand` is an argument array. Playwright configuration identifies analysis inputs, outputs, and connection targets.

```yaml
source:
  frontend:
    root: frontend/src
playwright:
  baseUrl: http://127.0.0.1:5173
  scenarios: [scenarios/**/*.yaml]
  observations: mandala/snapshots/ui/**/*.json
  screenshots: mandala/generated/example/screenshots
  webServer:
    command: npm run dev --workspace frontend
    url: http://127.0.0.1:5173/health
    reuseExistingServer: true
    timeoutMs: 120000
  captureCommand: [npm, run, capture:ui]
```

`webServer` is optional. Omit it when an external script or CI job starts the server. When configured, `reuseExistingServer: true` safely reuses an existing server. Use argument arrays instead of a shell command string for fields such as `captureCommand` to avoid quoting injection.

The capture runner accepts `MANDALA_CAPTURE_REPOSITORY_ROOT`, `MANDALA_CAPTURE_CONFIG`, `MANDALA_CAPTURE_FRONTEND_ROOT`, `MANDALA_CAPTURE_SCENARIOS`, `MANDALA_CAPTURE_OBSERVATIONS`, `MANDALA_CAPTURE_SCREENSHOTS`, `MANDALA_CAPTURE_BASE_URL`, `MANDALA_CAPTURE_WEB_SERVER_ENABLED`, `MANDALA_CAPTURE_WEB_SERVER_COMMAND`, `MANDALA_CAPTURE_WEB_SERVER_URL`, `MANDALA_CAPTURE_REUSE_EXISTING_SERVER`, and `MANDALA_CAPTURE_WEB_SERVER_TIMEOUT`. Corresponding CLI options are `--repository-root`, `--config`, `--frontend-root`, repeatable `--scenario`, `--observations`, `--screenshots`, `--base-url`, `--web-server-command`, `--web-server-url`, `--web-server-timeout`, `--[no-]reuse-existing-server`, and `--no-web-server`. Precedence is CLI options, environment variables, then `mandala.yml`.

## Custom content and output

`custom.root` is the source of truth for free-form content. `allowJavaScript` defaults to false. `output.graph` and `output.site` are required; `previousGraph` and `diff` are cache or report destinations. Do not configure the official `site` directory as a sample output.

Generated Mandala sites use the official landing-page palette by default. To apply product branding, define both Light and Dark public tokens in `palette.css` directly under `custom.root`.

```css
:root {
  --mandala-light-accent: #3b5ccc;
  --mandala-dark-accent: #9fb3ff;
}
```

Supported prefixes are `--mandala-light-` and `--mandala-dark-`. The main suffixes are `page`, `paper`, `ink`, `body`, `muted`, `line`, `accent`, `accent-strong`, `gold`, `indigo`, `jade`, `danger`, `header-bg`, `raised`, `diagram-surface`, `diagram-box`, and `diagram-stroke`. `palette.css` accepts only `:root` declarations for these tokens and rejects remote imports or arbitrary selectors. All other Custom CSS remains automatically scoped to Custom HTML.

## Refresh and cache

`refresh.mode` defaults to `incremental`, `fallbackToFull` defaults to true, and `baseRef` defaults to `HEAD~1`. The cache manifest records the commit, configuration hash, and adapter version. Put the cache directory in CI cache only when it contains no secrets.

## Masking and exclusions

`security.maskKeys` is applied recursively and case-insensitively. Defaults include password, authorization, cookie, token, sessionId, and email. `excludedPaths` removes build output, `.git`, and dependency directories from scans and discovery.
