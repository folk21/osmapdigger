# PATCH

Add a strict headless real-package ranked-analysis acceptance path for the next P0 Desktop analysis gate.

The new `make desktop-headless-analysis-acceptance` target opens a generated package through the real Desktop package/JDBC adapters and the same shared `AnalysisWorkspaceController` used by Compose. It uses an isolated temporary settings SQLite, requires a representative candidate volume and at least one enabled scoring metric, and prints copy-ready retrieval/shared/end-to-end diagnostics from the completed current analysis generation.

This does not replace native Desktop acceptance. Map renderer behavior, layout/non-overlap, map/list interaction, and UI responsiveness still require the existing native checks and `analysis-acceptance-strict` log evidence where applicable.

Validation in the patch-generation environment:

- `python -m pytest -q tests/test_documentation_contracts.py tests/test_archive_contracts.py tests/test_mobile_architecture.py` -> 19 passed.
- `make check` -> 68 passed, 2 existing PyProj/NumPy warnings.
- Focused `:desktopApp:jvmTest` was attempted but could not start because the environment could not resolve `services.gradle.org`; Kotlin/Gradle validation remains to be rerun on the configured workstation.
