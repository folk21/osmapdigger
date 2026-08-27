---
type: Module Overview
title: OsmapDigger geo format
description: Entry point for the versioned persisted contract shared by builders and runtime readers.
---
# OsmapDigger geo format

`geo-format/` defines the versioned persisted compatibility contract between the Python Geo Builder and Android/Desktop runtime readers.

Current format version: **1**.

## Canonical files

- [`VERSION`](VERSION) — current integer format version;
- [`schema.sql`](schema.sql) — authoritative SQLite relational schema and table semantics;
- [`metadata.schema.json`](metadata.schema.json) — package-level JSON metadata contract.

## Core design

The format is intentionally dynamic with respect to OSM-derived filters:

- `metric_definition` describes available runtime numeric metrics and hard-filter presentation defaults;
- `metric_preference_default` optionally stores separate dataset-provided generic ranking defaults;
- `settlement_name` stores multilingual/alternate lookup names for canonical settlements;
- `settlement_metric` stores sparse `(settlement, metric, value)` rows;
- shared UI reads metric definitions instead of assuming columns/categories.

This lets a rebuilt dataset expose additive numeric filters and ranking defaults without category-specific Kotlin branches. Preference defaults remain separate from hard-filter visibility and metric-generation profile selection.

A missing `settlement_metric` row means the value is unavailable/unknown, **not zero**.

## Compatibility work

Before changing the contract, read [`IMPLEMENTATION.md`](IMPLEMENTATION.md) and [`AGENTS.md`](AGENTS.md), then inspect both Python writer/validator code and Android/Desktop readers.

Incompatible semantic/schema changes require a format-version increment. Additive metric rows generally do not.

Cross-project package semantics, security, and current validation are summarized in [`../docs/IMPLEMENTATION.md`](../docs/IMPLEMENTATION.md).
