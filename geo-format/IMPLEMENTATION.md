---
type: Implementation
title: Geo format implementation
description: Current persisted SQLite and metadata compatibility contract and its readers and writers.
---
# Geo format implementation

## Scope

`geo-format/` owns persisted compatibility semantics only. It does not own OSM extraction, search UI, map styling, or platform storage implementation.

Current format version: **1**.

## Owning files

- `VERSION` — integer emitted/checked as dataset format version;
- `schema.sql` — canonical SQLite structure and relational constraints;
- `metadata.schema.json` — package metadata shape used outside SQLite.

## Artifact relationship

A normal full package contains:

- `georisk.sqlite`;
- `<dataset-id>.pmtiles`;
- `style.template.json`;
- `metadata.json`.

A data-only package may omit PMTiles while preserving SQLite/style/metadata so analytical development can proceed without tilemaker.

## SQLite tables

### `dataset_metadata`

Small key/value compatibility/identity metadata available to SQLite readers without opening JSON metadata.

### `metric_definition`

Runtime filter catalog. It separates the stable metric ID/category from presentation metadata and generated measure type.

### `settlement`

One searchable point-like settlement record. Geographic geometry is reduced to latitude/longitude for the current runtime contract; general source geometries remain build-time concerns.

### `settlement_metric`

Sparse numeric metric values. The composite primary key enforces one value per settlement/metric. Missing values are intentionally represented by missing rows.

## Writer/readers

Current implementations live outside this subproject:

- Python writer: `geo-builder/src/osmapdigger_geo/database.py`;
- Python package validation: `geo-builder/src/osmapdigger_geo/package.py`;
- Desktop reader: `mobile/desktopApp/.../JdbcGeoRepository.kt` and `DesktopDataset.kt`;
- Android reader: `mobile/androidApp/.../AndroidGeoRepository.kt` and `AndroidDataset.kt`.

Because producer/readers are separated, persisted changes must inspect all of them.

## Compatibility rules

- Additive rows in `metric_definition`/`settlement_metric` are normal dataset evolution and do not require a schema-version bump.
- Renaming/changing semantic meaning of a stable metric ID requires care because future saved searches may refer to IDs.
- Adding nullable/additive schema fields can remain compatible only if all existing readers tolerate them.
- Removing/renaming required columns/tables or changing meaning incompatibly requires `VERSION` increment and coordinated reader support.
- Unknown future format versions should be rejected rather than guessed.

## Metadata relationship

`metadata.json` carries package-level fields not appropriate for the small SQLite metadata table, including source hash/size, geographic bounds, initial map camera, artifact names, external property-search defaults, build information, and generated counts.

The style template references PMTiles through a placeholder resolved by platform code after installation because absolute local paths differ across Desktop/Android.

## Validation

The Python builder runs SQLite integrity checks and package structural validation before publication. Full compatibility validation additionally requires Desktop/Android readers to open a generated package.
