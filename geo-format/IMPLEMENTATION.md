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

Runtime metric/filter catalog. It separates the stable metric ID/category from presentation metadata and generated measure type. `default_enabled` remains hard-filter visibility metadata.

### `metric_preference_default`

Optional dataset-provided ranking defaults keyed by `metric_id`. Each row stores resolved `lower`/`higher` direction, target, limit, weight `1..10`, and initial enabled state. Foreign-key and `CHECK` constraints preserve referential integrity and direction-specific target/limit ordering. The table deliberately does not reuse `metric_definition.default_enabled`.

This table is additive in format version 1. Updated readers return no defaults when opening an older v1 package without it; older readers ignore the additional table in a newly generated v1 package.

### `settlement`

One canonical searchable point-like settlement record. The builder resolves duplicate OSM node/area representations before persistence. Geographic geometry is reduced to latitude/longitude for the current runtime contract; general source geometries remain build-time concerns.

### `settlement_name`

Searchable primary/localized/official/alternate names attached to a canonical settlement. `normalized_name` is generated deterministically by the builder for portable lookup semantics. The table is additive in format version 1: updated readers use it when present and fall back to legacy `settlement.name`, `name_local`, and `name_en` columns when opening older version-1 packages.

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
- The additive `settlement_name` table remains format-version-1 compatible because new readers explicitly fall back when it is absent and old readers ignore it.
- The additive `metric_preference_default` table follows the same compatibility rule: new readers explicitly return an empty default catalog when it is absent and old readers ignore it when present.
- Renaming/changing semantic meaning of a stable metric ID requires care because future saved searches may refer to IDs.
- Adding nullable/additive schema fields can remain compatible only if all existing readers tolerate them.
- Removing/renaming required columns/tables or changing meaning incompatibly requires `VERSION` increment and coordinated reader support.
- Unknown future format versions should be rejected rather than guessed.

## Metadata relationship

`metadata.json` carries package-level fields not appropriate for the small SQLite metadata table, including source hash/size, geographic bounds, initial map camera, artifact names, external property-search defaults, build information, and generated counts.

The style template references PMTiles through a placeholder resolved by platform code after installation because absolute local paths differ across Desktop/Android.

## Validation

The Python builder validates preference-profile semantics before publication, SQLite enforces persisted direction/weight/bound constraints, and package/database validation records the resulting default count. Full compatibility validation additionally requires Desktop/Android readers to open a generated package and legacy v1 packages without the additive table.
