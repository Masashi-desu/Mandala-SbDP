---
title: Extension guide
order: 17
description: Contracts for adapters, Node and Edge types, Evidence, renderers, SQL parsers, and other databases or frameworks
---
# Extension guide

## Adding an adapter

An adapter must not leak framework APIs into `mandala-model`. It returns immutable analysis records or Graph fragments with inputs, adapter version, Evidence, source locations, and warnings. An unparseable input must not become an empty success. Add fixtures and Golden tests.

## Nodes and Edges

Add an enum only when existing types cannot express the meaning. Update the Stable ID grammar, renderer fallback, serialization migration, reverse links, diff logic, and documentation together. Do not add a duplicate reverse Edge for the same relationship.

## Evidence

For new Evidence, define whether it describes a technical fact or design intent, and add its default Confidence and priority. Do not treat arbitrary external-tool output as OBSERVED. Retain the source version and collection time.

## Renderer

Node-specific sections must retain the common header, Evidence, and relationship sections. Derive output paths deterministically from Stable IDs and send every link through the Link Validator. The renderer must never rewrite the Custom HTML source.

Renderer-authored interface explanations use translation keys. Graph-derived display names, descriptions, IDs, SQL, Evidence excerpts, and source terminology remain verbatim and must not be marked as translatable UI text.

## Replacing the SQL parser

The `SqlAnalyzer` contract returns statement kind, schema, table, column, CTE, JOIN, predicate, RETURNING, function, dynamic segments, and warnings. Only an implementation that satisfies the PostgreSQL fixture corpus and CRUD classifier tests may replace it.

## Other databases, JPA, and MyBatis

PostgreSQL catalog details stay inside its adapter. A database fork retains the schema capability matrix and Stable ID contract. Implement JPA or MyBatis as an independent module rather than mixing branches into the Doma adapter.

## Fork contracts

Preserve canonical JSON, Stable IDs, Evidence and Confidence, the single-Edge reverse index, non-destructive Custom HTML, secret masking, Full fallback, link validation, and source-text fidelity.
