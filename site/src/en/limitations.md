---
title: Limitations
order: 19
description: Facts that static or runtime analysis cannot determine and how Mandala avoids presenting them as certain
---
# Limitations

## Unexecuted branches

Runtime Capture marks only paths exercised by a scenario as OBSERVED. Unexecuted branches remain INFERRED from source analysis and are not declared absent. E2E flows with insufficient coverage appear in the review report.

## Dynamic SQL

Runtime conditions in Doma templates, embedded variables, and application-built SQL prevent complete static enumeration. Mandala merges template segments with observed SQL and gives unobserved branches an UNKNOWN warning.

## Reflection and AOP

Reflection, dynamic proxies, and runtime-generated classes cannot be followed through a source call graph alone. Spring Mapping and traces provide complementary Evidence. When AOP changes execution order, declared and observed paths are displayed separately.

## Asynchronous work, triggers, and procedures

Asynchronous work that loses trace context cannot be proven to belong to the same flow. Trigger, function, and procedure internals are partially analyzed from catalog definitions, but dynamic execution remains uncertain. Direct, indirect, and asynchronous operations are identified explicitly.

## Frontend

Runtime-generated routes, feature flags, browser-specific branches, and canvas content cannot be discovered completely through TypeScript AST and ARIA alone. Add captures for fixed flag and role combinations. A screenshot does not automatically prove visual intent.

## OpenAPI and Javadoc

Analysis continues when they are incomplete, but Confidence in request or response explanations and business intent decreases. Agent inference is not styled as certain fact; Custom HTML or review supplies the missing context.

## Limits of generated specifications

The Graph organizes observations, declarations, and inference; it does not guarantee business correctness. Mandala does not resolve a Conflict automatically by majority vote and requires explicit human or Agent review.

Language selection changes only renderer-authored interface explanations. Source-derived terms, quotes, descriptions, code, SQL, and Evidence remain in their original language, so a page can intentionally contain more than one language.
