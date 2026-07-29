# Third-party component and asset inventory

This file records the direct third-party components, tools, container images,
and media assets intentionally used by Mandala SbDP. Mandala SbDP's
Apache-2.0 license does not replace or modify any license listed here.

The version catalogs, package manifests, lockfiles, container definitions, and
setup scripts are the authoritative sources for resolved versions. Transitive
dependencies retain their own licenses and notices. A release that bundles
third-party binaries must also retain the applicable license and NOTICE files
shipped by those binaries.

## JVM product and sample dependencies

| Component | Version | SPDX license | Use and distribution |
|---|---:|---|---|
| Spring Boot | 3.5.3 | Apache-2.0 | Sample runtime and Starter integration |
| Spring Framework | 6.2.8 | Apache-2.0 | Starter compile/runtime integration |
| Spring Security | 6.5.1 via Spring Boot BOM | Apache-2.0 | Sample authentication and authorization |
| Doma / Doma annotation processor | 3.9.1 | Apache-2.0 | Product adapter and sample persistence |
| Doma Spring Boot | 2.4.0 | Apache-2.0 | Sample integration |
| Jackson | 2.19.0; 2.19.1 in the sample runtime | Apache-2.0 | JSON, YAML, and Java time serialization |
| JavaParser symbol solver | 3.27.0 | Apache-2.0 OR LGPL-3.0-or-later; used under Apache-2.0 | Java source analysis |
| JSqlParser | 5.3 | Apache-2.0 OR LGPL-2.1-or-later; used under Apache-2.0 | SQL analysis |
| picocli | 4.7.7 | Apache-2.0 | CLI |
| SnakeYAML | 2.4 | Apache-2.0 | YAML parsing |
| OpenTelemetry Java API / SDK / OTLP exporter | 1.51.0 | Apache-2.0 | Starter and sample runtime observation |
| PostgreSQL JDBC Driver | 42.7.7 | BSD-2-Clause | PostgreSQL adapter and sample runtime |
| Flyway Core / PostgreSQL module | 11.8.2 | Apache-2.0 | Sample migrations |
| springdoc-openapi | 2.8.6 | Apache-2.0 | Sample OpenAPI endpoint |
| AspectJ Weaver | 1.9.24 | EPL-2.0 | Starter AOP runtime |
| Jakarta Servlet API | 6.1.0 | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0; used under EPL-2.0 | Starter compile-only servlet boundary |

## JVM test and build dependencies

| Component | Version | SPDX license | Use and distribution |
|---|---:|---|---|
| JUnit Jupiter / Platform | 5.12.2 | EPL-2.0 | Test only |
| AssertJ | 3.27.3 | Apache-2.0 | Test only |
| Gradle Wrapper / distribution | 8.14.3 | Apache-2.0 | Build tool; wrapper files are in the repository and the distribution is downloaded |

## Node.js workspace dependencies

| Component | Version | SPDX license | Use and distribution |
|---|---:|---|---|
| Playwright Test | 1.61.0 | Apache-2.0 | UI discovery and capture tooling |
| TypeScript | 6.0.3 | Apache-2.0 | Build and type checking |
| markdown-it | 14.3.0 | MIT | Official documentation rendering |
| js-yaml | 5.2.2 | MIT | Capture configuration |
| tsx | 4.23.1 | MIT | TypeScript script runner |
| Vite | 8.1.5 | MIT | Sample frontend build and development server |
| Vitest | 4.1.10 | MIT | Test only |
| jsdom | 29.1.1 | MIT | Test only |
| `@types/markdown-it`, `@types/node`, `@types/js-yaml` | versions pinned in `package-lock.json` | MIT | Type declarations; build only |

The npm workspace is private and npm packages are not copied into the
GitHub Pages artifact. `package-lock.json` is the exact resolved inventory for
direct and transitive Node.js packages.

## Downloaded tools and local container images

| Component | Pinned version | Primary license | Distribution boundary |
|---|---:|---|---|
| Docker Compose | 2.39.1 | Apache-2.0 | Downloaded only when a Compose plugin is unavailable; not committed or published |
| OpenTelemetry Java agent | 2.16.0 | Apache-2.0 | Downloaded with checksum verification; not committed or published |
| Playwright Chromium | revision selected by Playwright 1.61.0 | BSD-3-Clause plus bundled third-party notices | Downloaded by Playwright; not committed or published |
| PostgreSQL container | 16.9-alpine | PostgreSQL License plus licenses of Alpine packages | Pulled for local sample verification; not published by this project |
| Jaeger all-in-one container | 1.68.0 | Apache-2.0 plus bundled third-party notices | Pulled for local sample verification; not published by this project |
| OpenTelemetry Collector Contrib container | 0.128.0 | Apache-2.0 plus bundled third-party notices | Built locally from the upstream image; not published by this project |
| Alpine helper container | 3.22.0 | licenses of the contained Alpine packages | Pulled for verification; not published by this project |

Container and downloaded-tool images are aggregate distributions. Their own
embedded license and notice files are authoritative for the packages they
contain.

## Bundled media

| Asset | Local file | Rights status | Source and changes |
|---|---|---|---|
| *Chakrasamvara Mandala*, Nepal, ca. 1100, object 1995.233 | `site/assets/chakrasamvara-mandala.webp` | Public Domain / CC0-1.0 | [The Metropolitan Museum of Art object page](https://www.metmuseum.org/art/collection/search/38021). The Open Access image was resized and converted to WebP for the landing-page background. |

The Met's Open Access policy permits unrestricted sharing and remixing of
designated public-domain images. Attribution is not required by CC0, but the
project retains the work title and source link as a provenance citation. The
citation does not imply endorsement by The Metropolitan Museum of Art.

No third-party font, icon pack, or remotely hosted JavaScript is bundled in
the official site. The favicon and interface icons are project-authored.

## Maintenance and release checks

When a direct dependency or asset changes:

1. Update its pinned version in `gradle/libs.versions.toml`, the relevant
   `build.gradle.kts`, `package.json` / `package-lock.json`,
   `infra/local/compose.yaml`, or `scripts/setup.*`.
2. Update this inventory and the official third-party documentation.
3. Inspect the resolved transitive dependency graph and every redistributed
   archive for changed license or NOTICE files.
4. Include `LICENSE`, `NOTICE`, and this inventory in source releases.
5. For binary distributions, include the license texts and attribution notices
   required by every bundled component. Do not assume the project license
   covers a third-party component.
