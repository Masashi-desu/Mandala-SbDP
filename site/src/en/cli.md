---
title: CLI reference
order: 15
description: Every mandala command, major options, exit codes, and examples
---
# CLI reference

The common options are `--repository <path>` and `--config <path>`. The default configuration is `mandala/config/mandala.yml`.

## Commands

| Command | Operation |
|---|---|
| `mandala init` | Non-destructively create configuration, directories, and Custom HTML templates |
| `mandala discover` | Discover routes, client APIs, Spring, Java, Doma, and SQL |
| `mandala capture-ui` | Run Playwright and import UI observations |
| `mandala capture-runtime` | Run API scenarios and import OpenTelemetry traces |
| `mandala analyze-db` | Analyze the live PostgreSQL schema and SQL CRUD |
| `mandala reconcile` | Update Graph merge, confidence, conflicts, and stale state |
| `mandala refresh` | Run capture through render and verification |
| `mandala render` | Regenerate only HTML from a saved Graph |
| `mandala verify` | Verify the Graph, links, custom references, secrets, and generated state |
| `mandala diff` | Display a semantic diff between previous and current Graphs |
| `mandala serve` | Serve a static Mandala or a selected public bundle on localhost |

## Major options

`init --force-config` backs up an existing configuration before updating its template. `capture-ui --import-only` and `capture-runtime --import-only` do not start external commands. `analyze-db --snapshot-only` uses an existing snapshot without connecting to a database.

`refresh --mode full|incremental --offline` is supported. Offline mode rebuilds from sources and existing snapshots, observations, and traces. `verify --strict-review` also fails on unresolved conflicts or stale items. `diff --fail-on-change` exits with code 4 when a semantic change exists. `serve --bind 127.0.0.1 --port 4174` serves the configured `mandala.output.site` without public exposure. A repository-relative root such as `serve --root site/dist` serves the Pages-ready bundle.

## Exit codes

| Code | Meaning |
|---:|---|
| 0 | Success |
| 2 | Invalid usage or configuration |
| 3 | Analysis or I/O failure |
| 4 | Verification, strict review, or fail-on-change failure |
| 5 | External capture failure such as Playwright, runtime scenarios, or database access |

## Examples

```bash
./gradlew :mandala-cli:installDist
./platform/java/mandala-cli/build/install/mandala/bin/mandala --config mandala/config/mandala.yml refresh --mode full
./gradlew mandalaRefresh
./scripts/serve-mandala.sh --port 4174
```

The wrapper script regenerates `site/dist` and serves the landing page at `/`, Docs under `/docs/`, and the sample Mandala at `/sample/`. The CLI does not suppress errors; a fallback records its reason in standard output and the cache manifest.
