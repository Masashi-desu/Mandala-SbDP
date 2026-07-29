---
title: Security
order: 18
description: Secure handling of credentials, cookies, tokens, SQL, traces, database access, Custom HTML, and published static artifacts
---
# Security

## Data that is never stored

Passwords, cookies, session IDs, Authorization headers, access or refresh tokens, API keys, database passwords, personal information, and SQL bind values are not stored in the Graph, observations, normalized traces, or static HTML. Request and response data and span attributes are masked recursively.

## Credentials

Configuration contains only `usernameEnv` and `passwordEnv`. `.env` is ignored by Git. Never reuse local sample credentials in a real environment; the database seed stores password hashes with bcrypt.

## SQL and traces

SQL literals and binds are normalized to `?`. Raw OTLP is retained only briefly as a local or CI artifact and is excluded from Pages, releases, and public build caches. Verification scans for Bearer credentials, private keys, cloud access keys, and password assignments.

## Database permissions

Use a read-only account for schema capture instead of reusing a migration account. Production database access is not part of the default workflow. Configure connection allowlists, timeouts, and SSL in the consuming project.

## Custom HTML

Custom HTML is trusted repository content, but scripts, inline event handlers, and `javascript:` URLs are rejected by default. Enabling arbitrary JavaScript requires review. Configure a Content Security Policy in the hosting environment when publishing.

## Published static artifacts

Published static bundles contain only public projections such as renderer-produced HTML, the search index, page map, and screenshots. Raw Documentation Graphs, raw OTLP traces, database snapshots, local configuration, and credentials are excluded.

This boundary applies to any static hosting provider, not only GitHub Pages. Limit the publication source to the generated site root; never use the entire repository or the `mandala` workspace as the document root or upload target.

The theme and language controls store only the values `system|light|dark` and `ja|en` in browser-local storage. They make no external requests, load no remote translation catalog, and never translate or transmit Graph-derived source text.

## Threat model

Primary threats are secret leakage from observations, untrusted HTML, unintended external requests, excessive database permissions, and path traversal in the generated site. Capture rejects undefined APIs, and the server rejects paths outside its normalized root.
