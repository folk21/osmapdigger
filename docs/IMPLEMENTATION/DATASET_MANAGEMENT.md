# Dataset management

## Purpose

Dataset lifecycle is a shared application concept. Desktop and Android use different storage
mechanisms but follow the same logical model.

## Dataset package

The application uses `.omd.zip` packages containing metadata, SQLite analytical data and map
artifacts.

Dataset identity is defined by metadata, not by filename.

## Configuration

The default dataset is selected through application configuration.

Example:

```yaml
application:
  defaultDataset: belarus

datasets:
  belarus:
    package: belarus.omd.zip
```

## Platform implementation

### Desktop

- Reads local configuration.
- Can automatically import a configured dataset package.
- Stores installed datasets in user storage.
- Stores user preferences separately.

Recommended:

```
~/.osmapdigger/
  datasets/
  settings.sqlite
```

### Android

- Reads bundled application configuration.
- Installs the default dataset on first start.
- Uses application internal storage.
- Uses the same logical dataset manager API.

## Platform differences

| Area | Desktop | Android |
|---|---|---|
| Configuration | local file | assets/application config |
| Storage | user directory | app internal storage |
| Default dataset | configured package | bundled/downloaded package |
| Import | file picker/automatic | first run installation |

## Design rule

Business logic must not depend on platform storage details.
