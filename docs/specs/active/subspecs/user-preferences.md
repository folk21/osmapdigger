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

The implementation now provides a shared immutable preferences contract, versioned dynamic-filter payload, dataset-aware restore validation, Desktop settings SQLite, Android app-private settings SQLite, and shared Compose startup/save wiring. Generated dataset SQLite remains read-only and separate.

The sub-spec remains active until configured Gradle tests/builds and Android runtime restoration are validated on the normal development toolchain.

## Requirements

### UP-R1 — separate user-owned storage

User preferences must be stored separately from generated dataset artifacts. `georisk.sqlite` remains
read-only runtime data and must not contain mutable user state.

### UP-R2 — shared persistence contract

`mobile/shared` must own immutable preference models and a small persistence interface. Shared code
must not depend on JDBC, Android SQLite, filesystem APIs, or another platform persistence API.

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

There is no existing persisted preference state to migrate. The first implementation therefore starts
with a fresh application-owned schema.

Persisted filter payloads must contain an explicit payload version before future incompatible JSON
shape changes are introduced. Database schema evolution must be owned by the preferences adapter, not
by `geo-format`.

## Validation

Acceptance requires:

1. shared tests for preference/filter model serialization or equivalent persistence mapping;
2. Desktop round-trip tests covering save, repository recreation, and load;
3. tests for missing state, unknown metric IDs, and unavailable saved center settlement;
4. Android host/adapter validation for the same shared semantics when Android persistence is wired;
5. no writes to installed dataset directories or `georisk.sqlite`;
6. normal search behavior remains unchanged when no preferences have been saved.

## Implementation tasks

Implemented in this increment:

1. Add immutable shared preference/search-context models.
2. Add a small shared `UserPreferencesRepository` contract.
3. Define the versioned dynamic-filter persistence representation.
4. Implement Desktop application-owned SQLite storage under `~/.osmapdigger/settings/`.
5. Restore state after the matching dataset and metric definitions are available.
6. Persist relevant search-context changes through application state rather than directly from UI controls.
7. Implement the Android app-private adapter using the same shared contract.
8. Add focused shared and Desktop tests; Android adapter compilation/device validation remains part of acceptance.

After configured validation succeeds, update any final validation notes, archive this sub-spec, and select the next implementation focus under the umbrella specification.
