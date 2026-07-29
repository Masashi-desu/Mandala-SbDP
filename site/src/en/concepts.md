---
title: Core concepts
order: 2
description: Meaning of Node, Edge, Evidence, Confidence, Review, Conflict, Stale, and Stable ID
---
# Core concepts

## Mandala

A Mandala is reproducible technical documentation that places information from screens through the database in one relation graph. HTML is not the source of truth; the Renderer consumes analysis inputs, Custom HTML, and a versioned Documentation Graph.

## Nodes and Edges

Nodes include `SCREEN`, `HTTP_ENDPOINT`, `JAVA_METHOD`, `SQL_STATEMENT`, and `DB_COLUMN`. Edges include `CALLS_HTTP`, `ROUTES_TO`, `EXECUTES_SQL`, `READS`, and `UPDATES`. Reverse edges are not duplicated; an incoming index is constructed from the same `from` and `to` data.

The sample lets you traverse forward from [`POST /api/projects`](sample-ref:endpoint:POST:/api/projects) and backward from [`public.projects`](sample-ref:table:public.projects).

## Evidence

Evidence types distinguish `RUNTIME_OBSERVATION`, `SPRING_MAPPING`, `OPENAPI`, `SOURCE_CODE`, `JAVADOC`, `DOMA_MAPPING`, `SQL_STATIC_ANALYSIS`, `DATABASE_INTROSPECTION`, `PLAYWRIGHT_OBSERVATION`, `AGENT_INFERENCE`, and `HUMAN_INPUT`. Each records source location, commit, adapter, observation time, and details.

## Confidence

| Value | Meaning |
|---|---|
| `OBSERVED` | Seen in a Trace or Playwright execution |
| `DECLARED` | Declared by a Mapping, OpenAPI, or database schema |
| `INFERRED` | Derived by static analysis or an Agent |
| `HUMAN_REVIEWED` | Reviewed by a human |
| `CONFLICTED` | Sources disagree |
| `STALE` | The source changed after the explanation |
| `UNKNOWN` | Evidence is insufficient |

## Review State, Conflict, and Stale

Review State tracks unreviewed, Agent-reviewed, human-reviewed, and rejected content. Conflict retains competing claims and their evidence. Stale compares the source fingerprint used by an explanation with the current source and queues mismatches for review rather than deleting them.

## Stable ID

IDs such as `endpoint:POST:/api/projects` and `column:public.projects.name` come from semantic identity, never line numbers, timestamps, or raw trace IDs. Official-document sample links are also authored with Stable IDs and resolved to actual files through `page-map.json`; unresolved IDs fail the build.
