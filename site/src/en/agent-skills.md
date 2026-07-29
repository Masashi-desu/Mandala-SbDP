---
title: Agent Skills
order: 13
description: Contracts and editing boundaries for discover, capture, analyze, reconcile, refresh, and review Skills
---
# Agent Skills

Skills in `platform/agent-skills` compose reproducible CLI commands. A Skill does not hand-author a separate analysis result.

## Execution order

1. `mandala-discover` finds routes, actions, APIs, Endpoints, services, DAOs, SQL, DB objects, and Custom HTML.
2. `mandala-capture-ui` generates and executes mock and Scenario candidates.
3. `mandala-capture-runtime` runs backend Scenarios and captures Trace.
4. `mandala-analyze-db` generates live schema, SQL, CRUD, and ER.
5. `mandala-reconcile` merges Evidence and detects conflict/stale state.
6. `mandala-refresh` runs the full or incremental lifecycle.
7. `mandala-review` creates a human/Agent review packet.

## Evidence rules

Agent deductions use `AGENT_INFERENCE` or `SOURCE_CODE` Evidence and remain INFERRED. Nothing becomes OBSERVED without inspecting command output, a Screenshot, or runtime Evidence. Unanalyzable facts retain warnings.

## Editable regions

Agents may edit configuration, fixtures, Scenarios, Custom HTML, review metadata, and source code within task scope. They never patch `mandala/generated` to conceal a defect, overwrite human Custom HTML wholesale, or add secrets, raw tokens, cookies, or bind values.

## Review packet

Candidate flows, screen summaries, business-purpose drafts, exceptions, service candidates, CRUD, conflicts, stale items, diffs, and Custom HTML proposals are grouped by Stable ID. Acceptance updates Review State and Evidence rather than replacing visible text.
