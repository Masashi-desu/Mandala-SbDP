---
title: SQL and CRUD analysis
order: 10
description: Rules for deriving table- and column-level CRUD from SQL AST and Runtime Observation
---
# SQL and CRUD analysis

CRUD is never inferred from the HTTP method. `POST /search` is READ, soft delete is UPDATE, and project creation also creates an `audit_logs` row. SQL AST provides the primary classification, and runtime traces mark what was observed.

Inspect the analyzed [`ProjectDao/insert.sql`](sample-ref:sql:META-INF/io/github/mandala/sbdp/sample/database/dao/ProjectDao/insert.sql) and its [successful flow projection](sample-ref:flow:project.create.success).

## Base classification

| SQL | CRUD |
|---|---|
| `SELECT` | READ |
| `INSERT` | CREATE |
| `UPDATE` | UPDATE |
| `DELETE` | DELETE |
| `MERGE` | CREATE / UPDATE |
| `TRUNCATE` | DELETE |

## Columns

INSERT lists, UPDATE sets, RETURNING, projections, WHERE clauses, and JOIN keys are captured by role. `SELECT *` expands only when joined with a known live schema; otherwise the Table remains READ with a warning.

## Soft delete

If an HTTP DELETE runs `UPDATE projects SET archived = true`, the result is `UPDATE public.projects`. The business meaning “soft delete” belongs in Custom HTML or Review Evidence.

## Direct and indirect

Application-executed statements are direct; trigger/function/procedure work is indirect; later consumer work is async. Partial function expansion remains UNKNOWN or INFERRED.

## CRUD records

Each record contains flow, endpoint, service, DAO/method, SQL, schema, table, column, operation, directness, confidence, Evidence, Trace, and Scenario. Open the [CRUD Matrix](../sample/crud/) and [`public.audit_logs`](sample-ref:table:public.audit_logs).
