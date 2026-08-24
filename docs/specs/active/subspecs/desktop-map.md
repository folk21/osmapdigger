---
type: Specification
title: Desktop offline map support
description: Active sub-spec for rendering the installed offline PMTiles basemap and search overlays across supported Desktop hosts, including an Intel macOS web renderer experiment.
document_role: subspec
spec_status: active
parent: ../spec-initial-functional-product.md
---
# Desktop offline map support

## Status

Active implementation sub-spec.

Parent specification: [`../spec-initial-functional-product.md`](../spec-initial-functional-product.md).

This sub-spec is the current implementation focus under the active initial-product umbrella. It
implements the Desktop part of the existing offline-map requirements without changing analytical
search semantics or the generated dataset format.

## Goal

Render the installed dataset's local PMTiles basemap in the Desktop application. Use MapLibre Compose
where a compatible native runtime exists and a bounded local web-renderer adapter on Intel macOS,
while preserving a non-fatal analytical fallback when neither renderer can start.

The Desktop map must show the same runtime search-result and selected-settlement overlays already
used by Android.

## Relationship to the umbrella specification

This work directly refines R7, R8 and R13 of the parent specification and contributes to the Desktop
acceptance scenario in the umbrella validation section.

It does not change the build-time PMTiles format, dynamic metric contract, search SQL, radius
semantics, or Android map behavior.

## Current state

The repository already contains:

- generated local PMTiles and a style template with a runtime-resolved URI;
- shared `ResultGeoJson` generation for search-result overlays;
- Android MapLibre rendering;
- a Desktop `MapPanel` contract;
- Desktop package loading that resolves the installed PMTiles file into the style JSON.

The first part of this increment enabled MapLibre Compose on hosts with a published native runtime and
kept the fallback on Intel macOS. Because the current development workstation is Intel macOS, this
sub-spec now includes a bounded alternative-renderer experiment for that host without changing shared
search semantics or the dataset package contract.

## Requirements

### DM-R1 — local offline basemap

On a supported Desktop host, `MapPanel` must render the installed package's resolved local style and
PMTiles artifact. Core map rendering must not require a remote tile service.

### DM-R2 — supported native runtime selection

The Desktop application host owns selection of the platform-specific MapLibre native runtime.
Exactly one compatible runtime capability may be added for the current OS/architecture.

For MapLibre Compose 0.13.x the supported OsmapDigger targets are:

- macOS Apple Silicon -> `macos-aarch64-metal`;
- Linux x86-64 -> `linux-amd64-opengl`;
- Windows x86-64 -> `windows-amd64-opengl`.

Unsupported hosts must not receive an incompatible native capability.

### DM-R3 — graceful unsupported-host behavior

An unsupported Desktop OS/architecture must keep the application usable. Search, filters, details,
preferences, and external-link workflows must continue to work with a clear map fallback instead of
failing application startup.

### DM-R4 — search result overlay

Desktop must render current search results as a runtime GeoJSON overlay above the basemap. Map
rendering must not become an analytical source of truth.

### DM-R5 — selected settlement

The selected settlement must be visually distinct from other search results. Selecting a settlement
must move the map camera toward that settlement without altering the search request.

### DM-R6 — platform boundary

MapLibre/native runtime concerns must remain out of domain and search code. Shared analytical models
must not depend on native map types.

### DM-R7 — single active Desktop implementation

The repository must have one authoritative `desktopMain` `MapPanel` implementation. Historical
unused `desktopMapLibreMain` and `desktopFallbackMain` implementations must be removed rather than
kept as parallel sources.

### DM-R8 — no geo-format change

Desktop map enablement must consume the existing package contract. `geo-format/VERSION`, SQLite
schema, and package metadata semantics must not change for this increment.

### DM-R9 — attribution

The Desktop map must keep visible OpenStreetMap attribution while rendering OSM-derived map data.

### DM-R10 — Intel macOS alternative renderer

On Intel macOS, where the configured MapLibre Compose version has no compatible native JNI runtime,
Desktop may use a platform-owned JCEF + MapLibre GL JS renderer. The renderer must remain offline at
runtime: browser assets are packaged dependencies, PMTiles is read locally on the JVM, and vector tile
requests are served only through a loopback-only HTTP adapter.

Failure to initialize this renderer must degrade to the existing non-fatal map-unavailable surface and
must not break analytical workflows.

### DM-R11 — renderer injection boundary

An alternative renderer must be injected by the Desktop application host through a small shared UI
contract. JCEF, local HTTP serving, PMTiles reader APIs, and browser lifecycle must not enter shared
domain/search code or Android code.

## Scenarios

### DM-S1 — Apple Silicon Desktop

Given a full local dataset package on macOS Apple Silicon, starting Desktop resolves the Metal native
runtime, opens the local style/PMTiles artifact, and displays the map without a remote tile service.

### DM-S2 — search overlay

Given a rendered Desktop map, performing a settlement search displays all returned settlements as map
markers. Selecting one result renders a distinct selected marker and focuses the camera on it.

### DM-S3 — Intel macOS

Given an Intel macOS host and a full local package, Desktop starts the platform-owned web renderer,
loads packaged MapLibre GL JS assets, reads the local PMTiles file through the JVM adapter, and renders
result/selection overlays without external network access. If renderer initialization fails, the
application remains usable and shows the non-fatal fallback.

### DM-S4 — unsupported host

Given another OS/architecture without a configured renderer, the application starts normally and
displays the analytical fallback instead of attempting to load an incompatible native library.

### DM-S5 — data-only package

Given a valid package without PMTiles, Desktop continues to expose analytical search and reports that
the package has no generated map.

## Non-goals

This increment does not require:

- changing or redesigning the vector-map style;
- labels, glyph packaging, sprites, or production cartographic polish;
- map-driven center selection;
- clustering or specialized marker interaction;
- changing Android map implementation;
- upgrading MapLibre Compose solely for this feature.

## Design constraints

- Desktop package loading remains local and offline-first.
- The existing PMTiles/style package contract is reused unchanged.
- Native runtime selection and alternative renderer ownership belong to the Desktop host/build boundary.
- Intel macOS web rendering must bind its local HTTP adapter to loopback only and must not use a CDN.
- Unsupported-host fallback is deliberate product behavior, not an exception path that disables the
  rest of the application.
- Do not reintroduce conditional source-directory wiring through `kotlin.srcDir(...)`.

## Compatibility / migration

No dataset migration is required. Existing full `.omd.zip` packages remain compatible.

The change adds a MapLibre native runtime only on supported native hosts. Intel macOS additionally
packages the JCEF native bundle; browser/map assets and PMTiles access stay local. Other unsupported
hosts keep using the same application and shared code without resolving an incompatible native runtime.

## Validation

Acceptance requires:

1. `make test-desktop` passes on a configured Gradle environment.
2. `make run-desktop` starts successfully on a supported Desktop host with a full local package.
3. The local basemap is visible with network access disabled.
4. Search results appear as markers and selected settlement highlighting/camera focus works.
5. A data-only package reports no map without breaking search.
6. Intel macOS renders the full local package without external network access, or fails closed to the
   analytical fallback without breaking startup/search.
7. Unsupported-host capability resolution is covered by deterministic tests and retains the fallback.
8. `geo-format/VERSION` and persisted dataset schema remain unchanged.

## Implementation tasks

- add MapLibre Compose to the Desktop shared source set;
- select the supported native JNI runtime in `desktopApp`;
- replace the always-fallback `desktopMain` map with supported-host MapLibre rendering plus fallback;
- remove unused historical Desktop map source implementations;
- add host-capability tests;
- add the Intel macOS JCEF/MapLibre GL JS renderer behind a platform UI contract;
- serve packaged web assets and local PMTiles-derived vector tiles through a loopback-only adapter;
- update implementation, installation, test, usage, and roadmap documentation;
- run supported-host offline-map acceptance before archiving this sub-spec.
