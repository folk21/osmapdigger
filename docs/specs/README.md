---
type: Specification Guide
title: Change specifications
description: OsmapDigger specification hierarchy, lifecycle, feature references, and minimal metadata conventions.
---
# Change specifications

## Purpose

`docs/specs/` contains specifications for significant planned or in-progress changes to OsmapDigger.

A specification defines intended behavior and acceptance criteria. It is not the source of truth for behavior that is already accepted and implemented.

Current implementation truth belongs in:

- [`../ARCHITECTURE.md`](../ARCHITECTURE.md);
- [`../IMPLEMENTATION.md`](../IMPLEMENTATION.md);
- [`../CONFIGURATION.md`](../CONFIGURATION.md);
- [`../USAGE.md`](../USAGE.md);
- owning subproject `README.md`/`IMPLEMENTATION.md` files.

Stable capability names belong in [`../FEATURES.md`](../FEATURES.md).

## Feature, requirement, and specification IDs

Keep these concepts separate:

- **Feature ID** — stable capability vocabulary from [`../FEATURES.md`](../FEATURES.md), for example `ANALYSIS.RANKING`.
- **Requirement ID** — normative rule inside one specification, for example `R12`, `DA-R7`, or `SS-R9`.
- **Specification name** — one implementation/change slice, for example `settlement-shortlist-workflow`.

A feature may be introduced or refined by several specifications. A completed specification is archived, but its feature IDs remain stable.

Do not invent feature IDs in tests, code comments, documentation, or specifications. Add a genuinely new durable capability to `docs/FEATURES.md` first.

Requirement IDs are local to their specification. They remain stable while that specification is active and when it is archived, but they must not be reused as repository-wide feature vocabulary.

## Roadmap and acceptance status

Keep capability identity, changing status, and normative evidence separate:

- [`../FEATURES.md`](../FEATURES.md) owns stable Feature IDs and must not carry transient implementation/acceptance status;
- [`../ROADMAP.md`](../ROADMAP.md) owns the compact cross-feature view of current state, acceptance gaps, closure order, and future directions;
- active specifications own normative requirements and the exact remaining evidence for their bounded slice;
- owning implementation/test documents record accepted current behavior and validation evidence.

When implementation is complete but evidence remains, change the sub-spec to `spec_status: verification-pending` and make it an acceptance focus rather than keeping it artificially `active`. Do not create a new Feature ID merely to express roadmap state or an acceptance phase.

## Active tree

Keep the active tree shallow:

```text
active/
    spec-<umbrella>.md
    subspecs/
        <bounded-focus>.md
```

Rules:

- the umbrella defines the active product/system target;
- one sub-spec is the current implementation or acceptance focus;
- other sub-specs may remain active only while they define unresolved supporting or verification-pending constraints;
- completing one sub-spec does not complete the umbrella;
- do not create deeper trees without a concrete need.

The current bounded acceptance focus is `settlement-shortlist-workflow`; its implementation is complete and its remaining work is verification evidence. Desktop analysis, Desktop map, and user-preferences sub-specs remain active only because acceptance verification is incomplete.

## When to create a sub-spec

Create a sub-spec when a change materially affects one or more of these areas:

- product behavior spanning multiple implementation sessions;
- Gradle/module ownership or dependency direction;
- persisted dataset or application-owned storage semantics;
- search/ranking/failure behavior shared across platforms;
- platform boundaries or package installation/lifecycle behavior;
- user-visible workflows that need stable acceptance scenarios;
- work that benefits from explicit migration/compatibility rules.

Do not create a sub-spec for:

- small bugs;
- local refactors;
- narrow documentation cleanup;
- routine dependency maintenance.

## Lifecycle

1. Keep the umbrella under `docs/specs/active/spec-*.md`.
2. Put the current bounded implementation slice under `docs/specs/active/subspecs/`.
3. Record its parent and lifecycle state in frontmatter.
4. Keep umbrella `current_focus`, human-readable status, and `docs/specs/README.md` synchronized.
5. Reference stable feature IDs from `docs/FEATURES.md` in a `Feature scope` section.
6. Give important normative requirements stable local IDs when tests/reviews need them.
7. Implement and validate the slice while preserving root/local `AGENTS.md` invariants.
8. After developer acceptance, move stable behavior into current-state documentation.
9. Move the completed sub-spec to `docs/specs/archive/subspecs/`.
10. Archive the umbrella only when its own acceptance target is complete.

A specification must exist in exactly one lifecycle location. Archival is a move, not a copy.

An implemented but unverified slice may remain active with `spec_status: verification-pending`.

After acceptance:

- current-state documentation owns the behavior;
- the archived spec is historical context only;
- archived specifications are excluded from routine project context unless historical reasoning is needed.

## Active spec hygiene

An active specification is a change contract, not an implementation diary. Keep it useful for review and acceptance rather than accumulating a second copy of current-state documentation.

- `Current state` should be a short status summary plus links to owning current-state documents when detailed wiring already exists there.
- Keep normative requirements, scenarios, non-goals, compatibility constraints, and acceptance criteria while the change remains active.
- `Implementation tasks` should contain only work that is still pending. Once an implementation step is complete, move durable behavior/rationale into the owning architecture, implementation, configuration, usage, or test document instead of preserving a long `Implemented:` history in the active spec.
- A `verification-pending` sub-spec should primarily describe the requirements being verified, the remaining acceptance evidence, and any unresolved compatibility constraint.
- Do not duplicate schema-version timelines, command matrices, class/file inventories, or accepted UI behavior when an owning current-state document already maintains them.
- Preserve design rationale in the active spec only when it is still necessary to evaluate the pending change. Accepted rationale that future maintainers need should move to the relevant owning document before archival.

## Document metadata

Managed documentation uses minimal YAML frontmatter:

- `type`;
- `title`;
- `description`.

Specifications may additionally use:

- `document_role` — `umbrella` or `subspec`;
- `spec_status` — `active`, `verification-pending`, or `archived`;
- `parent` — umbrella path for a sub-spec;
- `current_focus` — only when an umbrella points to one active implementation sub-spec.

Do not add metadata that only repeats the body or Git history. Extend the profile only when a field has a concrete navigation, validation, or maintenance use.

## Writing structure

Prefer a small predictable structure. Use only sections that add value.

```text
# <Change name>

## Status
## Feature scope
## Goal
## Current state
## Requirement map              # when several requirements map to stable features
## Requirements
## Processing sequence          # when order matters
## Failure semantics            # when failures need separate rules
## Scenarios
## Non-goals
## Design constraints
## Compatibility / migration
## Validation
## Remaining work / acceptance  # prefer this once implementation is substantially complete
```

Additional domain-specific sections are allowed when they own real requirements or explanatory context. Do not create headings merely to satisfy the template.

Writing rules:

- keep normative requirements precise;
- use `must`, `must not`, `should`, and `may` consistently;
- prefer short paragraphs with one main assertion;
- use lists for independent rules or ordered processing steps;
- keep processing order separate from invariants and failure semantics;
- distinguish implemented behavior from intended changes and verification-pending work;
- do not restate accepted current-state detail when a current-state document already owns it;
- keep feature IDs stable and requirement IDs local to the spec.

## Active specifications

Umbrella:

- [`active/spec-initial-functional-product.md`](active/spec-initial-functional-product.md) — initial country-agnostic offline dataset builder + KMP Desktop/Android product umbrella.

Current acceptance focus:

- [`active/subspecs/settlement-shortlist-workflow.md`](active/subspecs/settlement-shortlist-workflow.md) — `CANDIDATES.IMPORT`, `FAVORITES.NOTEBOOK`, `FAVORITES.SNAPSHOTS`, `FAVORITES.EXPORT`, `ANALYSIS.EXPLANATION`, and `EXTERNAL.PROPERTY_SEARCH` workflow.

Verification-pending supporting increments:

- [`active/subspecs/desktop-analysis-workspace.md`](active/subspecs/desktop-analysis-workspace.md) — `ANALYSIS.PREFERENCES`, `ANALYSIS.RANKING`, `ANALYSIS.EXPLANATION`, and `PRESENTATION.ANALYSIS_WORKSPACE`; strict country-scale scoring and remaining configured acceptance checks are pending.
- [`active/subspecs/desktop-map.md`](active/subspecs/desktop-map.md) — `MAP.OFFLINE`, `MAP.RESULTS`, and Desktop renderer behavior; remaining acceptance verification pending.
- [`active/subspecs/user-preferences.md`](active/subspecs/user-preferences.md) — `SETTINGS.PERSISTENCE` for dataset-scoped search context; remaining cross-platform verification pending.

## Recently completed sub-specifications

- [`archive/subspecs/kmp-modular-architecture-hardening.md`](archive/subspecs/kmp-modular-architecture-hardening.md) — accepted `PLATFORM.MODULAR_KMP` hardening track covering dependency cleanup, operational failures, shared persistence/query semantics, source decomposition, and physical `:core`/`:application`/`:presentation` modularization.

Create another architecture-hardening spec only when a concrete new structural requirement justifies one. Do not reactivate the archived spec as a permanent bucket for unrelated refactoring.
