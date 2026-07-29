---
title: Installation
order: 14
description: Prerequisites, Starter, Doma, PostgreSQL, OpenTelemetry, Playwright, and initial and continuous analysis
---
# Installation

## Prerequisites

Install Java 21, Node.js 24, Docker Engine, and Bash or PowerShell 7. Gradle is provided through the Wrapper and npm dependencies are locked, so global installations are unnecessary. When the Docker Compose plugin is unavailable, the scripts use the pinned standalone Compose binary.

## New clone

```bash
git clone <repository-url>
cd mandala-sbdp
./scripts/setup.sh
./scripts/start.sh
./scripts/refresh-mandala.sh
```

The setup script creates `.env` from `.env.example`. Do not commit credentials other than the documented local sample credentials.

## External Spring Boot project

Add the Mandala modules from a Maven repository and apply the Gradle plugin `io.github.mandala.sbdp`. Add the Starter as a runtime dependency and annotate Application Service or DAO boundaries with `@MandalaSpan`. Expose Actuator mappings and OpenAPI only in local or CI environments with restricted network access.

```kotlin
plugins { id("io.github.mandala.sbdp") version "0.1.0" }
dependencies { implementation("io.github.mandala.sbdp:mandala-spring-boot-starter:0.1.0") }
```

## Doma

Configure the Java roots, resource roots, and the resource directory containing `META-INF` SQL files. Generated annotation-processor classes do not need to be analyzed. The external SQL files and DAO interface sources are required.

## PostgreSQL and Flyway

Start an ephemeral PostgreSQL instance in CI, apply the same migrations as the application, and then run read-only schema capture. Put only environment-variable names for the URL, username, and password in `mandala.yml`.

## OpenTelemetry

Attach the OpenTelemetry Java Agent to the backend and export OTLP/HTTP data to the Collector. The Starter adds Mandala attributes at service and DAO boundaries. Raw traces remain under `mandala/traces` and are excluded from the Pages artifact.

## Playwright

Start only the frontend development server and intercept API routes. Run `npm run discover:ui` to update candidates and `npm run capture:ui` to regenerate screenshots and observations.

## Initial and continuous analysis

Use `mandala refresh --mode full` for the first run and `--mode incremental` for routine updates. Always run `mandala verify` after generation. Add Custom HTML under `mandala/custom`, never directly to generated output.
