# Change specifications

`docs/specs/` contains specifications for significant planned or in-progress changes to OsmapDigger. A spec defines the intended delta/acceptance target before that work is considered complete; it is **not** canonical documentation of what the repository already does.

Current product and implementation truth remains in owning documents such as [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../IMPLEMENTATION.md`](../IMPLEMENTATION.md), [`../CONFIGURATION.md`](../CONFIGURATION.md), [`../USAGE.md`](../USAGE.md), plus subproject `IMPLEMENTATION.md` files.

## When to create a spec

Create an active spec when a change:

- is cross-cutting across Python/format/runtime;
- changes a persisted/public compatibility contract;
- introduces a substantial product capability;
- is large enough that requirements and validation must survive across multiple coding sessions.

Small bug fixes, local refactors, narrow documentation improvements, and routine dependency maintenance do not require a separate spec.

## Lifecycle

Only one implementation specification should be active at a time. A significant follow-up that is
already architecturally useful but not yet being implemented may live under `docs/specs/planned/`.
Planned documents define direction and constraints but do not override the current active spec.

1. Draft future significant work under `docs/specs/planned/` when useful for sequencing or architecture.
2. Move the selected change to `docs/specs/active/<change>.md` before or at the start of implementation.
3. Define status, goal, current state, stable requirements, scenarios, non-goals, design constraints, compatibility/migration concerns, validation, and implementation tasks.
4. Give important requirements stable IDs such as `R1` so tests/reviews can refer to them.
5. Implement while preserving root/local `AGENTS.md` invariants.
6. After acceptance, update owning current-state documentation with the stable result.
7. Move the completed spec to `docs/specs/archive/`.

Archived specs preserve historical design intent but are not current source of truth. `concat_osmapdigger.sh` excludes archived specs so routine LLM context contains current code/docs plus active intended work.

## Suggested spec structure

```text
# <Change name>

## Status
## Goal
## Current state
## Requirements
## Scenarios
## Non-goals
## Design constraints
## Compatibility / migration
## Validation
## Implementation tasks
```

## Active specs

- [`active/initial-functional-product.md`](active/initial-functional-product.md) — initial country-agnostic offline dataset builder + KMP Desktop/Android search/map vertical slice; implementation exists but requires full real-toolchain acceptance before the spec can be archived.

## Planned specs

- [`planned/user-preferences.md`](planned/user-preferences.md) — local persistence of dataset-scoped search context across Desktop and Android; planned follow-up, not yet active.
