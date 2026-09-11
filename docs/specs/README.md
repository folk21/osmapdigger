---
type: Specification Guide
title: Change specifications
description: Specification hierarchy, lifecycle, and OKF-compatible metadata conventions.
---
# Change specifications

`docs/specs/` contains specifications for significant planned or in-progress changes to OsmapDigger. A specification defines the intended delta/acceptance target before that work is considered complete; it is **not** canonical documentation of what the repository already does.

Current product and implementation truth remains in owning documents such as [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../IMPLEMENTATION.md`](../IMPLEMENTATION.md), [`../CONFIGURATION.md`](../CONFIGURATION.md), [`../USAGE.md`](../USAGE.md), plus subproject `IMPLEMENTATION.md` files.

## Specification hierarchy

The active specification tree uses a deliberately small two-level structure:

```text
active/
    spec-<umbrella>.md
    subspecs/
        <current-focus>.md
```

- `spec-*.md` is an active umbrella specification that owns the broader product or architectural target.
- `subspecs/*.md` refines one bounded implementation increment under the umbrella.
- At most one sub-spec is the current implementation focus at a time.
- The umbrella links to the current sub-spec, and the sub-spec links back to its parent.
- A long-lived architecture-hardening sub-spec may remain active but non-current while bounded hardening gates are interleaved between completed, testable product increments. Do not switch into structural refactoring in the middle of an unfinished feature increment.
- Completing a sub-spec does not imply that its umbrella specification is complete.

This hierarchy is intentionally shallow. Do not create deeper specification trees unless a real product need makes the two-level model insufficient.

## When to create a sub-spec

Create a sub-spec when an implementation increment:

- introduces a substantial product capability;
- changes an application-owned persisted contract or important runtime boundary;
- spans multiple implementation sessions;
- benefits from stable requirements and acceptance scenarios without expanding the umbrella specification with implementation detail.

Small bug fixes, local refactors, narrow documentation improvements, and routine dependency maintenance do not require a sub-spec.

## Lifecycle

1. Keep the active umbrella specification under `docs/specs/active/spec-*.md`.
2. Put the current bounded implementation focus under `docs/specs/active/subspecs/`.
3. Record the parent/current-focus relationship in both YAML frontmatter and Markdown links.
4. Give important sub-spec requirements stable IDs so tests and reviews can refer to them.
5. Implement while preserving root/local `AGENTS.md` invariants.
6. After sub-spec acceptance, move stable knowledge into the owning current-state documentation.
7. Move the completed sub-spec to `docs/specs/archive/subspecs/`.
8. Keep the umbrella active until its own acceptance target is complete; then archive it separately.

Archived specifications preserve historical design intent but are not current source of truth. `concat_osmapdigger.sh` excludes archived specs so routine LLM context contains current code/docs plus active intended work.

## Document metadata

OsmapDigger documentation uses an Open Knowledge Format (OKF)-compatible YAML frontmatter profile. The project intentionally starts with a minimal subset:

- `type` — document kind;
- `title` — concise human-readable title;
- `description` — one sentence that helps humans and LLMs decide whether the document is relevant.

Specification files additionally use only the relationship/workflow fields that are currently needed:

- `document_role` — `umbrella` or `subspec`;
- `spec_status` — project workflow state such as `active` or `archived`;
- `parent` — parent umbrella path for a sub-spec;
- `current_focus` — current sub-spec path for an umbrella.

These additional fields are OsmapDigger extensions. Do not add metadata that merely duplicates the Markdown body or Git history. Extend the profile only when a field has a concrete navigation, validation, or maintenance use.

## Suggested sub-spec structure

```text
# <Change name>

## Status
## Goal
## Relationship to the umbrella specification
## Current state
## Requirements
## Scenarios
## Non-goals
## Design constraints
## Compatibility / migration
## Validation
## Implementation tasks
```

## Active specifications

Umbrella:

- [`active/spec-initial-functional-product.md`](active/spec-initial-functional-product.md) — initial country-agnostic offline dataset builder + KMP Desktop/Android product umbrella.

Current implementation focus:

- [`active/subspecs/settlement-shortlist-workflow.md`](active/subspecs/settlement-shortlist-workflow.md) — persistent Favorites/notebook, imported candidate sets, frozen analysis snapshots, export/share, and batch external searches.

Archived completed sub-specs:

- [`archive/subspecs/kmp-modular-architecture-hardening.md`](archive/subspecs/kmp-modular-architecture-hardening.md) — accepted KMP architecture-hardening track covering dependency cleanup, operational failures, shared persistence/query semantics, source decomposition, and physical `:core`/`:application`/`:presentation` modularization.

Verification-pending completed increments:

- [`active/subspecs/desktop-analysis-workspace.md`](active/subspecs/desktop-analysis-workspace.md) — weighted ranking/map-first implementation complete; strict country-scale scoring and remaining configured acceptance checks are pending.
- [`active/subspecs/desktop-map.md`](active/subspecs/desktop-map.md) — Desktop offline map implementation complete; remaining acceptance verification pending.
- [`active/subspecs/user-preferences.md`](active/subspecs/user-preferences.md) — local search-context persistence implementation complete; remaining cross-platform verification pending.
