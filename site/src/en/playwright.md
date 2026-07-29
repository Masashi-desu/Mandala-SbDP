---
title: Playwright UI Capture
order: 9
description: Capturing API mocks, screen states, stabilization, Client APIs, ARIA, and undefined traffic
---
# Playwright UI Capture

UI Capture is document execution rather than assertion-only E2E testing. It uses Request Interception and fixtures without connecting to the backend or database, then joins Runtime Capture by HTTP method and normalized path.

## Automatic discovery

TypeScript AST analysis discovers route declarations, API clients, and controls such as buttons, links, inputs, and selects. It emits candidate flows as INFERRED; humans do not need to author every Scenario first. Repository roots, frontend roots, scenario globs, outputs, base URL, and managed dev server are configuration-driven and overrideable through `MANDALA_CAPTURE_*` variables or CLI options.

## Screen states

The engine handles normal, Loading, empty, validation error, forbidden, API error, and Not Found states. Sample Scenarios are reviewed fixtures, while the runner remains a reusable YAML-driven engine. Undefined API traffic fails with status 599 instead of escaping to the network.

Explore the generated states:

- [Success](sample-ref:flow:project.create.success) / [Validation error](sample-ref:flow:project.create.validation)
- [Empty](sample-ref:flow:projects.empty) / [Loading](sample-ref:flow:projects.loading)
- [Forbidden](sample-ref:flow:forbidden) / [API error](sample-ref:flow:api.error) / [Not Found](sample-ref:flow:not.found)

## Determinism

Viewport 1440×1000, `ja-JP`, Asia/Tokyo, light color scheme, device scale factor 1, time, random, role, response, reduced motion, caret, and font loading are fixed. `body[data-doc-ready=true]`, ARIA, and locators provide synchronization rather than arbitrary sleeps.

## Captured format

Each observation records route, URL, state, action, locator, status, normalized path, masked bodies, mock ID, navigation, Screenshot, ARIA snapshot, DOM text, console error, and undefined communication. Sensitive fields are recursively masked.

Observation schema `1.1` records every operation in `transitions[]`. Each record contains `sequence`, `from { route, state }`, the sanitized `action`, `to { route, state }`, role and feature-flag conditions, the immediate `outcome`, the overall `scenarioOutcome`, and the `relatedHttp` calls made while that action was active. HTTP observations also carry `actionSequence`, so multi-action Scenarios do not misattribute initial page-load or other actions' requests to the final action.

Each `from` and `to` also records the verbatim primary heading as `name` and a repository-relative `screenshot` for that exact transition point. SCREEN pages and the global connection map can therefore label and show source screens such as create and edit forms, not only each Scenario's final result.

State names prefer `body[data-mandala-state]` or `body[data-doc-state]`. Otherwise, the runner uses the Scenario's `initialState`, each Action's optional `resultState`, and the final `state`. This preserves same-URL transitions such as normal to validation error.

## Screenshot review

Screenshots become `SCREENSHOT` Nodes connected with `CAPTURED_AS`. Visual Golden changes require an explicit update command; CI never approves them automatically.
