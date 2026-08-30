---
type: Changelog
title: OsmapDigger changelog
description: Notable project changes organized by release state.
---
# Changelog

All notable changes are documented here.

## Unreleased
- Establish an acyclic logical dependency graph inside KMP `shared`, move workspace/presentation/dataset/map/external contracts to semantic owners, remove unused legacy dataset-manager abstractions, and add a network-free architecture dependency check.
- Highlight the selected ranked settlement, show active criteria in two rows, and resolve settlement/map display names from persisted aliases matching the selected UI language.

- Replace the broad analytical Water metric with Beach proximity in generated metric catalogs and default ranking profiles. Basemap water rendering remains unchanged.

- Label ranked settlements on the map and show every active Required/Preference metric in the selected-settlement summary.

- Clarified Desktop map interaction: `Pick center on map` changes only the search center, while normal marker clicks select ranked settlements; center recalculation no longer scrolls the sidebar to a previously selected result.

- Add bidirectional Desktop map marker selection and `Pick center on map` nearest-settlement center selection for native MapLibre and Intel macOS JCEF renderers.

- Treat Desktop radius zero as unset and add an explicit radius Clear action.
- Keep selected-settlement content compact below the map, with search-result count, key participating metrics, and explicit Details and External search views.

### Added

- Map-first wide Desktop analysis workspace with a wider resizable criteria/results sidebar, generic preference editors, ranked score/coverage results, a left-contained Add filter chooser, and settlement details below the map without native-map overlap.
- Application-owned dynamic external-search provider registry with seeded country-specific property-search services.
- Native offline PMTiles map rendering on supported Desktop hosts with a non-fatal fallback elsewhere.
- Offline Intel macOS map rendering through JCEF, packaged MapLibre GL JS, and a loopback-only local PMTiles adapter.
- Local persistence and restart restoration for the current dataset-scoped center, radius, and dynamic filters on Desktop and Android.
- Dataset management architecture documentation.
- Desktop and Android platform documentation rules.

- Add shared Russian/English UI localization with Russian as the default application language.
