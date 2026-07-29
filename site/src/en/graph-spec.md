---
title: Documentation Graph specification
order: 4
description: Node and Edge types, metadata, serialization, reverse indexes, and diff rules
---
# Documentation Graph specification

The Graph root contains `schemaVersion`, `projectId`, `targetCommit`, `analyzedAt`, `nodes`, and `edges`. Nodes and Edges are canonicalized by Stable ID; duplicate IDs and dangling edges are verification errors.

## Node types

UI types include `E2E_FLOW`, `UI_ENTRY`, `SCREEN`, `SCREEN_STATE`, `UI_ACTION`, and `SCREENSHOT`. HTTP includes `HTTP_CLIENT_CALL`, `HTTP_ENDPOINT`, `OPENAPI_OPERATION`, and request/response schemas. Java includes classes, methods, controllers, Application Services, and Doma DAOs. Data includes SQL, schemas, tables, columns, views, materialized views, functions, triggers, and policies. Runtime includes traces and spans.

## Edge types

UI uses `HAS_STATE`, `HAS_ACTION`, `PERFORMED_ON`, `TRANSITIONS_TO`, `NAVIGATES_TO`, `CAPTURED_AS`, and `CALLS_HTTP`. `PERFORMED_ON` connects the source `SCREEN_STATE` to a `UI_ACTION`, while `TRANSITIONS_TO` connects that Action to the target `SCREEN_STATE`. Aggregated screen-level paths remain `NAVIGATES_TO` relationships between `SCREEN` Nodes. HTTP and Java use `MATCHES_OPERATION`, `ROUTES_TO`, `ACCEPTS`, `RETURNS`, `CALLS`, and `EXECUTES`. Data uses `EXECUTES_SQL`, `READS`, `CREATES`, `UPDATES`, `DELETES`, `REFERENCES`, `FK_TO`, and trigger/function relations. Provenance uses `OBSERVED_IN`, `DECLARED_BY`, and `INFERRED_FROM`.

## Metadata

Every element carries Evidence, source locations, target commit, analysis time, adapter, Confidence, Review State, StaleInfo, Conflicts, warnings, related Trace, and related Scenario. Attributes hold adapter-specific structured data, not common semantics.

## Incoming index

Core builds `outgoing[id]` and `incoming[id]` once. Renderer uses the same Edge for forward and reverse navigation, preventing one side of a duplicated relation from becoming stale.

## Diff format

Diff records `ADDED`, `REMOVED`, and `MODIFIED` per Node or Edge, including changed fields, before/after fingerprints, and impacted Stable IDs. Timestamps, collection order, and internal adapter debug values are excluded.

## Compatibility

Major changes require an explicit CLI migration. Minor added fields are readable by default. Stable ID grammar changes require an alias table instead of silently producing mass delete/add changes.
