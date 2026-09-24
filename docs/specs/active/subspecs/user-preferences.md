---
type: Specification
title: User preferences persistence
description: Sub-spec for local persistence and restoration of the dataset-scoped search context.
document_role: subspec
spec_status: verification-pending
parent: ../spec-initial-functional-product.md
---
# User preferences persistence

## Status

Implementation complete; remaining cross-platform validation is tracked as verification pending.

Parent specification: [`../spec-initial-functional-product.md`](../spec-initial-functional-product.md).

The implementation is no longer the current coding focus. Desktop persistence and restore behavior
have been exercised successfully; Android runtime validation remains pending before archival. This
sub-spec continues to document that bounded change until its remaining validation is complete.

## Feature scope

- `SETTINGS.PERSISTENCE` — application-owned schema/versioned persistence of mutable search context.
- `SEARCH.RADIUS` — persisted center/radius semantics.
- `SEARCH.FILTERING` — persisted dynamic Required filter state.
- `PLATFORM.KMP` — shared persistence contract with platform-specific storage adapters.
- `TESTING.DETERMINISTIC_LOCAL` — deterministic persistence/restore coverage.

Feature identifiers are defined in [`../../../FEATURES.md`](../../../FEATURES.md).

## Goal

Persist the user's current dataset-scoped search context locally and restore it after application
restart without coupling shared UI or domain logic to a platform database API.

The first increment persists:

- the selected dataset identifier;
- the selected center settlement;
- the radius;
- dynamic metric filter conditions.

## Relationship to the umbrella specification

This work builds on the existing runtime boundaries and search model, especially R5, R8, R9, R11,
and R17 of the parent specification. R17 applies to the persistence implementation through
deterministic, network-free default tests.

Persistence is a follow-up product capability and is not retroactively added to the acceptance
criteria of the original initial vertical slice.

## Current state

Application-owned dataset-scoped search-context persistence is implemented on Desktop and Android behind the shared application contract. Generated dataset SQLite remains read-only and separate. Stable schema/migration history and platform wiring belong in [`../../../../mobile/IMPLEMENTATION.md`](../../../../mobile/IMPLEMENTATION.md) and [`../../../IMPLEMENTATION.md`](../../../IMPLEMENTATION.md).

This sub-spec remains `verification-pending` for the remaining Android runtime restore validation.

## Requirement map

| Requirement | Feature ID | Purpose |
|---|---|---|
| UP-R1-UP-R3, UP-R7, UP-R9-UP-R10 | `SETTINGS.PERSISTENCE` | Separate application-owned state, dataset scope, tolerant restore, schema lifecycle |
| UP-R4-UP-R5 | `SEARCH.RADIUS`, `SETTINGS.PERSISTENCE` | Center identity and radius semantics |
| UP-R6 | `SEARCH.FILTERING`, `SETTINGS.PERSISTENCE` | Dynamic filter persistence |
| UP-R8 | `PLATFORM.KMP` | Shared contract with platform-owned storage |
| UP-R11 | `TESTING.DETERMINISTIC_LOCAL` | Deterministic persistence behavior |

## Requirements

### UP-R1 — separate user-owned storage

User preferences must be stored separately from generated dataset artifacts. `georisk.sqlite` remains
read-only runtime data and must not contain mutable user state.

### UP-R2 — application persistence contract

`mobile/application` must own immutable preference models and the small persistence interface. `mobile/shared` may consume that contract but must not depend on JDBC, Android SQLite, filesystem APIs, or another platform persistence API.

### UP-R3 — dataset-scoped search context

Persisted state must identify the dataset it belongs to. State from one dataset must not be applied
to another dataset merely because the application opened successfully.

### UP-R4 — center settlement identity

The selected center must be persisted by stable `settlement_id`. A display name may also be stored
for diagnostics or presentation, but it is not the identity key.

### UP-R5 — radius semantics

The optional radius must preserve the existing `SearchRequest` semantics. Absence of a radius must
remain distinguishable from a numeric radius.

### UP-R6 — dynamic filter persistence

Filters must be persisted by stable `metric_id` with optional minimum and maximum bounds. Persistence
must not introduce category-specific fields such as `forest_enabled`.

A compact versioned JSON payload may be used for the dynamic filter collection so additive metrics do
not require a settings-database schema change.

### UP-R7 — tolerant restore

Restore must occur only after the referenced dataset and its metric catalog are available. Unknown or
removed metric IDs must not prevent application startup; unsupported filter entries are ignored while
valid entries are restored deterministically.

If the saved center settlement no longer exists in the opened dataset, the center and radius
constraint must not be silently applied to another settlement.

### UP-R8 — platform ownership

Desktop owns the JVM filesystem path and SQLite implementation. The target Desktop database location
is `~/.osmapdigger/settings/preferences.sqlite`.

Android owns its app-private persistence implementation. This sub-spec does not require Room; the
implementation may use the Android SQLite API or another small local adapter while preserving the same
shared contract.

### UP-R9 — independent settings schema lifecycle

The preferences database is application-owned and is not part of `geo-format`. Changes to it must not
increment `geo-format/VERSION`. The preferences store must have a simple explicit schema version or
migration strategy before incompatible changes are introduced.

### UP-R10 — current state, not history

The initial implementation stores the current restorable search context. Search history, multiple
saved searches, favorites, notes, accounts, and synchronization are outside this increment.

### UP-R11 — deterministic persistence behavior

Preference persistence and restoration must be testable without network access or generated geographic
data. Repositories must define deterministic behavior for missing storage, empty state, and malformed
or unsupported filter payloads.

## Scenarios

### UP-S1 — restart with the same dataset

A user opens a dataset, selects a center settlement, sets a radius and several metric ranges, closes
the application, and starts it again. The same dataset-scoped search context is restored.

### UP-S2 — different dataset

Saved state references dataset A, but dataset B is opened. Dataset A's center and filters are not
applied to dataset B.

### UP-S3 — metric removed from a rebuilt dataset

A saved filter references a metric ID that the opened dataset no longer provides. The application
restores the remaining valid filters and stays usable.

### UP-S4 — center settlement unavailable

A saved center settlement is absent from the current version of the dataset. The application clears
the invalid center/radius constraint rather than choosing a settlement by name.

## Non-goals

This increment does not implement:

- search history;
- named saved searches;
- favorites or settlement notes;
- user profiles;
- cloud synchronization;
- cross-device migration;
- changes to generated dataset SQLite or `geo-format`.

## Design constraints

- Keep mutable user data separate from generated read-only geographic artifacts.
- Keep persistence APIs out of shared UI state and domain algorithms.
- Prefer stable IDs over display strings.
- Preserve dynamic filter semantics and tolerate additive dataset evolution.
- Keep the first persistence schema intentionally small.
- Do not introduce a cross-platform persistence framework unless the platform implementations justify it.

## Compatibility / migration

The settings database is application-owned and evolves independently from `geo-format`. Historical migration semantics must remain immutable; later changes require explicit forward migrations.

Persisted dynamic filter/search-context payloads must remain versioned and tolerant of additive dataset evolution. Unknown metric IDs or an unavailable saved center must degrade according to UP-R7 rather than corrupting startup or being silently rebound by display name.

## Validation

Acceptance requires:

1. shared tests for preference/filter model serialization or equivalent persistence mapping;
2. Desktop round-trip tests covering save, repository recreation, and load;
3. tests for missing state, unknown metric IDs, and unavailable saved center settlement;
4. Android host/adapter validation for the same shared semantics when Android persistence is wired;
5. no writes to installed dataset directories or `georisk.sqlite`;
6. normal search behavior remains unchanged when no preferences have been saved.

## Remaining acceptance

1. Run the configured Android host/build checks for the current persistence adapter.
2. Verify on Android that reopening the same dataset restores the saved center/radius/filter context and that opening another dataset does not apply it.
3. Verify unavailable center and removed-metric restore behavior on the current platform implementation.
4. Record the accepted evidence in the owning test/implementation docs and archive this sub-spec.

Completed schema versions, adapter classes, and migration mechanics remain documented in the current-state implementation docs rather than repeated here.
