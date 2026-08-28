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

Currently this runs Python synthetic/unit tests under `geo-builder/tests/`.

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

Shared tests cover Haversine distance, search-input parsing, deterministic preference scoring/ranking, dataset preference-default contract validation, rank-before-limit analysis orchestration, exact-radius filtering before scoring, missing-score coverage semantics, analysis-workspace initialization/restore, debounced input coalescing, stale-analysis suppression, preference override recalculation, legacy no-default manual fallback, deterministic number/filter-summary formatting, external-search URL templates/catalog validation, hard-filter and preference-override payload round trips, dataset scoping, removed metrics, dataset-default/override merging, invalid effective preference contracts, unavailable saved centers, and Desktop MapLibre capability resolution. Desktop tests cover legacy dynamic SQL filtering, persisted preference-default reading plus legacy-v1 empty fallback, batch analysis-candidate retrieval of only requested scoring metrics, unknown scoring values, the no-scoring-metric branch, settings SQLite search/preference round trips, version-2-to-3 preference migration, provider seeding/custom-row preservation, and malformed hard-filter/preference payload behavior.

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
