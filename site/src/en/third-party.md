---
title: Third-party components and media
order: 22
description: Licenses and distribution boundaries for direct dependencies, build tools, containers, and published media
---
# Third-party components and media

Mandala SbDP's Apache-2.0 license does not convert dependencies, tools, containers, or images to Apache-2.0. Every component retains its own license. Directly selected components and assets are recorded in the [third-party inventory](../../legal/THIRD_PARTY_NOTICES.txt).

## Direct Java product and sample dependencies

| Component | Version | Selected license | Use |
|---|---:|---|---|
| Spring Boot / Framework / Security | 3.5.3 / 6.2.8 / 6.5.1 | Apache-2.0 | Starter integration and sample runtime |
| Doma / Doma Spring Boot | 3.9.1 / 2.4.0 | Apache-2.0 | Doma analysis and sample persistence |
| Jackson | 2.19.0; 2.19.1 when resolved in the sample | Apache-2.0 | JSON, YAML, and Java time |
| JavaParser symbol solver | 3.27.0 | Apache-2.0 from Apache-2.0 OR LGPL-3.0-or-later | Java source analysis |
| JSqlParser | 5.3 | Apache-2.0 from Apache-2.0 OR LGPL-2.1-or-later | SQL analysis |
| picocli / SnakeYAML | 4.7.7 / 2.4 | Apache-2.0 | CLI and YAML |
| OpenTelemetry Java API / SDK / OTLP | 1.51.0 | Apache-2.0 | Trace instrumentation and export |
| PostgreSQL JDBC | 42.7.7 | BSD-2-Clause | PostgreSQL connectivity |
| Flyway | 11.8.2 | Apache-2.0 | Sample migrations |
| springdoc-openapi | 2.8.6 | Apache-2.0 | Sample OpenAPI |
| AspectJ Weaver | 1.9.24 | EPL-2.0 | Starter AOP |
| Jakarta Servlet API | 6.1.0 | EPL-2.0 from EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 | Compile-only boundary |
| JUnit / AssertJ | 5.12.2 / 3.27.3 | EPL-2.0 / Apache-2.0 | Test only |

`gradle/libs.versions.toml` and each `build.gradle.kts` are authoritative for declarations. Gradle dependency reports are authoritative for resolved transitive versions.

## Node.js, documentation, and UI capture

| Component | Version | License | Use |
|---|---:|---|---|
| Playwright Test | 1.61.0 | Apache-2.0 | UI discovery and capture |
| TypeScript | 6.0.3 | Apache-2.0 | Build and type checking |
| markdown-it | 14.3.0 | MIT | Official documentation generation |
| js-yaml / tsx | 5.2.2 / 4.23.1 | MIT | Capture configuration and script execution |
| Vite | 8.1.5 | MIT | Sample frontend |
| Vitest / jsdom | 4.1.10 / 29.1.1 | MIT | Test only |
| Direct `@types/*` packages | Pinned in `package-lock.json` | MIT | Build only |

Node.js packages are not copied into the Pages artifact. `package-lock.json` tracks the exact direct and transitive resolution.

## Downloaded tools and local containers

| Component | Version | Primary license | Distribution boundary |
|---|---:|---|---|
| Gradle | 8.14.3 | Apache-2.0 | Wrapper is included; distribution is downloaded |
| Docker Compose | 2.39.1 | Apache-2.0 | Downloaded locally only when the plugin is absent |
| OpenTelemetry Java agent | 2.16.0 | Apache-2.0 | Checksum-verified local download |
| Playwright Chromium | revision selected by Playwright 1.61.0 | BSD-3-Clause and bundled third-party notices | Local download |
| PostgreSQL container | 16.9-alpine | PostgreSQL License and licenses of Alpine packages | Local verification |
| Jaeger | 1.68.0 | Apache-2.0 and bundled third-party notices | Local verification |
| OpenTelemetry Collector Contrib | 0.128.0 | Apache-2.0 and bundled third-party notices | Local verification |
| Alpine helper | 3.22.0 | Licenses of contained packages | Local verification |

The repository and Pages artifact do not redistribute these downloads. Containers and browsers are aggregate distributions, so their embedded license and notice files are the final authority.

## Published image

| Work | Local asset | Rights | Transformation and display |
|---|---|---|---|
| *Chakrasamvara Mandala*, Nepal, ca. 1100, object 1995.233 | `site/assets/chakrasamvara-mandala.webp` | Public Domain / CC0-1.0 | The Met Open Access source image was resized, converted to WebP, and used as the landing-page background |

- [The Metropolitan Museum of Art object page](https://www.metmuseum.org/art/collection/search/38021)
- [The Met Open Access policy](https://www.metmuseum.org/policies/image-resources)

CC0 does not require credit, but the landing page and inventory retain the work title and object-page link for provenance. This citation follows The Met's preference and does not imply The Met's endorsement of Mandala SbDP.

The official site bundles no third-party font, icon pack, or remotely hosted JavaScript. The favicon and interface icons are project-authored.

## Update process

A pull request adding or updating a dependency or asset updates the version manifest, lockfile, [third-party inventory](../../legal/THIRD_PARTY_NOTICES.txt), and this page together. Before release, inspect transitive dependencies and the `LICENSE` / `NOTICE` files of every bundled archive. Include Mandala's `LICENSE`, `NOTICE`, and third-party inventory in source releases.
