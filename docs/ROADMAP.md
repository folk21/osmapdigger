---
type: Roadmap
title: Roadmap
description: Compact product roadmap and current implementation focus.
---
# Roadmap

This roadmap stays intentionally compact. Detailed requirements for significant active work belong in [`specs/`](specs/README.md).

## P0 — accept the initial functional vertical slice

Active spec: [`specs/active/spec-initial-functional-product.md`](specs/active/spec-initial-functional-product.md).

Current implementation focus: [`specs/active/subspecs/user-preferences.md`](specs/active/subspecs/user-preferences.md).

Acceptance requires a configured workstation to demonstrate:

- real Andorra PBF -> valid SQLite;
- real Andorra PBF -> PMTiles;
- Desktop opening/searching/filtering/map overlay;
- Android importing the same `.omd.zip` and performing local search/map display;
- dynamic metrics appearing without category-specific shared UI code;
- package data staying outside Git.

## P1 — dataset experience

- installed-dataset manager instead of one current dataset;
- package metadata/details screen;
- explicit package compatibility/error UX;
- Desktop workflow for selecting a local PBF/config and invoking the Python builder with progress/logging;
- optional smaller-region package creation from a large local source.

## P1 — map quality

- richer vector style;
- offline labels/glyph strategy;
- icons/sprites where needed;
- better layer visibility by zoom;
- selected/result marker interactions;
- map-driven center selection.

## P1 — user workflow

- favorites;
- notes;
- saved filters/searches;
- comparison of multiple settlements;
- share/export search parameters and settlement summaries.

## P2 — additional data sources

- elevation and slope;
- flood/hydrology risk;
- satellite-derived land indicators;
- climate and soil;
- pollution/environmental sources;
- public cadastral/property context where legally and technically available.

Additional data sources should enter through builder adapters and versioned metrics rather than bypassing the current SQLite/filter contract.

## Deferred

- cloud account/sync;
- LLM-driven query parsing;
- embedded listing aggregation/scraping;
- Android-side Pyrosm/tilemaker processing.
