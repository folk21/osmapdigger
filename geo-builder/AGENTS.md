---
type: Development Guide
title: Geo Builder development rules
description: Geo Builder-specific architecture, configuration, publication, testing, and documentation rules.
---
# Geo Builder development rules

Root [`../AGENTS.md`](../AGENTS.md) applies. Read [`README.md`](README.md), [`IMPLEMENTATION.md`](IMPLEMENTATION.md), and the active spec before changing cross-stage behavior.

## Protected boundaries

- Importing builder modules must not parse PBF, access the network, invoke tilemaker/Docker, or write generated artifacts.
- `cli.py` remains a thin composition root; GIS behavior belongs in focused modules.
- `PbfReader` owns Pyrosm adaptation, not category semantics.
- `metrics.py` owns authoritative selector matching and numeric spatial calculations.
- `database.py` writes only according to `../geo-format/schema.sql`; do not duplicate schema creation in Python strings.
- `map_builder.py` owns external map generation only; map style changes must not alter analytical search values.
- `pipeline.py` orchestrates stages but should not absorb stage internals.

## Configuration-driven metrics

Prefer adding ordinary OSM-derived numeric filters through `config/metrics.toml`. Dataset-provided ranking defaults belong in `config/preference-profiles.toml`; do not overload `default_filter` or metric-generation profiles with scoring semantics.

Keep category IDs and generated metric IDs stable when possible. Renaming IDs can break saved search/preference compatibility even if the SQLite schema remains unchanged. Preference profiles must reference generated stable metric IDs and be validated against the selected metric profile before publication.

The broad Pyrosm custom filter is an optimization. Exact category meaning comes from local OR-of-AND selector evaluation; do not make correctness depend on merged-filter semantics.

## Geometry and distance rules

- Source geometry is WGS84 unless explicitly projected.
- Distance/area calculations must use a metric CRS.
- Settlement runtime coordinates come from geometry, not assumed `lat`/`lon` columns.
- A boundary-limited dataset may read contextual features outside the strict settlement boundary using the configured buffer.
- Missing source/category data should not be fabricated as a meaningful distance value.

## Publication

Build into staging, validate, then replace final output. Do not update/publish metadata that claims success before required artifacts are written and validated.

Temporary cropped PBF/map inputs must stay in staging/generated paths and must not enter Git.

## Testing

Default tests use synthetic GeoDataFrames/temp directories and require no real PBF or external map tool.

For changes to:

- selectors/metrics — update `test_metrics.py`;
- config/catalog — update `test_config.py`;
- persisted writing — update `test_database.py` and coordinate with `geo-format/`;
- package semantics — update `test_package.py`/pipeline tests;
- real Pyrosm assumptions — rerun `make build-andorra-data`.

## Documentation

Public classes/functions with important invariants should have useful docstrings. Comments should explain boundary/semantic decisions, not restate individual assignments.
