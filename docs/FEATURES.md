---
type: Feature Catalog
title: OsmapDigger feature vocabulary
description: Stable human-readable feature identifiers used across specifications, documentation, tests, and important implementation boundaries.
---
# OsmapDigger feature vocabulary

## Purpose

This document owns the stable feature IDs used across OsmapDigger documentation and code references.

A feature ID names a durable product or platform capability. It does not name a requirement, one implementation task, or a specification lifecycle stage.

Keep these concepts separate:

- **Feature ID** — a long-lived capability name, for example `ANALYSIS.RANKING`.
- **Requirement ID** — a normative rule inside one specification, for example `R12`, `DA-R7`, or `SS-R9`.
- **Specification** — one planned or in-progress change, for example `settlement-shortlist-workflow`.

A feature may be introduced or refined by several specifications. Archiving a specification does not retire its feature IDs.

## Identifier format

Feature IDs use uppercase ASCII words.

- Use `.` between capability levels: `ANALYSIS.RANKING`.
- Use `_` inside a multi-word level: `TESTING.DETERMINISTIC_LOCAL`.
- Prefer two levels.
- Add a third level only when it materially improves navigation.
- Do not encode dates, versions, requirement numbers, implementation stages, Kotlin/Python packages, Gradle module names, or deployment topology.
- Once an ID is referenced outside this catalog, keep it stable. Rename it only through an explicit repository-wide vocabulary migration.

The dot is a naming hierarchy only. It does not define inheritance or authorization semantics.

## Usage rules

Use feature IDs when they improve navigation or make capability ownership clear.

Useful locations include:

- umbrella and sub-spec `Feature scope` sections;
- requirement maps;
- owning architecture or implementation documentation;
- feature-level tests or important application entry points when a stable capability reference helps;
- roadmap items that refine an already named capability.

Do not tag every helper, DTO, mapper, UI composable, SQL statement, or private implementation class. Avoid metadata noise.

Tests and documents must use IDs from this catalog. Do not invent local feature codes.

## Catalog

### Offline runtime and dataset lifecycle

- `RUNTIME.OFFLINE_FIRST` — core runtime behavior remains useful without a backend or public network after a dataset is installed. Owner: repository architecture and KMP runtime. Related umbrella requirements: R2, R6-R8.
- `DATASET.SCOPE` — country-agnostic whole-country, regional, or custom dataset scope. Owner: Geo Builder + runtime dataset model. Related umbrella requirement: R1.
- `DATASET.BUILD` — deterministic build-time transformation from local OSM PBF and configuration into runtime artifacts. Owner: `geo-builder/`. Related umbrella requirements: R3 and R6.
- `DATASET.PACKAGE` — validated published dataset directory/archive containing the analytical database, metadata, and optional map artifacts. Owner: `geo-builder/` + `geo-format/`. Related umbrella requirements: R6 and R15.
- `DATASET.IMPORT` — Desktop/Android installation and opening of generated dataset packages. Owner: platform hosts. Related umbrella requirement: R15.
- `DATASET.USER_BUILD` — future user-driven local dataset build workflow over explicit builder boundaries. Owner: future product work + `geo-builder/`. Related umbrella requirement: R16.
- `FORMAT.GEODATA` — versioned SQLite and metadata compatibility contract shared by Python writers and Kotlin readers. Owner: `geo-format/`.

### Metrics, search, and analysis

- `METRICS.GENERATION` — configuration-driven extraction and calculation of persisted geographic metrics. Owner: `geo-builder/`. Related umbrella requirement: R4.
- `METRICS.RUNTIME_CONTRACT` — dynamic metric definitions, values, and dataset-provided preference defaults consumed by runtime code. Owner: `geo-format/` + Geo Builder publication. Related umbrella requirement: R5.
- `SEARCH.FILTERING` — deterministic generic Required/min/max filtering over persisted metric IDs. Owner: `mobile/application`. Related umbrella requirement: R9.
- `SEARCH.DESCRIPTION` — deterministic human-readable description of active search/filter state. Owner: `mobile/presentation`. Related umbrella requirement: R10.
- `SEARCH.RADIUS` — center-settlement and exact-radius candidate restriction using shared geographic semantics. Owner: `mobile/application`. Related umbrella requirement: R11.
- `SEARCH.SETTLEMENTS` — local settlement discovery and detail inspection from generated data. Owner: `mobile/application` + platform repositories. Related umbrella requirement: R12.
- `ANALYSIS.PREFERENCES` — generic weighted Preference model and dataset-provided default preference parameters. Owner: `mobile/application`. Refines umbrella search/analysis requirements.
- `ANALYSIS.RANKING` — deterministic normalized quality, weighted coverage, score, and stable ranking of eligible settlements. Owner: `mobile/application`.
- `ANALYSIS.EXPLANATION` — deterministic score diagnostics and candidate-relative ranking explanations. Owner: `mobile/presentation` over application scoring results.
- `CANDIDATES.IMPORT` — explicit imported candidate source resolved to stable settlement IDs before analysis. Owner: `mobile/application` + platform import adapters.

### Favorites, settings, and external discovery

- `SETTINGS.PERSISTENCE` — application-owned, schema-versioned persistence of mutable user state separate from generated datasets. Owner: `mobile/application` + platform settings adapters.
- `FAVORITES.NOTEBOOK` — persistent dataset-scoped favorite settlements and notes, independent from current ranking and map selection. Owner: `mobile/application`.
- `FAVORITES.SNAPSHOTS` — frozen versioned analysis snapshots attached to Favorites and updated only by explicit action. Owner: `mobile/application`.
- `FAVORITES.EXPORT` — deterministic portable Favorites export with platform save/share adapters. Owner: `mobile/application` + platform hosts.
- `EXTERNAL.PROPERTY_SEARCH` — explicit user-triggered provider-configured external property search, including bounded batch query planning. Owner: `mobile/application` + platform browser adapters. Related umbrella requirement: R14.

### Map and presentation

- `MAP.OFFLINE` — local PMTiles basemap rendering without a remote tile backend. Owner: Geo Builder map publication + KMP/platform map runtime. Related umbrella requirement: R7.
- `MAP.RESULTS` — map visualization, selection, focus, and center-picking behavior for analytical settlements. Owner: `mobile/shared` + platform renderers. Related umbrella requirement: R13.
- `PRESENTATION.ANALYSIS_WORKSPACE` — shared explainable analysis UI for Required conditions, Preferences, ranked results, Favorites, and details. Owner: `mobile/shared` with deterministic helpers in `mobile/presentation`.
- `PRESENTATION.LOCALIZATION` — Russian/English application presentation without changing dataset/search semantics. Owner: `mobile/presentation` + shared UI.

### Platform architecture and validation

- `PLATFORM.KMP` — shared Kotlin Multiplatform product logic with explicit Desktop and Android adapters. Owner: `mobile/`. Related umbrella requirement: R8.
- `PLATFORM.DESKTOP` — Desktop JVM host, local filesystem/JDBC integration, browser integration, and supported map renderers. Owner: `mobile/desktopApp`.
- `PLATFORM.ANDROID` — Android host, app-private dataset installation, SQLite/SAF integration, browser intents, and lifecycle integration. Owner: `mobile/androidApp`.
- `PLATFORM.MODULAR_KMP` — enforced acyclic `:core` → `:application` → `:presentation` → `:shared` architecture with platform hosts above shared contracts. Owner: `mobile/` architecture.
- `TESTING.DETERMINISTIC_LOCAL` — default deterministic validation without public network, production credentials, or large downloaded datasets. Owner: repository testing. Related umbrella requirement: R17.

## Adding a feature ID

Add an ID only when the capability should remain meaningful beyond one patch or specification.

Before adding an ID:

1. Check this catalog for an existing capability with the same meaning.
2. Name the owning capability, not the current class, file, transport, renderer, or implementation stage.
3. Add the ID here first.
4. Reference it from the owning active specification or current-state document.
5. Keep requirement wording and specification lifecycle independent from the feature name.

Do not rename or remove a published feature ID in one document only. Treat that as a repository-wide vocabulary migration and update all references coherently.
