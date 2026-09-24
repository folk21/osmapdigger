# PATCH

Add strict full-package acceptance validation for the current Andorra build path.

`make andorra-full-package-acceptance` now rebuilds Andorra with PMTiles and validates the published package with `--require-map --require-archive`. Strict validation requires a non-empty metadata-declared PMTiles artifact, a non-empty portable `.omd.zip`, the required runtime archive entries, and a PMTiles archive entry whose size matches the published map artifact.

Normal staging validation and data-only packages remain valid without PMTiles or a portable archive requirement.

Validation performed:
- focused package/pipeline/documentation tests: 11 passed, 2 existing PyProj/NumPy warnings;
- `make check`: 73 passed, 2 existing PyProj/NumPy warnings;
- `make -n andorra-full-package-acceptance`: confirmed full build followed by strict validation.

Not verified in this environment:
- real `make andorra-full-package-acceptance`, because the shareable baseline does not contain the ignored Andorra PBF or a configured tilemaker/Docker runtime;
- native Desktop/Android rendering/import behavior, which remains a separate acceptance layer.
