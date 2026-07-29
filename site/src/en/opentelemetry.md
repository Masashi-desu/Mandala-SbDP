---
title: OpenTelemetry integration
order: 8
description: Endpoint-to-database execution paths, Mandala attributes, Trace import, and sensitive data
---
# OpenTelemetry integration

Runtime Graph imports OTLP JSON traces. HTTP server and JDBC auto-instrumentation are supplemented by Starter AOP at Application Service and Doma DAO boundaries; every private method is not indiscriminately traced.

## Identified boundaries

- HTTP server, Controller, Application Service, and Use Case
- Doma DAO, JDBC/R2DBC, and external HTTP client
- Async task, consumer, and message processing

Parent/child and link relationships are retained. Async work uses trace context or Mandala flow attributes when available.

## Mandala attributes

`mandala.flow.id`, `mandala.symbol.id`, `mandala.layer`, `mandala.endpoint.id`, `mandala.dao.id`, and `mandala.sql.id` connect runtime spans to semantic identities.

## SQL

`db.system=postgresql`, `db.operation.name`, and sanitized statements are imported. Bind values and connection passwords are excluded. Normalized SQL connects JDBC observations to Doma external SQL while retaining static and runtime Evidence separately.

## Masking

Authorization, Cookie, Token, session, email, SQL literal, and configured mask keys are recursively masked. A Graph-boundary allowlist retains only route/method, DB operation and sanitized SQL, Java symbol, messaging/RPC, `mandala.*`, limited service metadata, and SDK information. Raw traces are excluded from Pages, and `mandala verify` rescans generated output.

## Meaning of OBSERVED

OBSERVED means the path ran in a prepared Scenario, not that every input always follows it. Unobserved branches remain INFERRED or DECLARED. Compare both in the [successful project creation flow](sample-ref:flow:project.create.success).
