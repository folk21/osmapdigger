# Desktop storage and settings

## Scope

This document describes current Desktop storage behavior and points to planned preference persistence.
It must not describe unimplemented user-state persistence as current behavior.

## Dataset auto import

Desktop can load a configured `.omd.zip` package automatically during startup.

The repository-local default package path is defined in `config/desktop-config.json`. Manual import
remains available as a fallback.

Installed datasets live under the Desktop application storage root and are independent of Gradle
build outputs. Generated packages under `data/generated/packages/` are installation sources, not the
location for mutable user state.

## Current persistent state

Desktop currently persists installed dataset files under the application storage root. Search criteria
such as center settlement, radius, and dynamic filters are not yet restored as authoritative user
preferences after restart.

Generated dataset artifacts remain read-only runtime inputs and must not be modified to store user
state.

## Planned user preferences

Persistent search preferences are defined by the planned
[`../specs/planned/user-preferences.md`](../specs/planned/user-preferences.md) architectural initiative.

The intended Desktop storage location is:

```text
~/.osmapdigger/settings/preferences.sqlite
```

The first implementation will store the current dataset-scoped search state rather than a search
history log. The settings database is application-owned user data and is separate from both installed
dataset SQLite files and the versioned `geo-format` schema.
