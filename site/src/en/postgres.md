---
title: PostgreSQL live-schema analysis
order: 7
description: Converting JDBC metadata, information_schema, and pg_catalog into a Documentation Graph
---
# PostgreSQL live-schema analysis

Mandala connects read-only to a Flyway-migrated PostgreSQL instance instead of inferring the database from Java Entities. JDBC `DatabaseMetaData`, `information_schema`, and `pg_catalog` are combined into a structured schema snapshot equivalent to tbls.

## Captured objects

Database, schema, table, column type, nullable, default, PK, FK, unique, check, index, sequence, view, materialized view, enum, domain, trigger, function, RLS policy, and table/column comments are captured.

Examples: the [`public.projects` table definition](sample-ref:table:public.projects), the [`public.tasks` table definition](sample-ref:table:public.tasks), and the [`public.projects.name` column detail](sample-ref:column:public.projects.name).

## Table definitions

Each Table page presents a conventional table definition with schema, table name, owner, RLS, column name, type, nullability, default, and comment. PK, FK, Unique, Check, Index, and referenced targets appear alongside each column and in detailed sections. Referencing Tables, Triggers, Functions, RLS Policies, related SQL, DAOs, and Application Services link to their graph nodes.

Related E2E flows remain a primary section immediately after the table definition, with CRUD classifications. This keeps database structure and reverse lookup to user scenarios equally accessible from the same Table page.

## Snapshot

Values are normalized by Stable ID and written to `mandala/snapshots/db`. Commit, configuration hash, PostgreSQL server version, and adapter version determine whether incremental cache reuse is safe.

## Privileges

Production connections are not the default. Local or CI PostgreSQL receives the same Flyway migrations, while the capture user is limited to CONNECT, USAGE, SELECT, and catalog access. Passwords come from environment variables and never enter the snapshot.

## ER relationships

PK and FK metadata produces `FK_TO` Edges. The global ER diagram can filter by schema, while a flow-specific diagram contains only reachable and optionally adjacent Tables. Relations connect their endpoint Columns and expose `0..*`, `0..1`, or `1` cardinality at each end.

Relationship lines can be switched between IDEF1X and IE (Crow's Foot) notation within the page. An FK that forms part of the child Table's PK is derived as identifying; every other FK is non-identifying. IDEF1X maps that structure to solid or dashed lines, a child dot, an optional-parent diamond, and `Z` or `P`. IE maps the same semantics to solid or dashed lines plus circle, bar, and Crow's Foot endpoints. The choice is page-local and does not add a persisted setting.

ER cards preview only the PK, FK, Unique, and referenced Columns needed to understand those relationships. Complete Column definitions—including type, nullability, default, comment, constraints, and indexes—remain the responsibility of each Table page. Follow a Table name, relationship Column, or card footer to open that definition. Open the [sample ER diagram](../sample/er/).

## Trigger, Function, and RLS

Triggers and functions are separate Nodes connected by `FIRES_TRIGGER` and `CALLS_FUNCTION`. CRUD inferred from function bodies is marked indirect and INFERRED. RLS expressions are captured with secret-looking literals masked.
