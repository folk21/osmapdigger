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
- `context_km` — default context outside a configured boundary for nearby-feature analysis;
- `settlement_name_tags` — default ordered OSM name tags persisted as searchable aliases;
- `settlement_deduplication_tolerance_m` — optional default meter tolerance for geometry-based duplicate resolution such as a label point just outside its matching area; `0` disables this tolerance.
- `settlement_name_deduplication_distance_m` — optional default maximum representative-point distance for merging candidates that share a normalized name alias; `place=*` differences do not block this match, and `0` disables name+distance merging.

Each `[datasets.<id>]` entry defines one installable package scope.

Important fields:

- `display_name` — runtime-visible dataset label;
- `country_code` — optional metadata, not control flow;
- `source_pbf` — filename relative to `source_root`;
- `boundary_geojson` — optional boundary for extracting a smaller package from a larger PBF;
- `initial_center_latitude`, `initial_center_longitude`, `initial_zoom` — initial map camera;
- `property_search_site` — legacy package metadata retained for compatibility; current provider selection does not depend on this field;
- `property_search_terms` — default terms passed to enabled external-search provider templates.
- `metric_profile` — named build profile selecting which metric categories are physically processed for this dataset;
- `settlement_name_tags` — optional dataset override of searchable OSM name tags. Belarus currently includes `name`, `name:be`, `name:ru`, `name:en`, `official_name`, and `alt_name`;
- `settlement_deduplication_tolerance_m` — optional dataset override for geometry-based settlement canonicalization. Belarus uses `250` meters to absorb small gaps between a mapped place label and its matching boundary/alternate OSM representation;
- `settlement_name_deduplication_distance_m` — optional dataset override for same-name spatial canonicalization. Belarus uses `1000` meters, so nearby candidates sharing a normalized alias merge even when both are OSM nodes or their `place=*` values differ.

A dataset does **not** need to be a country. For a large country, add a regional PBF as its own dataset entry.

## Metric build profiles

`geo-builder/config/metric-profiles.toml` owns named build profiles. Profiles select category IDs from `metrics.toml` before PBF filtering and metric calculation begin. This is intentionally separate from `default_filter`: a build profile controls which metrics exist in the generated dataset, while `default_filter` only controls which generated metrics are initially visible in the UI.

The current profiles are:

- `full` — all configured categories, used by the small Andorra integration dataset;
- `core10` — ten high-value nature/risk/transport/infrastructure categories, used by Belarus to keep country-scale generation practical.

`core10` currently includes forest, water, industrial areas, landfill, major roads, railway stations, bus stops, schools, medical facilities, and supermarkets. Profile category IDs are validated against `metrics.toml` before expensive PBF processing starts.

### External property-search configuration

External-search providers are application-owned runtime configuration rather than dataset-format configuration. `mobile/config/external-search-providers.json` is the packaged seed catalog. On first use its rows are inserted into the local settings SQLite, which then becomes the authoritative runtime registry. Dataset `country_code` selects applicable country rows and `property_search_terms` supplies default query terms. Provider URLs are templates expanded locally by shared Kotlin; the application does not scrape property portals.

Provider customization currently has no dedicated settings screen. Advanced/manual changes may update the application settings database directly; seeded defaults use `INSERT OR IGNORE`, so existing customized rows are not overwritten on startup.

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
