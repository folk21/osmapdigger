---
type: Specification
title: OsmapDigger initial functional product specification
description: Active umbrella specification for the initial country-agnostic offline product and dataset pipeline.
document_role: umbrella
spec_status: active
current_focus: subspecs/settlement-shortlist-workflow.md
---
# OsmapDigger initial functional product specification

## Status

Active specification — implementation in progress / acceptance pending.

This spec captures the initial product slice discussed before code generation. Much of the implementation now exists, but the spec remains active until real PBF, PMTiles, Desktop, and Android end-to-end validation is completed on a configured workstation.

After acceptance, move stable behavior into owning implementation/architecture docs as needed and archive this spec.

## Active implementation focus

Current implementation sub-spec:

- [`subspecs/settlement-shortlist-workflow.md`](subspecs/settlement-shortlist-workflow.md) — persistent Favorites/notebook, imported candidate lists, frozen analysis snapshots, export/share, and batch external searches.

Completed architecture-hardening track:

- [`../archive/subspecs/kmp-modular-architecture-hardening.md`](../archive/subspecs/kmp-modular-architecture-hardening.md) — iterations 1–7 were accepted and archived after establishing the current `:core` → `:application` → `:presentation` → `:shared` dependency direction and configured Kotlin validation.

The implemented [`subspecs/desktop-analysis-workspace.md`](subspecs/desktop-analysis-workspace.md) remains verification-pending while its strict country-scale scored acceptance and remaining configured checks are completed.

Previous implemented sub-specs such as [`subspecs/desktop-map.md`](subspecs/desktop-map.md) and
[`subspecs/user-preferences.md`](subspecs/user-preferences.md) remain verification-pending until their
remaining acceptance checks are recorded.

This document remains the active umbrella specification. The linked current-focus sub-spec refines one
bounded implementation increment and does not replace or supersede the umbrella requirements.

## Goal

Deliver a country-agnostic offline-first application and dataset build pipeline that can:

- turn a local OSM PBF into an installable analytical database and offline vector map;
- let Desktop and Android users load the generated package;
- construct transparent visual numeric filters without an LLM;
- search settlements locally by arbitrary generated metrics and optional center/radius;
- inspect matching settlements on the offline map;
- open explicit external property searches for a selected settlement.

The first production-oriented package is expected to cover Belarus. The repository uses Andorra as a small real integration dataset because it is cheap to process during development. Neither country may be hardcoded into runtime behavior.

## Current state

The repository contains a functional vertical-slice implementation:

- Python config-driven PBF/metric pipeline;
- normalized SQLite schema and package metadata;
- optional tilemaker PMTiles generation;
- KMP shared search/filter/map UI;
- Desktop JDBC package loading;
- Android SQLite/ZIP import;
- external search actions.

Synthetic Python tests and platform-independent Kotlin checks were performed during generation. Real Pyrosm/tilemaker/full Gradle end-to-end validation remains the acceptance blocker.

## Requirements

### R1 — country-agnostic dataset scope

The runtime must operate on dataset metadata rather than country-specific branches.

A dataset may represent:

- a whole country when practical;
- a regional PBF for a large country;
- a custom geographic extract.

If a whole-country package is already available, searching inside its internal regions must be runtime filtering rather than requiring pre-generated packages for every administrative subdivision.

### R2 — local source/generated data isolation

Raw OSM files and generated geographic artifacts must live in ignored local paths and must not enter Git or normal source snapshots.

At minimum this applies to `.osm.pbf`, `.pmtiles`, generated `.sqlite`, `.omd.zip`, `data/source/osm/`, and `data/generated/`.

### R3 — build-time PBF processing

Python must own heavy OSM processing. Runtime Android/Desktop code must not parse PBF.

The builder must support:

- direct practical-size PBF datasets;
- optional configured boundary extraction;
- named settlement extraction from configured `place=*` types;
- OSM nodes/ways/relations where supported by Pyrosm;
- configurable feature-category extraction;
- data-only builds without map tooling.

### R4 — generated analytical metrics

The builder must generate numeric metrics useful for settlement filtering.

Supported metric families must include:

- nearest distance;
- counts within configured radii;
- polygon coverage percentages within configured radii.

The initial default filter set must prioritize useful nature, agriculture, risk, transport, infrastructure, and recreation metrics while preserving additional configured categories for optional filters.

### R5 — dynamic metric/filter contract

Adding an additive numeric OSM-derived filter must generally be possible by changing builder configuration and rebuilding a dataset rather than changing shared Kotlin UI code.

Runtime SQLite must persist metric definitions containing stable IDs, titles, grouping, units, measure type, default visibility, and presentation order.

A missing metric value means unavailable/unknown, not numeric zero.

### R6 — generated runtime package

A published package must contain:

- `georisk.sqlite`;
- `metadata.json`;
- `style.template.json`;
- `<dataset-id>.pmtiles` when map generation is enabled.

The builder should additionally publish a portable `.omd.zip` containing the runtime artifacts.

Publication must use staging/validation so a failed build does not replace a previously published valid package with partial output.

### R7 — offline vector map

The builder must be able to generate a PMTiles vector basemap from the OSM source through tilemaker or an equivalent configured backend.

The runtime map must use the generated local map artifact; remote tile services must not be required for core map browsing.

Map rendering and analytical filtering must remain separate responsibilities.

### R8 — KMP Desktop and Android runtime

Initial runtime targets are:

- Desktop JVM;
- Android.

Shared Kotlin should own reusable domain/search/filter-description/UI logic. Platform hosts should own filesystem, SQLite APIs, package import, browser opening, and lifecycle.

### R9 — visual filter builder

The UI must provide numeric min/max fields for each active metric. Users must be able to:

- start with a dataset-provided default filter set;
- add other generated metrics dynamically;
- remove filters;
- leave either min or max empty;
- combine many conditions.

An empty visible range row must have no filtering effect.

### R10 — human-readable filter description

The runtime must render the structured filter state into understandable text using deterministic templates/metric metadata. This behavior must not require an LLM.

### R11 — center/radius search

The user must be able to select a settlement as a center and optionally apply a maximum radius.

Search should use an efficient coarse coordinate range in SQLite and exact shared-code geographic distance filtering afterward so both Android/Desktop behave consistently without requiring spatial SQLite extensions.

### R12 — settlement search and details

Search must return only settlements satisfying all effective requested metric conditions plus optional radius.

Selecting a settlement must expose:

- names and coordinates;
- place type/population when available;
- available generated metrics grouped for inspection;
- map selection state.

### R13 — map result visualization

Matching settlements must be rendered above the offline basemap as a runtime overlay. The selected settlement must be visually distinguishable from other results.

### R14 — external property search

A selected settlement must offer explicit external search-engine actions. Dataset metadata/config may provide:

- optional site restriction such as `kufar.by`;
- default search terms.

The application must not scrape the property portal as part of this initial feature.

### R15 — package import and safety

Desktop must be able to open a generated directory and/or portable package. Android must be able to import a portable package into app-private storage.

ZIP extraction must reject path traversal entries. Runtime SQLite should be opened read-only where supported.

### R16 — future user-built dataset path

The architecture must preserve a future Desktop workflow where a user selects an arbitrary local PBF and invokes the existing builder with progress/warnings.

Long processing time must be expected and communicated. Direct Android PBF generation is not required initially.

### R17 — testability

Default automated tests must not require network access, production PBF data, Docker/tilemaker, or Android SDK.

Real PBF and PMTiles generation must have explicit integration/acceptance commands using the local Andorra fixture.

## Scenarios

### S1 — whole-country package

Given a practical whole-country PBF, the developer builds one package. A user searches anywhere inside that installed package without separately generated subregion packages.

Exercises: R1, R3, R4, R6, R7, R8.

### S2 — regional package for a large country

Given a regional PBF (or a large source plus configured boundary), the builder generates a smaller installable package whose runtime behavior is identical to a whole-country package.

Exercises: R1, R3, R6, R16.

### S3 — transparent real-estate search

A user chooses a city as center, sets radius 50 km, forest 0–2 km, beach 0–5 km, railway station <= 20 km, farmyard >= 5 km, then reads the generated sentence describing that query and runs it locally.

Exercises: R5, R9, R10, R11, R12.

### S4 — map inspection

After S3, result points appear over the offline PMTiles map. Selecting one highlights it and opens its metrics/details.

Exercises: R7, R12, R13.

### S5 — external listing discovery

From a selected settlement, the user taps Google or Yandex and opens a query containing the settlement name and optional dataset-configured portal restriction.

Exercises: R14.

### S6 — Android offline import

A user imports the same generated `.omd.zip` on Android. The app installs it locally, opens SQLite, displays the local map, and performs the same structured filtering without a backend.

Exercises: R6, R8, R15.

### S7 — add a new numeric category

A developer adds an OSM-derived category in `metrics.toml`, rebuilds the dataset, and the new metric appears through **Add filter** without a shared-UI category branch.

Exercises: R4, R5.

## Non-goals

The initial accepted slice does not require:

- an LLM or natural-language query parser;
- cloud backend/accounts/synchronization;
- direct embedded property-listing ingestion;
- parcel/cadastral analysis;
- saved favorites/notes/searches;
- polished production cartography with complete offline glyph/sprite packaging;
- Android-side Pyrosm/tilemaker execution;
- non-OSM environmental sources.

## Design constraints

- Offline core behavior is preferred over cloud convenience.
- Search must remain deterministic and inspectable.
- Builder/runtime contracts must be versioned.
- Map and search must remain separate artifacts.
- Country/portal-specific details belong in generated metadata/configuration.
- Large local geographic data must stay out of Git and normal LLM source snapshots.
- Shared KMP code must not import platform database/filesystem APIs.

## Compatibility / migration

The initial persisted format is version `1` in `geo-format/VERSION`.

Before changing format semantics, inspect both Python writers and Android/Desktop readers. Incompatible changes require a format-version increment and coordinated tests/migration policy. Additive metric rows do not inherently require a schema version bump when existing semantics remain unchanged.

## Validation

Acceptance requires all of the following on a configured development workstation:

1. `make check` passes.
2. `make build-andorra-data` produces a valid real SQLite package from the local Andorra PBF.
3. `make build-andorra` produces a non-empty PMTiles artifact and validates the package.
4. Desktop opens that generated package, displays the map, loads dynamic filters, executes multi-condition search, and selects/details a result.
5. Android builds and imports the same `.omd.zip`, then performs local filtering/map display.
6. A configured additive test metric/category can appear without shared UI source modification.
7. `git status` remains clean with raw/generated geographic data present locally.
8. ZIP traversal tests or manual hostile-entry checks confirm package import boundaries.

Detailed commands belong in [`../../TESTS.md`](../../TESTS.md).

## Implementation tasks

Most initial implementation tasks now exist in code. Remaining acceptance/follow-up work for this active spec:

- run real Pyrosm integration on Andorra and fix source-tag/version mismatches discovered by that run;
- run tilemaker PMTiles generation and verify style/source-layer compatibility;
- complete full Gradle Desktop/Android builds using actual current dependencies;
- exercise Android import on emulator/device;
- verify OSM attribution and local map operation;
- record acceptance results in implementation/tests docs;
- archive this spec only after the vertical slice is accepted.
