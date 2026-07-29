---
title: Architecture
order: 3
description: Core, Adapter, Capture, Normalize, Merge, Reconcile, and Render architecture
---
# Architecture

Mandala SbDP separates the framework-independent model, integration Core, input Adapters, Renderer, CLI, and Starter. External projects can replace only the Adapters they need.

The repository places product code in `platform/`, the analyzed application in `sample-app/`, the consumer-side analysis workspace in `mandala/`, official documentation in `site/`, and local verification infrastructure in `infra/local/`. Generated and sample-specific logic does not enter product modules.

## Modules

| Module | Responsibility |
|---|---|
| `mandala-model` | Immutable Node, Edge, Evidence, and Diff |
| `mandala-core` | Merge, incoming index, confidence, conflict, stale, and impact |
| `mandala-spring` | Source, Actuator Mapping, and OpenAPI |
| `mandala-doma` | DAO, method, external SQL, and PostgreSQL parser |
| `mandala-postgres` | JDBC, `information_schema`, and `pg_catalog` |
| `mandala-opentelemetry` | OTLP trace normalization and masking |
| `mandala-renderer` | Static HTML, ER, CRUD, custom sections, and search |
| `mandala-cli` | Refresh lifecycle, serve, verify, and diff |
| `mandala-spring-boot-starter` | Service and DAO Span completion |
| `mandala-gradle-plugin` | Gradle task integration |

## Pipeline

```text
Capture / Source scan / Introspection
                 ↓
Adapter-specific records
                 ↓ Normalize
Documentation Graph fragments
                 ↓ Merge
evidence-aware canonical Graph
                 ↓ Reconcile
conflict · stale · unconnected · diff
                 ↓ Render
static pages · reverse links · search
```

UI Capture does not connect to the database, and Runtime Capture is separated from the browser. `HTTP Method + normalized path` is the join key used to merge independently timed Graphs.

## Full and Incremental

Full Refresh reacquires every source. Incremental uses Git paths to select affected Adapters and reuses cache entries only when commit, configuration hash, and adapter version match. Migration, configuration, or schema-version changes fall back to Full when safety cannot be proven.

## Serialization contract

Graph JSON is canonicalized by stable ID. Generation time and map order are excluded from semantic diffs. The schema version is stored at the root, and readers accept unknown fields within the same major version.

The [successful project creation flow](sample-ref:flow:project.create.success) shows the rendered projection. GitHub Pages publishes official documentation under `docs/` and this generated output under `sample/`.
