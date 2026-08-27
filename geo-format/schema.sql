-- OsmapDigger runtime SQLite schema.
--
-- This file is the authoritative relational contract between the Python Geo Builder
-- and the Android/Desktop runtime readers. Keep schema semantics coordinated with
-- geo-format/VERSION, geo-format/IMPLEMENTATION.md, the Python writer, and both
-- platform GeoRepository implementations.
--
-- Metric values are intentionally normalized instead of adding one column per OSM
-- category. A missing settlement_metric row means "unknown/unavailable", not zero.

PRAGMA foreign_keys = ON;

-- Small compatibility/identity metadata that runtime readers may need without
-- parsing package-level metadata.json. Values are strings to keep this table stable
-- and deliberately simple.
CREATE TABLE dataset_metadata (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
);

-- Runtime catalog of filterable numeric metrics generated from builder configuration.
-- Shared Kotlin UI reads these rows to build generic filters; it must not hardcode
-- source categories such as forest/water/railway.
CREATE TABLE metric_definition (
    metric_id TEXT PRIMARY KEY NOT NULL,
    category_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    unit TEXT NOT NULL,
    measure_type TEXT NOT NULL,
    preferred_direction TEXT NOT NULL,
    default_enabled INTEGER NOT NULL CHECK(default_enabled IN (0, 1)),
    sort_order INTEGER NOT NULL
);

-- One searchable settlement represented by a WGS84 point. The builder may derive
-- this point from a non-point OSM place geometry, but general source geometry is a
-- build-time concern and is not persisted in the current runtime format.
CREATE TABLE settlement (
    settlement_id TEXT PRIMARY KEY NOT NULL,
    osm_id INTEGER,
    osm_type TEXT,
    name TEXT NOT NULL,
    name_local TEXT,
    name_en TEXT,
    place_type TEXT,
    population INTEGER,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL
);

-- Legacy primary-name lookup remains indexed for older readers; the multilingual center
-- picker uses settlement_name when available. Coordinate lookup supports coarse radius
-- bounding-box reduction before exact Haversine filtering.
CREATE INDEX idx_settlement_name ON settlement(name);
CREATE INDEX idx_settlement_lat_lon ON settlement(latitude, longitude);

-- Searchable multilingual/alternate names for one canonical settlement. The builder
-- normalizes these values using the same deterministic rules reproduced by shared
-- Kotlin search. Existing readers may ignore this additive table and continue using
-- settlement.name/name_local/name_en.
CREATE TABLE settlement_name (
    settlement_id TEXT NOT NULL,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    language TEXT,
    kind TEXT NOT NULL CHECK(kind IN ('primary', 'localized', 'official', 'alternate')),
    PRIMARY KEY (settlement_id, normalized_name),
    FOREIGN KEY (settlement_id) REFERENCES settlement(settlement_id) ON DELETE CASCADE
);

CREATE INDEX idx_settlement_name_normalized ON settlement_name(normalized_name);

-- Sparse numeric metric values. One row exists only when the builder could calculate
-- a value for that settlement/metric pair. The composite primary key prevents
-- ambiguous duplicate values.
CREATE TABLE settlement_metric (
    settlement_id TEXT NOT NULL,
    metric_id TEXT NOT NULL,
    value REAL NOT NULL,
    PRIMARY KEY (settlement_id, metric_id),
    FOREIGN KEY (settlement_id) REFERENCES settlement(settlement_id) ON DELETE CASCADE,
    FOREIGN KEY (metric_id) REFERENCES metric_definition(metric_id) ON DELETE CASCADE
);

-- Runtime SQL normally starts from a requested metric ID and numeric range, so this
-- index keeps dynamic range filters efficient without category-specific indexes.
CREATE INDEX idx_metric_value ON settlement_metric(metric_id, value);
