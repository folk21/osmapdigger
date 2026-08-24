# Dataset manager implementation

## Goal

Dataset management provides one logical API for Desktop and Android.

## Current implementation status

This patch introduces shared contracts and configuration format.

## Desktop

Desktop implementation should:

- load local configuration;
- resolve configured package;
- automatically open/import dataset;
- keep installed datasets outside the source tree.

Recommended location:

```
~/.osmapdigger/datasets/
```

## Android

Android implementation should:

- read configuration from assets;
- install the configured initial dataset on first launch;
- keep dataset in internal application storage.

## Design rule

UI must not know whether dataset came from Desktop filesystem or Android assets.
