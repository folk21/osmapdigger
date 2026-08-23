# Geo format development rules

Root [`../AGENTS.md`](../AGENTS.md) applies. Read [`README.md`](README.md) and [`IMPLEMENTATION.md`](IMPLEMENTATION.md) before changing persisted semantics.

## Compatibility boundary

`VERSION`, `schema.sql`, and `metadata.schema.json` form the persisted contract between independent Python writers and Kotlin readers.

Before changing them, inspect:

- `geo-builder/src/osmapdigger_geo/database.py`;
- `geo-builder/src/osmapdigger_geo/package.py`;
- Desktop `JdbcGeoRepository` / `DesktopDataset`;
- Android `AndroidGeoRepository` / `AndroidDataset`.

## Versioning

An incompatible persisted semantic change requires a `VERSION` increment. Additive metric-definition/value rows normally do not, because dynamic metrics are data rather than new schema columns.

Do not reuse an existing metric ID for a materially different meaning.

## Schema semantics

- Keep `settlement_metric` sparse.
- Missing metric rows mean unknown/unavailable, not zero.
- Preserve referential integrity between settlements, definitions, and values.
- Keep schema comments aligned with runtime semantics; this file is frequently read directly by contributors/LLMs.

## Validation

Persisted changes require writer tests plus both platform-reader review/tests. Python-only success is insufficient for a format change.
