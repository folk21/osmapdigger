# PATCH

Finish the previously incomplete documentation/archive cleanup against the current FULL repository.

The obsolete `docs/specs/planned/user-preferences.md` duplicate is deleted. The authoritative verification-pending specification remains `docs/specs/active/subspecs/user-preferences.md`.

`archive.sh` now excludes `.vscode/` plus repository-root pytest/mypy/ruff/Python cache metadata in addition to the existing nested IDE/build/cache exclusions. A deterministic repository test protects these shareable-FULL archive exclusions.

Validation:

- `python -m pytest -q tests/test_documentation_contracts.py tests/test_archive_contracts.py` -> 7 passed.
- `make check` -> 68 passed, 2 existing PyProj/NumPy deprecation warnings.
- Functional `archive.sh` smoke -> generated FULL contained no `.vscode`, `.idea`, `.pytest_cache`, `__pycache__`, build, `.gradle`, or `.kotlin` entries and did not contain the deleted planned spec.

`make check-all` was not run because this patch changes no Kotlin/runtime code or Gradle contracts.

Commit summary: remove the superseded planned preferences spec and harden FULL archive metadata exclusions.
