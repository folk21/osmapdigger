---
type: Configuration Guide
title: Configuration
description: Ownership and semantics of dataset and metric configuration.
---
# Configuration

## Ownership

OsmapDigger intentionally keeps source/builder configuration out of Kotlin runtime code.

- `geo-builder/config/datasets.toml` owns dataset source/output/map defaults and optional dataset-specific external-search defaults.
- `geo-builder/config/metrics.toml` owns OSM source categories and generated numeric metrics.
- persisted runtime `metric_definition` rows are generated from metric configuration and become the runtime filter catalog.

## Dataset configuration

The `[builder]` section defines paths shared by configured datasets:

- `source_root` — local ignored PBF directory;
- `output_root` — local ignored generated package root;
- `format_schema` — authoritative SQLite schema;
- `format_version_file` — persisted format version;
- `context_km` — default context outside a configured boundary for nearby-feature analysis.

Each `[datasets.<id>]` entry defines one installable package scope.

Important fields:

- `display_name` — runtime-visible dataset label;
- `country_code` — optional metadata, not control flow;
- `source_pbf` — filename relative to `source_root`;
- `boundary_geojson` — optional boundary for extracting a smaller package from a larger PBF;
- `initial_center_latitude`, `initial_center_longitude`, `initial_zoom` — initial map camera;
- `property_search_site` — optional domain restriction for external web search;
- `property_search_terms` — default property query terms.

A dataset does **not** need to be a country. For a large country, add a regional PBF as its own dataset entry.

### External property-search configuration

Current runtime property-search behavior is intentionally small: generated dataset metadata carries the optional `property_search_site` restriction and `property_search_terms`, while shared Kotlin exposes explicit Google and Yandex actions. The application does not load a separate provider registry at runtime and does not scrape property portals.

`config/external-services.yaml` is not part of the current runtime configuration path. Treat it as non-authoritative planning material unless a future change specification explicitly introduces a provider-registry contract and wires it through the builder/runtime boundary.

## Metric configuration model

`metrics.toml` begins with shared settings such as `settlement_places`. Each `[[categories]]` block defines one conceptual OSM feature category.

Example concepts represented by current configuration include forest, water, farmyard, landfill, railway station, school, medical facility, supermarket, protected area, and many additional non-default categories.

### Selector alternatives

A category contains `selectors`, represented as alternatives of AND-ed tag constraints.

Conceptually:

```text
(alternative 1) OR (alternative 2) OR ...
```

Within one alternative all listed selectors must match.

For example, a category may accept `natural=wood` **or** `landuse=forest`.

A selector value of `"*"` means the tag must exist regardless of its concrete value.

### Generated measure switches

- `distance = true` generates `<category>.distance_km`.
- `count_radii_km = [...]` generates one count metric per configured radius.
- `coverage_radii_km = [...]` generates polygon-coverage metrics.
- `default_filter = true` makes the generated distance metric visible initially when applicable.
- `preferred_direction` is persisted metadata for future scoring/presentation semantics.
- `road_batch = true` moves the category into the separate road read batch.

## Runtime implications

The Kotlin UI does not read `metrics.toml`. It receives only generated `metric_definition` records from SQLite.

Therefore an additive numeric category can usually be introduced by:

1. adding/changing builder configuration;
2. rebuilding the dataset;
3. verifying the generated metric definitions and values.

No shared UI branch is needed for ordinary range-filter metrics.

## Configuration compatibility cautions

Changing a category ID or metric ID can invalidate saved filter references in future persisted user state. Prefer stable IDs and change titles/descriptions independently.

Changing source selector semantics does not by itself require a geo-format version change, because the SQLite schema remains compatible; it does require dataset regeneration and should be documented when materially changing meaning.

Changing persisted table/metadata semantics belongs to `geo-format/` and may require a format version increment.
