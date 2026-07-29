---
title: Scope and non-goals
order: 1
description: Scope and explicit non-goals of E2E-centered living documentation
---
# Scope and non-goals

Mandala is centered on E2E flows. It connects UI actions, Client APIs, Spring Endpoints, Application Services, Doma DAOs, external SQL, and PostgreSQL Tables, showing Evidence and Confidence at every boundary. Humans do not have to write a complete E2E specification first: routes, components, API clients, mappings, and traces provide candidates for Agent-assisted review.

The public [successful project creation flow](sample-ref:flow:project.create.success) demonstrates the complete vertical connection.

## Supported stack

- Java 21, Spring Boot 3, Spring MVC or WebFlux
- Doma 3, external SQL, and templates
- PostgreSQL, Flyway, `information_schema`, and `pg_catalog`
- OpenTelemetry and OTLP JSON
- TypeScript, Playwright, and Request Interception
- Gradle Kotlin DSL, GitHub Actions, and GitHub Pages

## Complementary sources

Runtime observations, framework-resolved values, the live database schema, OpenAPI, static analysis, and Agent inference are weighted separately. Human-reviewed explanations take precedence for business intent. Conflicting sources are retained as a `Conflict` instead of being overwritten.

## Humans and Agents

Agents discover, collect, draft explanations, compute diffs, and identify stale candidates. Humans review business intent, design decisions, false positives, and accepted exceptions. Free-form explanations are stored as Custom HTML outside generated regions.

## Non-goals

The initial version does not provide JPA/Hibernate-centered analysis, MyBatis, jOOQ, non-PostgreSQL databases, complete call graphs for every private method, or continuous production monitoring. Reflection, procedures, and unexecuted dynamic branches remain UNKNOWN or INFERRED when evidence is insufficient. Adapter boundaries allow forks to add other stacks.
