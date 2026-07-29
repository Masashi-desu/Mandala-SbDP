---
title: Generated documentation specification
order: 11
description: E2E, Endpoint, Java, DAO, SQL, Table, ER, CRUD, Custom HTML, search, and link validation
---
# Generated documentation specification

Renderer produces static HTML from the Graph with no server-side runtime. Every Node page displays Stable ID, description, attributes, Evidence, Confidence, warnings, conflicts, outgoing relations, and the incoming index.

## Page types

E2E and Screen pages show Screenshot, state, action, Client API, request/response, execution path, CRUD, partial ER, and Custom HTML. Endpoint pages show mapping, handler, validation, error, and consuming screens. Java/DAO/SQL pages show Javadoc, signature, callers, callees, normalized SQL, and static/runtime differences. Table pages lead with a conventional definition covering schema, columns, types, nullability, defaults, comments, constraints, indexes, references, triggers, functions, RLS, and related SQL, DAOs, and Application Services. Related E2E flows and CRUD reverse lookup remain a separate primary section immediately afterward.

The Screen Transitions page places E2E-observed `SCREEN` Nodes in a screenshot-backed overview and connects `NAVIGATES_TO` relationships with responsive lines. The overview stays focused on the whole application and does not repeat one-to-one transition rows or internal screen states.

Each individual SCREEN page owns its representative screenshots, one-to-one incoming and outgoing transitions, and `SCREEN_STATE → UI_ACTION → SCREEN_STATE` details. Action rows include sequence, role, feature flags, outcome, and related HTTP status, distinguishing conditional branches that share a source state and action but reach different outcomes.

Examples:

- [E2E: successful project creation](sample-ref:flow:project.create.success)
- [Endpoint: `POST /api/projects`](sample-ref:endpoint:POST:/api/projects)
- [Table: `public.projects`](sample-ref:table:public.projects)
- [Observed screen transition diagram](../sample/screens/transitions.html)

## ER and CRUD Matrix

Global and partial ER diagrams use selectable semantic HTML cards rather than fixed images. Responsive lines connect each FK Column to its referenced Column and label both endpoint cardinalities. Cards preview only PK, FK, Unique, and referenced Columns; complete Column lists stay on individual Table pages. CRUD Matrix cells link to E2E, SQL, Table, and Column reverse lookups. Open the [ER diagram](../sample/er/) and [CRUD Matrix](../sample/crud/).

## Custom HTML

`mandala/custom/{entries,endpoints,symbols,tables}/<stable-slug>/*.html` is inserted after generated sections. Stable references are resolved during rendering, and missing references fail verification. The [project creation Custom HTML](sample-ref:custom-html:entries/project-create-success) shows generated and human-authored content together.

Scripts, inline event handlers, and `javascript:` URLs are removed by default. CSS should be scoped below `.custom-html`.

Generated sites share the official landing-page palette by default. For site-wide branding, use the public `--mandala-light-*` and `--mandala-dark-*` tokens in `mandala/custom/palette.css`. The Renderer preserves this file across refreshes and rejects arbitrary selectors or remote imports. Item-specific Custom CSS is automatically scoped to Custom HTML so that it cannot leak into the generated theme.

## Search and link validation

`search-index.json` contains title, type, Stable ID, description, and URL. All relative `href` and asset targets are checked after rendering. Dangling links, missing Graph nodes, and unresolved custom references fail the build.

## Language and source fidelity

Language selection translates only Renderer-authored navigation and explanatory labels. Display names, descriptions, code, SQL, Stable IDs, Evidence, and quotations collected from analyzed sources remain in their original language.

The Language and Theme controls appear in the header of both official Docs and the generated Mandala. Theme supports System, Light, and Dark; System follows `prefers-color-scheme`. Both selections are stored locally in the browser and shared between the two artifacts.
