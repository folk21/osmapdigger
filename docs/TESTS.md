---
type: Test Guide
title: Tests and validation
description: Deterministic test strategy, integration checks, and acceptance validation commands.
---
# Tests and validation

## Testing principles

Default checks must be deterministic and runnable without network access, Docker, tilemaker, Android SDK, or a production PBF. Real GIS/toolchain validation is layered on top as explicit integration checks.

## Default repository check

```bash
make check
```

Currently this runs Python synthetic/unit tests under `geo-builder/tests/` plus repository-level deterministic checks under `tests/`. The repository checks include the current `mobile/shared/commonMain` logical package dependency DAG, so an accidental upward/cyclic dependency fails without requiring Gradle or network access.

The tests cover configuration parsing, metric/preference profile validation, dynamic metric generation, synthetic settlement extraction, selector semantics, distance/count/coverage calculations, SQLite round trips including preference-default constraints, package validation, and a synthetic pipeline path.

## Python unit tests directly

```bash
PYTHONPATH=geo-builder/src python -m pytest geo-builder/tests
```

Synthetic tests use small in-memory GeoDataFrames and temporary SQLite/package directories. They must not download data or invoke tilemaker.

## Real Andorra data integration

When Pyrosm is installed and the ignored Andorra PBF exists:

```bash
make build-andorra-data
```

This exercises:

- real PBF parsing;
- current metric configuration against real OSM tags/geometries;
- SQLite publication;
- package metadata;
- validation/publication without PMTiles.

Inspect generated statistics and spot-check settlement/metric content before accepting major metric changes.

## Full package integration

With tilemaker/Docker:

```bash
make build-andorra
```

Then validate explicitly:

```bash
PYTHONPATH=geo-builder/src python -m osmapdigger_geo.cli validate \
  data/generated/packages/andorra
```

Manual map acceptance should confirm:

- the PMTiles file opens through the generated style;
- the initial camera is sensible;
- map layers render without remote tile dependencies;
- OSM attribution remains visible in the application;
- search-result overlays line up with the basemap.

## Kotlin shared/Desktop tests

When Gradle dependencies are available:

```bash
make test-desktop
make test-mobile
```

Shared tests cover Haversine distance, search-input parsing, settlement-list normalization/de-duplication and conservative exact/ambiguous/unresolved alias resolution, deterministic stable-ID batching, shared candidate-query construction/bind ordering, semantic distance/count/coverage filter presentation, deterministic preference scoring/ranking, dataset preference-default contract validation, rank-before-limit analysis orchestration, exact-radius filtering before scoring, missing-score coverage semantics, analysis-workspace initialization/restore, debounced input coalescing, stale-analysis suppression, preference override recalculation, generic preference editor mutations/reset, legacy no-default manual fallback, deterministic score-explanation grouping, Desktop workspace layout geometry, deterministic number/filter-summary formatting, external-search URL templates/catalog validation, hard-filter, preference-override, and candidate-scope payload round trips, dataset scoping, removed metrics/settlements, dataset-default/override merging, invalid effective preference contracts, unavailable saved centers, and Desktop MapLibre capability resolution. Desktop tests cover legacy dynamic SQL filtering, persisted preference-default reading plus legacy-v1 empty fallback, batch analysis-candidate retrieval of only requested scoring metrics, imported stable-ID restriction including a >900-ID chunked query case, unknown scoring values, the no-scoring-metric branch, settings SQLite search/preference round trips, settings migrations through version 6, dataset-scoped favorite add/list/remove/clear persistence plus note/snapshot round trips, provider seeding/custom-row preservation, malformed hard-filter/preference payload behavior, and Intel-macOS renderer selection.


## Imported candidate workflow acceptance

Shared tests cover one-name-per-line parsing, normalized de-duplication, unique exact resolution, radius-limited duplicate-name review, ambiguous multi-selection, fuzzy suggestions without auto-selection, explicit reviewed choices, stable-ID candidate restriction, retained source-text/candidate payload round trips, and restore behavior when saved imported IDs become stale. Desktop settings tests cover candidate-source round trips through the current settings schema and malformed candidate-source payloads.

On wide Desktop, verify that **Candidate source** can switch between the full dataset and an imported list. Paste and UTF-8 `.txt` loading must populate the review surface inside the left pane. Reopening **Edit list** must preserve the previous user-entered lines and expose **Clear**. With a center and positive radius, duplicate-name choices and suggestions must show only settlements inside that radius; ambiguous exact rows must support selecting one, several, or **Select all**. Applying the review recalculates only reviewed stable IDs. **Disable import** must preserve the saved list while returning analysis to the full dataset so Required/Preferences operate normally; **Enable import** must reuse the reviewed IDs; **Delete saved list** must clear the retained source and restriction. Restart must restore both activation state and retained list. The import surface must not overlap the native/JCEF map rectangle. Android must continue to compile and read/write the same candidate-source payload even though its document-import UI is deferred.

## Favorites notebook acceptance

On wide Desktop, mark several ranked settlements as **Favorites** and verify that membership does not change current candidate source, Required/Preferences, ranking, or map/details selection. Open **Favorites** and verify compact current context, individual selection, **Select all**, clear-selection, open/remove/clear actions, and disabled selection for an entry unavailable in the current dataset. Use **Copy to import list** and verify the selected stable IDs replace the previous retained import, the imported source becomes active without name resolution, the Favorites pane closes, and the sidebar returns to ranked results while existing Required/Preferences/center/radius state remains intact. Add a Favorite from a ranked result and verify a frozen snapshot records the saved score/coverage plus current effective Required and enabled Preference contribution context. Change current filters/weights and confirm the saved snapshot remains unchanged. Edit/clear the note and explicitly update the snapshot while the Favorite is in current ranked results; only that explicit action may replace it. Restart the application with the same dataset and confirm Favorites, notes, and snapshots are restored. Favorite identity must be dataset-scoped and duplicate additions must not create duplicate rows. Android must continue to compile and use the same settings schema/repository contract even though the initial browsing surface is Desktop-first.

## Ranked-analysis performance measurement

Desktop records one `analysis.performance` line for each completed current analysis generation. Use a realistic country-scale package, perform representative Required/Preferences/radius edits, then inspect `~/.osmapdigger/logs/desktop.log`, for example with `grep "analysis.performance" ~/.osmapdigger/logs/desktop.log`. Each line records:

- `candidates`: rows returned by the batch repository query after SQL hard/coarse reduction;
- `exactEligible`: candidates remaining after shared exact-radius filtering;
- `scoringMetrics`: enabled scoring metric count requested from SQLite;
- `results`: final visible ranked result count;
- `retrievalMs`: batch SQLite retrieval time;
- `sharedMs`: shared exact-radius filtering, scoring, deterministic sort, and final limit time;
- `totalMs`: end-to-end `SettlementAnalysisService` execution time.

Use the repository acceptance command instead of copying a log line manually:

```bash
make analysis-acceptance
```

`make analysis-acceptance` prefers a country-scale sample with enabled scoring metrics. If the current installed dataset has no preference defaults, it reports the latest qualifying run as `filter-only` instead of failing. That report validates repository/radius throughput but does not complete ranked-scoring acceptance.

For the strict stage-16 gate after rebuilding/loading a dataset with at least one enabled Preference:

```bash
make analysis-acceptance-strict
```

The command reads `~/.osmapdigger/logs/desktop.log`, requires at least 1000 batch candidates and at least one enabled scoring metric, selects the latest qualifying completed sample, and prints a copy-ready Markdown acceptance record. Override the defaults only when the dataset scope justifies it:

```bash
make analysis-acceptance \
  ANALYSIS_LOG=/path/to/desktop.log \
  ANALYSIS_MIN_CANDIDATES=5000
```

A non-zero exit status means no representative sample is available yet. This prevents a small Andorra or zero-preference run from being accidentally recorded as country-scale acceptance evidence. Rapid edits should produce diagnostics only for the latest completed generation; superseded work must not be reported as the accepted measurement. These timings are diagnostic only and must never affect deterministic ranking.

## Operational-failure and storage-hardening checks

Shared Kotlin tests cover operational failure classification, preservation of prior typed failures, cancellation propagation, shared settings migration paths, and common dataset metadata parsing. Desktop JVM tests additionally cover failure-safe package replacement: malformed/traversal/corrupt-SQLite packages must leave the previous installed directory usable, while a validated replacement publishes only after staging.

Root `make check` includes architecture checks proving that settings DDL/migrations remain in one shared owner, Desktop/Android package loaders consume the shared package contract, compatibility-sensitive candidate SQL remains owned by `DatasetCandidateQueries`, and selected meaningful runtime paths do not reintroduce silent `getOrNull()`/`getOrDefault()` failure loss.

## Desktop analysis workspace acceptance

With a dataset that contains preference defaults, verify on a wide Desktop window that:

- the left analysis panel is resizable and the map occupies the remaining majority of the window;
- Required and Preferences are visually separate and preference rows remain metric-generic;
- only one preference row expands at a time, weight/target/limit edits recalculate rankings automatically, and reset restores dataset defaults;
- ranked result cards show the numeric score plus a matching 0–100 score bar, incomplete data coverage when applicable, and deterministic strongest/weakest contribution cues;
- favorite checkboxes persist dataset-scoped settlements without changing candidate source or the single selected map/details settlement; favorite selection remains transient, and only the explicit selected-Favorites transfer replaces/activates the imported candidate source and triggers normal recalculation;
- the Desktop analysis sidebar starts at roughly one third of a normal wide window and remains resizable within bounded limits;
- **Add filter** opens a full-height chooser inside the left analysis pane and never overlaps the map rectangle;
- with no selected settlement, the shallow pane beneath the map shows the current ranked-result count and updating state without requiring the user to inspect the left results list;
- selecting a ranked result highlights/focuses the existing map marker and opens the compact lower settlement pane beneath the map;
- the compact settlement pane shows score/coverage and every active search metric, prioritizing effective Required constraints before enabled Preferences, with later criteria reachable through the horizontal criteria strip;
- **Details** opens a scrollable lower-pane view with deterministic strongest/weakest/unknown preference explanation and complete grouped metrics, while External search remains a separate view;
- on Intel macOS/JCEF, both filter selection and settlement details remain outside the Swing map rectangle, so no map freeze/hide/occlusion workaround is required;
- Android continues to use the existing responsive Search/Map workflow.

Also verify map presentation and bidirectional interaction: ranked result markers expose settlement names at useful zoom levels without requiring generated basemap glyph assets; the selected settlement name remains visible. Clicking a ranked result marker selects the same settlement and opens the same lower summary. Then enable **Pick center on map** and click an arbitrary map location that is not necessarily on a marker. The geographically nearest dataset settlement must become the shared center, its name must appear in Search area, pick mode must exit, the ranked-result selection and sidebar scroll position must remain unchanged, and the same debounced radius/search semantics as name-based center selection must run. Verify that a settlement excluded from current ranked results can still be selected as the nearest center. Run these checks on both a native MapLibre Desktop host and Intel macOS/JCEF when available.

## Desktop offline map acceptance

On a supported host with a full generated package:

```bash
make run-desktop
```

Verify with network access disabled that the local basemap renders, search-result markers appear, selecting a result highlights it and focuses the camera, and OpenStreetMap attribution remains visible. On Intel macOS, also verify that the JCEF renderer starts without external CDN/tile requests. On another unsupported host, verify that application startup and analytical search remain usable with the fallback panel.

Manual Intel macOS x86-64 validation recorded during the Desktop map increment: `make run-desktop`
built and started successfully, the JCEF map rendered, and settlement selection continued to display
details. Network-disabled rendering, the complete result-marker/selected-marker/camera sequence, and
`make test-desktop` were not part of that report and remain explicit acceptance checks.

## Android compile validation

```bash
make build-android
```

This is currently the primary automated Android host check and compiles the app-private preferences adapter. Package import/map behavior and preference restoration should also be exercised on a device/emulator with a real generated package.

## Full configured check

```bash
make check-all
```

Use this before accepting a cross-project change when the local toolchain supports all included tasks.

## Persisted-format changes

Any change to `geo-format/schema.sql`, `geo-format/VERSION`, or metadata semantics must test both sides of the boundary:

- Python writer/package validation and schema constraints;
- Desktop SQLite/metadata reader, including explicit legacy fallback for additive v1 tables;
- Android SQLite/metadata reader or at minimum configured compile/device validation of the matching contract.

An incompatible format change must not be accepted solely because the Python writer tests pass.

## Active-spec acceptance

Requirements and scenarios in [`specs/active/spec-initial-functional-product.md`](specs/active/spec-initial-functional-product.md) are the acceptance source for the current initial product slice. Once accepted, stable validation knowledge belongs here and the spec moves to archive.

## Current environment limitations inherited from generation

The original generated repository environment could not complete network-dependent Gradle dependency resolution or install/run Pyrosm/tilemaker. The current code therefore still requires those full integration checks on a normal development workstation before the initial spec can be archived.

### Optional Desktop radius and compact settlement pane

Shared input parsing tests verify that blank and numeric zero mean an unset optional radius, finite positive values remain valid, and negative/non-numeric/non-finite values remain invalid. Shared presentation tests also verify deterministic compact settlement metric selection: every effective Required metric first, then every enabled Preference, with de-duplication and explicit unknown values. GeoJSON overlay tests verify that stable settlement IDs and display names are both available to map renderers. Desktop manual acceptance should verify automatic recalculation without pressing Search, the radius Clear action, the result-count status pane, the compact metric summary, scrollable Details navigation, and the separate External search provider view.


JCEF settlement-ID and WGS84 map-location payload decoding plus the minimum normal-marker click hit tolerance are covered without starting JCEF or a display server. Shared settlement lookup tests cover nearest-center selection against the complete dataset index using Haversine distance.

## UI localization checks

Shared tests verify that Russian is the default UI language, Russian and English catalogs expose distinct application chrome, settlement display names prefer persisted aliases matching the selected language, current category labels resolve to Russian with dataset-title fallback, and the active-criteria layout preserves order across two balanced rows. Manual Desktop/Android checks should verify runtime switching between Russian and English and confirm that ranked-list/map/center settlement names update without reopening the dataset.

### Belarus metric catalog check

`geo-builder/tests/test_config.py` verifies that the `core10` profile publishes `beach.distance_km` as an addable Required metric (`default_enabled = false`) and does not publish legacy `water.*` metrics. Rebuild the full local Belarus package with `make build-belarus` before validating the filter chooser against generated data.

For Intel macOS Desktop acceptance, verify that the lower status/details pane remains fully visible while selecting and closing settlements; the JCEF map rectangle must remain fixed above that pane.
