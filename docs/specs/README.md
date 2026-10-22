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

When implementation is complete but evidence remains, change the sub-spec to `spec_status: verification-pending` and make it an acceptance focus rather than keeping it artificially `active`. A reviewed future slice may live under `docs/specs/planned/` with `spec_status: planned`; planned specs document intent but are not the current implementation contract and must not silently enlarge active acceptance. Do not create a new Feature ID merely to express roadmap state or an acceptance phase.

## Processing-state contract

[`STATE.json`](STATE.json) is the only Git-tracked **processing checkpoint** for a selected active sub-specification. Its strict [schema](state.schema.json) and read-only [validator](../../tools/specs/validate_state.py) define the supported shape and repository semantics:

```bash
python3 tools/specs/validate_state.py
```

`schemaVersion: 1` supports only `mode: SINGLE` with one selected workstream (`selectedWorkstreamId`) and a bounded `workstreams` list. Each record has a unique `id` and active `specPath`, an existing normative `stageId`, a checkpoint `phase`, optional `runId`, and `developerDecision`. Only the selected workstream may progress; other records must be `PAUSED` or `BLOCKED`. `selectedWorkstreamId` is a pointer, **not a lock**. Parallel stage writers and `MULTI` mode are not authorized.

The allowed checkpoint phases are `DESIGN`, `READY_TO_DELEGATE`, `READY_FOR_REVIEW`, `BLOCKED`, `PAUSED`, and `AWAITING_DEVELOPER_DECISION`. Review/decision phases require a valid `runId`; `developerDecision` allows only `UNRECORDED` or `REJECTED`, with rejection requiring `BLOCKED` or `PAUSED`. **`RUNNING` and `ACCEPTED` are invalid**: live attempts belong to private source-bound run evidence, and spec acceptance requires a separate developer decision and lifecycle action. A recorded `runId` does not prove a run occurred. The validator checks structure and referenced active stages, not Git checkout cleanliness, authorization, runtime status, reviewer evidence or acceptance.

Only the developer may change and commit the checkpoint at a **clean Git boundary between source-bound runs**, after verifying the actual HEAD, stage and associated evidence. Do not edit it during initialization, execution, review, or pending promotion of a bound run. When the source baseline is uncertain, or the checkpoint is missing, invalid or stale, stop and reconcile with actual Git/evidence; never infer permission or acceptance from the umbrella's `current_focus`, the roadmap, or this index. Keep product acceptance navigation separate from the selected contributor-tooling workstream. Updating `STATE.json` does not start Aider, update Git refs, authorize a worker, accept a stage, or archive a spec.

## Ordinary development and optional delegation

**DEFAULT is the normal implementation path.** Read the active requirement or reproduced defect, edit the owning code directly, add regression tests, run `make check` and applicable native/integration checks, and obtain developer review. No stage packet, Aider, `STATE.json` change or orchestrator is required. A `verification-pending` spec records outstanding acceptance evidence, not an instruction to restart its implementation.

Only when the developer **explicitly selects delegated staged work**, a new stage-oriented sub-spec needs a normative change contract with stable local requirement/stage IDs, scope, protected paths, compatibility, failure semantics and independently observable acceptance cases, plus a same-directory advisory `<subspec-stem>-implementation-plan.md` with file-level guidance and test scenarios. The plan is not a separate spec, worker permission or acceptance record; move it with its spec on archival. Do not retrofit legacy specs with plans.

A validated packet, approved source base and working isolated executor would be prerequisites to actual delegation. **None is currently installed**: the [pipeline specification](active/subspecs/development-agent-pipeline.md) and its [advisory plan](active/subspecs/development-agent-pipeline-implementation-plan.md) remain an independent tooling design, not product P0 work. Never infer permission to execute Aider from this document or `STATE.json`.

## Active tree

Keep the active tree shallow:

```text
active/
    spec-<umbrella>.md
    subspecs/
        <bounded-focus>.md
```

Rules:

- processing checkpoint fields belong to `STATE.json`, never to this index;
- the umbrella defines the active product/system target;
- one sub-spec is the current implementation or acceptance focus;
- other sub-specs may remain active only while they define unresolved supporting or verification-pending constraints;
- completing one sub-spec does not complete the umbrella;
- do not create deeper trees without a concrete need.

The current bounded acceptance focus is `settlement-shortlist-workflow`; its implementation is complete and its remaining work is verification evidence. Desktop analysis, Desktop map, and user-preferences sub-specs remain active only because acceptance verification is incomplete.

## Planned tree

Reviewed future slices that are useful to preserve before implementation may live separately:

```text
planned/
    subspecs/
        <future-bounded-slice>.md
```

Rules:

- planned specs use `spec_status: planned`;
- a planned spec may reference an active umbrella as its parent, but it does not extend that umbrella's current acceptance gate;
- planned specs are design intent, not accepted implementation truth and not the current coding focus;
- before implementation starts, move the selected spec into `active/subspecs/`, change its status to `active`, and update the umbrella `current_focus`/roadmap coherently;
- do not keep duplicate copies in both planned and active trees.

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
2. A reviewed future slice may start under `docs/specs/planned/subspecs/` with `spec_status: planned`.
3. When implementation becomes current, move that slice into `docs/specs/active/subspecs/`, set `spec_status: active`, and update umbrella `current_focus`.
4. Record its parent and document lifecycle state in frontmatter.
5. For explicitly authorized staged delegation only, update/commit `STATE.json` at a developer-controlled between-run checkpoint and validate with `python3 tools/specs/validate_state.py`. Ordinary DEFAULT work must not change the checkpoint.
6. Reference stable feature IDs from `docs/FEATURES.md` in a `Feature scope` section.
7. Give important normative requirements stable local IDs when tests/reviews need them.
8. Implement and validate the slice while preserving root/local `AGENTS.md` invariants.
9. After developer acceptance, move stable behavior into current-state documentation.
10. Move the completed sub-spec to `docs/specs/archive/subspecs/`.
11. Archive the umbrella only when its own acceptance target is complete.

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
- `spec_status` — `planned`, `active`, `verification-pending`, or `archived`;
- `parent` — umbrella path for a sub-spec;
- `current_focus` — informational umbrella pointer to the product implementation/acceptance focus, not stage processing state.

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

## Acceptance and optional stage checkpoints

Ordinary implementation: verify affected requirements with independent test expectations and real checks, record unavailable evidence, obtain developer acceptance, update owning documentation and archive the completed spec (and any companion) together. No delegation-specific checkpoint is involved.

For separately authorized staged delegation, the developer first commits the reviewed normative spec/plan and validates the source-bound packet. Each approved worker attempt would require isolated edits, exact-byte tests/security checks, independent review and a developer decision before any guarded promotion or next stage. `STATE.json` may change **only between runs**. The pipeline and promotion operations remain unimplemented; their normative details belong in the [pipeline specification](active/subspecs/development-agent-pipeline.md). Source-bound reviewer PASS, checks and checkpoint state do not establish product acceptance.

## Active specifications

Umbrella:

- [`active/spec-initial-functional-product.md`](active/spec-initial-functional-product.md) — initial country-agnostic offline dataset builder + KMP Desktop/Android product umbrella.

Current acceptance focus:

- [`active/subspecs/settlement-shortlist-workflow.md`](active/subspecs/settlement-shortlist-workflow.md) — `CANDIDATES.IMPORT`, `FAVORITES.NOTEBOOK`, `FAVORITES.SNAPSHOTS`, `FAVORITES.EXPORT`, `ANALYSIS.EXPLANATION`, and `EXTERNAL.PROPERTY_SEARCH` workflow.

Verification-pending supporting increments:

- [`active/subspecs/desktop-analysis-workspace.md`](active/subspecs/desktop-analysis-workspace.md) — `ANALYSIS.PREFERENCES`, `ANALYSIS.RANKING`, `ANALYSIS.EXPLANATION`, and `PRESENTATION.ANALYSIS_WORKSPACE`; strict country-scale scoring and remaining configured acceptance checks are pending.
- [`active/subspecs/desktop-map.md`](active/subspecs/desktop-map.md) — `MAP.OFFLINE`, `MAP.RESULTS`, and Desktop renderer behavior; remaining acceptance verification pending.
- [`active/subspecs/development-agent-pipeline.md`](active/subspecs/development-agent-pipeline.md) — independent contributor-tooling spec; the selected checkpoint is tracked in [`STATE.json`](STATE.json) (A1 design; no runner), not in the product P0 acceptance map. Its [companion plan](active/subspecs/development-agent-pipeline-implementation-plan.md) is advisory.
- [`active/subspecs/user-preferences.md`](active/subspecs/user-preferences.md) — `SETTINGS.PERSISTENCE` for dataset-scoped search context; remaining cross-platform verification pending.

## Planned specifications

- [`planned/subspecs/settlement-interpretation-and-goal-profiles.md`](planned/subspecs/settlement-interpretation-and-goal-profiles.md) — planned `ANALYSIS.INTERPRETATION` layer for goal-oriented dimensions, traceable practical insights, and compact visual summaries over the existing transparent Preference/ranking engine. This is P1 and does not block current P0 acceptance.

## Recently completed sub-specifications

- [`archive/subspecs/kmp-modular-architecture-hardening.md`](archive/subspecs/kmp-modular-architecture-hardening.md) — accepted `PLATFORM.MODULAR_KMP` hardening track covering dependency cleanup, operational failures, shared persistence/query semantics, source decomposition, and physical `:core`/`:application`/`:presentation` modularization.

Create another architecture-hardening spec only when a concrete new structural requirement justifies one. Do not reactivate the archived spec as a permanent bucket for unrelated refactoring.

