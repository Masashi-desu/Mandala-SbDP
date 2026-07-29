---
title: Continuous refresh
order: 12
description: Full/Incremental Refresh, cache, semantic diff, stale, conflict, and CI operations
---
# Continuous refresh

## Full Refresh

Full reacquires frontend source, Playwright, Spring Mapping, OpenAPI, Java/Javadoc, Doma/SQL, OpenTelemetry, PostgreSQL, and Custom HTML. Missing baselines, schema/configuration changes, adapter-version changes, and unavailable Git history also require Full.

## Incremental Refresh

Git paths classify invalidation: Java affects Spring/Doma symbols, SQL affects statements/DAO/Tables, migrations affect the whole database, frontend and Scenario changes affect UI, and Custom HTML affects referenced Stable IDs. Core impact analysis rerenders downstream E2E and incoming consumers.

## Cache and fallback

Cache keys contain content hash, configuration hash, adapter/version, schema version, and target commit. Unclassifiable changes, rename failures, migrations, configuration changes, and schema-major changes fall back to Full. With `fallbackToFull=false`, the command fails instead of producing a partial update.

## Semantic Diff

The previous Graph is stored in `mandala/cache/previous-graph.json`. Order and analysis timestamps are ignored; screen, endpoint, schema, SQL, CRUD, and impacted E2E semantic changes are reported.

## CI

Pull requests run Full Refresh, snapshot diff, Java/TypeScript/Playwright/integration tests, bidirectional links, secrets, and the Pages-ready bundle. Main publishes only `site/dist`, with landing pages at the root, official documentation under `docs/`, and the verified static Mandala under `sample/`. Raw Graphs, Traces, database snapshots, and local configuration are excluded.
