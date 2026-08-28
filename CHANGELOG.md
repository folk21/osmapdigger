---
type: Changelog
title: OsmapDigger changelog
description: Notable project changes organized by release state.
---
# Changelog

All notable changes are documented here.

## Unreleased

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
