---
title: Contributing
order: 20
description: Development environment, module boundaries, conventions, tests, snapshots, adapters, pull requests, and releases
---
# Contributing

## Development environment

Install Java 21, Node 24, and Docker Engine, then run `./scripts/setup.sh`. Before changing code, read the root `GOAL.md`, `AGENTS.md`, and the tests for the affected module.

## Module responsibilities

Do not introduce framework types into `mandala-model` or direct dependencies between adapters. Core is responsible only for Evidence-aware integration. Product code must not hardcode sample-specific paths or tables. Never edit generated files directly.

## Coding conventions

Java favors immutable records and values, null-safe constructors, and repository-relative paths. TypeScript uses strict mode, validates unknown input, and masks secrets. Do not turn an error into an empty result or leave a major feature as TODO or pseudocode.

## Tests

```bash
./gradlew test
npm test
./scripts/verify.sh
```

Unit tests cover Stable IDs, merge, reverse indexes, confidence, conflict and stale state, diffs, adapters, SQL and CRUD, traces, Custom HTML, and links. PostgreSQL, Flyway, Doma, and Spring use integration tests; UI capture uses Playwright.

## Snapshot

Update Golden files only through the explicit `./scripts/update-snapshots.sh` command. Review the diff and do not auto-approve unintended changes. Also confirm that Custom HTML remains intact.

## Documentation locales and source fidelity

Every Markdown page in `site/src` requires a same-named English page in `site/src/en`; the build rejects missing or extra translations. Keep Stable IDs, commands, configuration keys, code, SQL, quoted source terms, and generated Graph data verbatim. Translate only explanatory prose and renderer-owned interface labels.

## Adding an adapter

Follow the [Extension guide](extension.md) contract and add fixtures, negative cases, capability documentation, and license information. Test Evidence and warnings for inputs the adapter cannot analyze.

## Pull request

The PR workflow verifies Java, TypeScript, Playwright, integration, Full Refresh, snapshots, links, secrets, and the Pages-ready bundle. Only `site/dist` is uploaded as a Pages artifact, and the public sample projection exists only at `site/dist/sample`.

## GitHub Pages (this repository)

The following rules apply to maintaining and publishing this repository's official documentation and verified sample. They are not requirements for projects that adopt Mandala.

Build the Pages artifact only from `site/dist`. Publish landing pages at the repository root and `en/`, official Docs at `docs/<document-path>` and `docs/en/<document-path>`, and the verified static sample at `sample/<generated-artifact-path>`. The workflow declares the artifact root explicitly and never uploads the whole repository.

## Release

Review schema compatibility, dependency licenses, NOTICE, the Gradle plugin, Starter and CLI distributions, and documentation versions. Never include local `.env`, raw traces, or a sample database volume in a release artifact.
