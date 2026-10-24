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

## Staged specifications and MAIN_MODEL / WORKER_MODEL delegation

DEFAULT is the normal implementation path for product changes. `GENSPEC` prepares a normative specification and a separate advisory implementation plan without changing application code; `REVIEW` examines an actual diff and available verification evidence. Ordinary `DEFAULT` product work does not require Aider, `WORKER_MODEL`, or changes to `STATE.json`. Product-stage progress is tracked by the owning specification and verified source/test evidence, independently of the contributor-pipeline checkpoint.

For **new** implementation slices intentionally delegated across multiple sessions, `MAIN_MODEL` must write both:

- a **normative** sub-spec, owning stable local requirement IDs, ordered `stage` IDs, affected modules, stage-specific allowed/protected path boundaries, failure/compatibility semantics and independent acceptance criteria;
- a **same-directory advisory** `<subspec-stem>-implementation-plan.md`, with module and API context, candidate file-by-file edits, expected algorithms/signatures, test fixtures, positive/negative/boundary/regression scenarios, canonical/configured gates and unresolved decisions.

`stage` is a durable machine identity for an implementation slice, not another Feature ID. The plan must not have `document_role: subspec`, its own `spec_status`, independent acceptance status, or authority to override the normative spec. Mark unknown paths and decisions clearly; a worker may not turn a candidate file or free-form task note into edit authorization. New staged plans follow their normative spec on active-to-archive **paired moves**. Legacy specs without staged delegation are not required to gain plans retroactively.

The developer commits a reviewed spec/plan baseline on one feature branch before any source-bound worker run. Packet, provider, path, branch, risk and check authorization must be explicit. ChatGPT `GENSPEC` produces designs only; `DEFAULT` does not silently invoke a worker; `REVIEW` needs actual final diff and bound check/reviewer evidence. An agent's implementation, independent reviewer PASS, successful check or stage commit does not accept a spec. Developer acceptance, owning-doc updates and archival are separate decisions.

The contributor-tooling design is [`active/subspecs/development-agent-pipeline.md`](active/subspecs/development-agent-pipeline.md), with [advisory implementation guidance](active/subspecs/development-agent-pipeline-implementation-plan.md). It is orthogonal to product P0 acceptance despite the mandatory current umbrella-parent navigation link. The repository provides a validated `STATE.json` **checkpoint**, but it does **not** yet provide an Aider stage controller, independent reviewer adapter, worker execution, or guarded promotion.

## Active tree

Keep the active tree shallow:

```text
active/
    spec-<umbrella>.md
    subspecs/
        <bounded-focus>.md
```

Rules:

- machine-readable processing checkpoint status belongs only to `STATE.json`, not this index;
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
5. Update/commit `STATE.json` only at a developer-controlled between-run Git checkpoint; validate it with `python3 tools/specs/validate_state.py`. The umbrella `current_focus`, roadmap, and this index are navigation and product-acceptance references, not processing state or authorization.
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

## Evidence and delegated stage lifecycle

1. Establish requirements and observable success/failure oracles independently of the worker; keep module ownership explicit and escalate changes to `geo-format`, platform/native boundaries and unapproved dependencies.
2. Review the normative spec and companion plan; choose or create one `feature/*` branch, commit the accepted design and validated `STATE.json` at a clean Git checkpoint (only between source-bound runs).
3. Only after the pipeline stages are implemented and accepted, developer-authorize **one** source-bound stage packet, isolated worker attempt, exact-byte focused and canonical checks, security policy gates and independent review.
4. Failed/blocked/repaired attempts retain evidence; changed bytes require fresh checks/review. Promotion is a separate human-authorized guarded Git operation, not reviewer acceptance. Developer manually transitions to the next stage after verifying source identity and predecessor receipt, then updates `STATE.json` only between runs.
5. After all stages, perform whole-feature requirement coverage, configured/native gates as applicable, final MAIN_MODEL review, developer acceptance and manual PR/merge. Synchronize owning documentation and archive normative spec + companion together, never on a stage PASS alone.

The current processing checkpoint is [`STATE.json`](STATE.json); stage runner availability is defined separately by implemented tooling. The actual verification command matrix is owned by [`../TESTS.md`](../TESTS.md). A workflow described here is not a claim of an installed executor or completed gate.

## Active specifications

Umbrella:

- [`active/spec-initial-functional-product.md`](active/spec-initial-functional-product.md) — initial country-agnostic offline dataset builder + KMP Desktop/Android product umbrella.

Current product development focus:

- [`active/subspecs/settlement-interpretation-and-goal-profiles.md`](active/subspecs/settlement-interpretation-and-goal-profiles.md) — P1 `ANALYSIS.INTERPRETATION` staged normative contract and [advisory implementation plan](active/subspecs/settlement-interpretation-and-goal-profiles-implementation-plan.md); P0 verification remains open.

P0 verification-pending acceptance focus:

- [`active/subspecs/settlement-shortlist-workflow.md`](active/subspecs/settlement-shortlist-workflow.md) — `CANDIDATES.IMPORT`, `FAVORITES.NOTEBOOK`, `FAVORITES.SNAPSHOTS`, `FAVORITES.EXPORT`, `ANALYSIS.EXPLANATION`, and `EXTERNAL.PROPERTY_SEARCH` workflow.

Verification-pending supporting increments:

- [`active/subspecs/desktop-analysis-workspace.md`](active/subspecs/desktop-analysis-workspace.md) — `ANALYSIS.PREFERENCES`, `ANALYSIS.RANKING`, `ANALYSIS.EXPLANATION`, and `PRESENTATION.ANALYSIS_WORKSPACE`; strict country-scale scoring and remaining configured acceptance checks are pending.
- [`active/subspecs/desktop-map.md`](active/subspecs/desktop-map.md) — `MAP.OFFLINE`, `MAP.RESULTS`, and Desktop renderer behavior; remaining acceptance verification pending.
- [`active/subspecs/development-agent-pipeline.md`](active/subspecs/development-agent-pipeline.md) — independent contributor-tooling spec; the selected checkpoint is tracked in [`STATE.json`](STATE.json) (A1 design; no runner), not in the product P0 acceptance map. Its [companion plan](active/subspecs/development-agent-pipeline-implementation-plan.md) is advisory.
- [`active/subspecs/user-preferences.md`](active/subspecs/user-preferences.md) — `SETTINGS.PERSISTENCE` for dataset-scoped search context; remaining cross-platform verification pending.

## Planned specifications

No separate planned normative sub-spec is currently selected. The interpretation sub-spec has moved to `active/subspecs/`; no duplicate planned copy should remain.

## Recently completed sub-specifications

- [`archive/subspecs/kmp-modular-architecture-hardening.md`](archive/subspecs/kmp-modular-architecture-hardening.md) — accepted `PLATFORM.MODULAR_KMP` hardening track covering dependency cleanup, operational failures, shared persistence/query semantics, source decomposition, and physical `:core`/`:application`/`:presentation` modularization.

Create another architecture-hardening spec only when a concrete new structural requirement justifies one. Do not reactivate the archived spec as a permanent bucket for unrelated refactoring.

