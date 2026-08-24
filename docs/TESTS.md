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

The tests cover configuration parsing, dynamic metric generation, synthetic settlement extraction, selector semantics, distance/count/coverage calculations, SQLite round trips, package validation, and a synthetic pipeline path.

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

Shared tests cover Haversine distance, deterministic filter-summary/property-link helpers, user-preference payload round trips, dataset scoping, removed metrics, unavailable saved centers, and Desktop MapLibre capability resolution. Desktop tests cover dynamic SQL filtering plus settings SQLite missing-state, persistence round-trip, and malformed-filter behavior.

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

- Python writer/package validation;
- Desktop SQLite/metadata reader;
- Android SQLite/metadata reader.

An incompatible format change must not be accepted solely because the Python writer tests pass.

## Active-spec acceptance

Requirements and scenarios in [`specs/active/spec-initial-functional-product.md`](specs/active/spec-initial-functional-product.md) are the acceptance source for the current initial product slice. Once accepted, stable validation knowledge belongs here and the spec moves to archive.

## Current environment limitations inherited from generation

The original generated repository environment could not complete network-dependent Gradle dependency resolution or install/run Pyrosm/tilemaker. The current code therefore still requires those full integration checks on a normal development workstation before the initial spec can be archived.
