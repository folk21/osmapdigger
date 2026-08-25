---
type: Implementation
title: Geo Builder implementation
description: Current Python geospatial builder modules, contracts, processing stages, and extension points.
---
# Geo Builder implementation

## Scope

This document describes the current Python implementation under `geo-builder/`. Stable cross-project responsibilities remain in [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md); package-format semantics are coordinated with [`../geo-format/`](../geo-format/).

## Composition root

`osmapdigger_geo.cli` exposes three command groups:

- `list-datasets` — list configured build IDs;
- `build <dataset>` — execute the staged pipeline;
- `validate <package-dir>` — validate an already published/unpacked package.

`cli.py` intentionally contains no GIS logic. It parses arguments and delegates to configuration/pipeline/package APIs.

## Configuration loading

`config.py` resolves paths relative to the TOML configuration file, not the current working directory. This is important because root `Makefile` commands and direct subproject commands may start from different directories.

`load_dataset_definition()` returns one immutable `DatasetDefinition` containing:

- source/output paths;
- optional boundary;
- initial camera;
- context buffer;
- geo-format schema/version paths;
- external property-search defaults.

`load_categories()` converts raw selector TOML into immutable `CategoryDefinition`/`Selector` records.

## PBF reader

`PbfReader` is lazy: construction stores path/geometry configuration, while `_get_osm()` imports/creates Pyrosm only when a read is requested. Importing the package therefore performs no PBF parsing.

For large source files, the adapter may choose Pyrosm `out_of_core`; smaller files use in-memory mode. Out-of-core custom reads request only selector/filter keys plus OsmapDigger's explicit extra tag columns and disable Pyrosm's catch-all `tags` column. This keeps country-scale GeoParquet caches bounded and avoids heterogeneous Arrow schemas for arbitrary leftover OSM tags.

Two current read batches are intentional:

- `read_general()` merges non-road category tags plus configured settlement `place` values;
- `read_roads()` merges categories flagged `road_batch = true`.

The merged batch is deliberately broad; category-specific precision is applied locally by `metrics.filter_category()`.

`crop_pbf()` is used only when map generation needs a regional source derived from a larger PBF.

## Selector semantics

A category contains OR-ed alternatives. Each alternative contains AND-ed selectors.

Example interpretation:

```text
(natural=wood) OR (landuse=forest)
```

A selector containing `*` requires tag presence regardless of value.

The broad Pyrosm read filter is not the source of category truth; local selector evaluation is.

## Settlement extraction

`metrics.extract_settlements()`:

1. filters `place` values configured by `settings.settlement_places`;
2. optionally restricts candidates to the dataset scope geometry;
3. converts non-point place geometry to `representative_point()`;
4. resolves a display name through supported name tags;
5. retains OSM identity/type, population when parseable, and coordinates;
6. creates a stable runtime `settlement_id` when OSM identity is available.

The function uses geometry coordinates rather than expecting `lat`/`lon` columns from Pyrosm.

## Metric calculation

`calculate_metrics()` iterates configured categories and selects the correct source batch. It transforms data to one local metric CRS before spatial distance/buffer operations.

### Nearest distance

`_apply_nearest()` uses `geopandas.sjoin_nearest()` and retains the minimum distance per settlement.

### Counts

`_apply_count()` buffers each settlement at the requested radius and spatially joins features intersecting that buffer. The resulting numeric metric is a feature count.

### Coverage

`_apply_coverage()` keeps polygon/multipolygon features, queries candidates through the spatial index, unions intersecting geometry, intersects with the settlement buffer, and stores percent coverage.

Coverage is therefore an area ratio, not a count or nearest-distance approximation.

## Runtime metric catalog

`metric_catalog.build_metric_definitions()` derives persisted IDs from category config. Examples:

- `forest.distance_km`;
- `forest.coverage_pct_1km`;
- `school.count_10km`.

Those definitions are inserted into SQLite alongside values, allowing generic runtime UI.

## SQLite publication

`DatasetDatabaseWriter` executes the authoritative SQL from `geo-format/schema.sql`, inserts:

- dataset metadata;
- metric definitions;
- settlements;
- metric values.

It then commits, runs `ANALYZE`, and asks SQLite to optimize. `validate_database()` reopens read-only and runs `PRAGMA integrity_check` plus table counts.

## Map generation

`map_builder.write_style_template()` writes the current compact MapLibre style template.

`build_pmtiles()` selects:

- `tilemaker` executable for direct mode; or
- Docker tilemaker container for Docker mode.

Auto mode prefers direct tilemaker.

Map generation is intentionally external-process based so the Python application does not reimplement a vector-tile encoder.

## Staging and publication

`pipeline.build_dataset()` creates a temporary staging directory under the output parent. It writes/validates SQLite, style, optional PMTiles, and metadata there.

Only after `validate_package()` succeeds does it replace the final dataset directory. The portable `.omd.zip` is generated from the published directory afterward.

This ordering protects against a build leaving a half-populated final directory.

## Package validation

`package.validate_package()` currently checks:

- required core files;
- JSON metadata readability/basic dataset ID access;
- SQLite integrity;
- settlement and metric-definition counts;
- whether a PMTiles file is present.

It is structural/integrity validation, not cryptographic trust validation.

## Test structure

- `test_config.py` — dataset/category parsing and generated metric catalog;
- `test_osm_reader.py` — Pyrosm adapter behavior, including bounded out-of-core tag reads;
- `test_metrics.py` — geometry-driven settlement extraction, selector logic, synthetic spatial metrics;
- `test_database.py` — schema/writer round trip;
- `test_package.py` — package validation/ZIP creation;
- `test_pipeline.py` — synthetic staged pipeline behavior where present.

Real-PBF testing is intentionally separate through `make build-andorra-data`.

## Safe extension points

- add OSM categories/measures through config first;
- add new source families behind new adapters rather than putting source-specific branches into `pipeline.py`;
- keep persisted output changes coordinated with `geo-format` and both runtime readers;
- preserve data-only builds so analytical development does not depend on map tooling.
