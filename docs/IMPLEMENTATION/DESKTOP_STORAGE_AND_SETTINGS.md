# Desktop storage and settings

## Dataset auto import

Desktop can load a configured `.omd.zip` package automatically during startup.

The path is defined in local desktop configuration:

```yaml
dataset:
  autoImportPackage: "data/generated/andorra.omd.zip"
```

Manual import remains available as a fallback.

## Persistent desktop state

Desktop keeps user state separately from generated GIS datasets.

Stored state includes:

- last selected dataset;
- last search filters;
- center settlement;
- radius;
- UI preferences.

The storage location is:

```
~/.osmapdigger/
```

The generated dataset directory is not removed by `make run-desktop` or Gradle clean.

## Search history

Search criteria are user data and should survive application restart.

The implementation should keep a lightweight local SQLite database for desktop preferences/history.

Suggested table:

```
search_history
---------------
id
dataset_id
created_at
center_settlement
radius_km
filters_json
```
