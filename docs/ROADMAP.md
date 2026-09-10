---
type: Roadmap
title: Roadmap
description: Compact product roadmap and current implementation focus.
---
# Roadmap

This roadmap stays intentionally compact. Detailed requirements for significant active work belong in [`specs/`](specs/README.md).

## P0 — accept the initial functional vertical slice

Active spec: [`specs/active/spec-initial-functional-product.md`](specs/active/spec-initial-functional-product.md).

Architecture note: KMP architecture hardening iterations 1–5 are implemented between feature checkpoints (dependency direction, typed operational failures, shared settings/package semantics, atomic dataset installation, shared analytical candidate-query semantics, and source-responsibility decomposition); physical Gradle module extraction remains interleaved backlog.

Current implementation focus: [`specs/active/subspecs/settlement-shortlist-workflow.md`](specs/active/subspecs/settlement-shortlist-workflow.md) — imported candidate sets, persistent favorites/notebook, later analysis snapshots, export/share, and batch external search.
Candidate-source core/workflow, visual ranked affordances, persistent dataset-scoped Favorites, explicit selected-Favorites transfer back into imported candidates, notebook notes/frozen analysis snapshots, explicit bounded batch external search, and portable deterministic export/share are implemented. A focused shared acceptance fixture now locks the cross-feature shortlist contract. Deterministic calibration-report tooling reads generated runtime databases; representative dataset evidence and configured/manual Desktop/Android acceptance remain before archival.

Interleaved architecture track: [`specs/active/subspecs/kmp-modular-architecture-hardening.md`](specs/active/subspecs/kmp-modular-architecture-hardening.md) — dependency-direction, typed-error, shared settings/package/atomic-install, analytical-query DRY, and source-responsibility gates 1–5 are implemented; later physical-module gates run between completed product increments where they protect the next feature boundary.

Previously implemented Desktop analysis workspace, Desktop map, and user-preference increments remain verification-pending; changing the coding focus does not mark their acceptance complete.

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

The active Favorites/import/notebook sub-spec covers favorites-style notebook entries, notes, imported settlement candidate lists, batch external search, and portable export/share, interleaved with bounded KMP architecture-hardening checkpoints. Follow-up P1 work remains:

- named saved filters/searches;
- richer comparison of multiple settlements;
- reusable named preference profiles.

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
