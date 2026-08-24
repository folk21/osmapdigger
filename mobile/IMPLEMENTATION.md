---
type: Implementation
title: Mobile/runtime implementation
description: Current shared KMP runtime and Desktop/Android adapter implementation.
---
# Mobile/runtime implementation

## Scope

This document describes the current Kotlin Multiplatform implementation under `mobile/`. Stable system boundaries remain in [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md); persisted SQLite/metadata semantics are owned by [`../geo-format/`](../geo-format/).

## Module layout

| Module | Current responsibility |
|---|---|
| `shared` | Domain models, search/preferences contracts, exact-radius search, filter summary, property-link generation, GeoJSON overlay, shared Compose/MapLibre UI |
| `desktopApp` | JVM app host, dataset JDBC SQLite, settings SQLite, filesystem/ZIP loading, Desktop browser integration |
| `androidApp` | Android app host, dataset/settings SQLite, SAF ZIP import, app-private installation, browser intents |

`shared/commonMain` contains no Android/JDBC/filesystem implementation APIs.

## Shared domain model

`domain/Models.kt` defines:

- `GeoPoint` — WGS84 coordinate;
- `DatasetInfo` — runtime dataset identity/initial camera/map availability;
- `PreferredDirection` — descriptive lower/higher preference metadata;
- `MetricDefinition` — one persisted numeric filter definition;
- `Settlement` — one searchable settlement point;
- `SearchCondition` — optional min/max range for one metric ID;
- `SearchRequest` — center, radius, metric conditions, result limit;
- `MetricValue` — hydrated metric definition/value;
- `SettlementDetails` — settlement plus all available metrics.

Models are intentionally immutable and do not know SQL/OSM selector semantics.

## Runtime contracts

`runtime/RuntimeContracts.kt` defines `GeoRepository` as the shared data boundary.

Platform implementations provide:

- dataset metadata;
- metric catalog;
- settlement-name search;
- SQL-reduced search candidates;
- detailed settlement hydration.

`OsmapDiggerRuntime` bundles dataset-scoped repository/map/browser dependencies. The platform host separately creates one application-scoped `UserPreferencesRepository` and injects it into `OsmapDiggerApp`.

## User preferences

`preferences/UserPreferences.kt` defines the immutable current search-context model, the small persistence interface, a versioned JSON codec for dynamic metric conditions, and restore validation.

Restore is dataset-scoped. Metric conditions are retained only when their stable IDs still exist in the opened metric catalog. The saved center is resolved only by stable settlement ID; no display-name fallback is used.

`OsmapDiggerApp` loads preferences after dataset metadata and metric definitions, then enables persistence only after restore completes so default filters cannot overwrite saved state during startup. Changes to center, radius, or filter state are saved through the repository rather than directly by individual controls.

## Search orchestration

`search/SearchService.kt` is intentionally small and deterministic.

```mermaid
sequenceDiagram
    participant UI as OsmapDiggerApp
    participant S as SearchService
    participant G as GeoMath
    participant R as GeoRepository

    UI->>S: search(SearchRequest)
    S->>G: boundingBox(center, radius)
    S->>R: searchCandidates(conditions, bounds)
    R-->>S: SQLite candidates
    S->>G: distanceKm(center, candidate)
    S-->>UI: exact-radius results
```

This keeps SQL spatial requirements minimal and behavior consistent across Android/JVM.

## Filter summaries

`FilterSummaryBuilder` renders the current structured request into readable text. It uses persisted metric title/unit metadata and therefore requires no category-specific wording branches for normal range metrics.

It is presentation logic only: the generated sentence does not become an executable query language.

## External property links

`external/PropertySearchLinks.kt` builds percent-encoded Google/Yandex URLs from:

- provider;
- selected settlement;
- dataset-provided optional site restriction;
- dataset-provided terms.

It does not perform HTTP requests itself.

## Map overlay

`map/GeoJson.kt` serializes search results into a small in-memory GeoJSON FeatureCollection.

`ui/MapPanel.kt`:

- loads the platform-resolved local style through `BaseStyle.Json`;
- renders result points in one circle layer;
- renders the selected settlement in a separate highlighted layer;
- moves the camera toward a newly selected settlement.

The basemap remains PMTiles; result overlays are runtime GeoJSON and are not written back to tiles.

## Shared Compose UI

`ui/OsmapDiggerApp.kt` is the common entry point.

The UI has two top-level states:

1. no dataset — explain that a generated package is required and offer import;
2. loaded dataset — load metadata/metric definitions and show search/map UI.

Wide layouts show search/details and map side by side. Narrow layouts use Search/Map tabs.

`ui/SearchPane.kt` implements:

- center settlement lookup;
- radius input;
- dynamic default/additional filters;
- deterministic filter description;
- local search action;
- result cards;
- settlement detail metrics;
- explicit external property searches.

The UI never constructs SQL.

## Desktop adapter

### `JdbcGeoRepository`

Uses Xerial SQLite JDBC.

`searchCandidates()` builds SQL with:

- optional latitude range;
- optional longitude range;
- one `EXISTS` subquery per effective metric condition;
- lower/upper comparisons only when supplied;
- deterministic name ordering and limit.

Using `EXISTS` means a missing metric row fails a condition rather than being treated as zero.

### `SqliteUserPreferencesRepository`

Stores the current search context at `~/.osmapdigger/settings/preferences.sqlite` using a single-row application-owned schema with `PRAGMA user_version = 1`. Connections are short-lived and preference I/O runs on `Dispatchers.IO`.

### `DesktopDataset`

`open(directory)` parses metadata, opens dataset SQLite, resolves optional PMTiles into the style template, and creates the dataset-scoped `OsmapDiggerRuntime`. The Desktop host owns one separate preferences repository for the application lifetime.

`DesktopDatasetChooser` can open a package directory or securely extract a ZIP into `~/.osmapdigger/datasets/`. Normalized entries must stay under the destination root.

### Desktop MapLibre runtime

`shared/src/desktopMain/.../MapPanel.desktop.kt` is the default Desktop `actual` map surface. It renders the local package style, result/selection GeoJSON overlays, camera focus, and attribution on MapLibre Compose native hosts, and otherwise provides the non-fatal fallback. Shared UI can accept a small `PlatformMapSurface` override from the Desktop application host.

`desktopApp/build.gradle.kts` owns the platform-native runtime selection for macOS Apple Silicon Metal, Linux x86-64 OpenGL, and Windows x86-64 OpenGL. Intel macOS receives no incompatible MapLibre JNI runtime; instead `desktopApp/.../map/IntelMacWebMapSurface.kt` owns the JCEF + MapLibre GL JS renderer. `LocalWebMapServer` binds to loopback only, serves packaged browser assets, converts local PMTiles reads into XYZ vector-tile responses, and keeps browser/native lifecycle outside shared code.

### `Main.kt`

Uses `OSMAPDIGGER_DATASET_DIR` when set; otherwise starts with no dataset. It owns closing/replacing Desktop dataset resources.

## Android adapter

### `AndroidGeoRepository`

Implements the same repository semantics through `SQLiteDatabase.rawQuery()` and the same `EXISTS` metric-filter pattern.

### `AndroidDatasetInstaller`

Reads a user-selected ZIP through Storage Access Framework, resolves each entry to its canonical destination, and rejects entries escaping app-private dataset storage.

### `AndroidUserPreferencesRepository`

Stores the same current search-context contract in app-private `filesDir/settings/preferences.sqlite` through Android `SQLiteDatabase`. Its schema version is independent from generated dataset format versioning.

### `AndroidDataset`

Parses metadata, opens generated SQLite with `OPEN_READONLY`, resolves a local `pmtiles://file://` URI, and injects Android browser behavior. `MainActivity` owns the separate application-scoped preferences adapter.

### `MainActivity`

Owns the document picker and current `AndroidDataset` lifecycle, then hosts the shared `OsmapDiggerApp`.

## Main dependencies

Dependency versions are centralized in `gradle/libs.versions.toml`.

Current dependency families:

- Kotlin Multiplatform / Kotlin compiler;
- Compose Multiplatform + Material 3;
- kotlinx.coroutines;
- kotlinx.serialization;
- MapLibre Compose;
- Xerial SQLite JDBC for Desktop;
- Android platform SQLite.

## Tests

- `shared/commonTest` covers geography, filter summaries, external links, preference payloads, and restore semantics;
- `shared/desktopTest` covers Desktop MapLibre host capability resolution;
- `desktopApp/jvmTest` covers dynamic metric SQL and preferences SQLite round trips;
- Android compilation/host tests are separate Gradle tasks.

See [`../docs/TESTS.md`](../docs/TESTS.md) for current commands and real-package acceptance checks.

## Known implementation limitations

- one current Android dataset is installed at a time;
- Desktop dataset chooser is functional but not yet a full dataset-manager UI;
- numeric filters use text fields rather than metric-specific widgets;
- map style is basic and lacks polished fully offline label/font/sprite packaging;
- favorites, named saved searches, notes, and comparison state are not persisted yet.
