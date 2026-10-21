# PATCH

Introduce a strict Git-tracked specification processing checkpoint for contributor stages.

- `docs/specs/STATE.json` selects the contributor pipeline at stage A1 / DESIGN, without granting execution or acceptance.
- Schema and read-only semantic validator enforce SINGLE mode, active-spec stage references, bounded fields, rejection semantics, and forbidden runtime/acceptance claims.
- `make check` runs the checkpoint validator before the Python suite; deterministic negative tests cover unsafe/stale/malformed state.
- Repository guidance distinguishes the implemented checkpoint from the still-unimplemented Aider orchestrator, independent reviewer and guarded promotion.

Validation actually performed against the text-only project snapshot with the earlier documentation PATCH overlaid:
- `python3 tools/specs/validate_state.py`: PASS.
- `python3 -m pytest tests/test_spec_state.py tests/test_documentation_contracts.py -q`: 40 passed.
- `PYTHONPATH=geo-builder/src python3 -m pytest tests geo-builder/tests -q --ignore=geo-builder/tests/test_map_builder.py`: 106 passed, 2 PyProj/NumPy warnings.
- `PYTHON=python3 make check`: state validation PASS, 106 passed, 2 failed because the concatenated snapshot omits `geo-builder/tilemaker/process.lua`.

Not verified:
- `make check` on a complete local repository checkout containing the tracked tilemaker process file.
- `make check-all`, native/Android/Gradle checks, live Aider execution, independent reviewer, guarded promotion, or actual Git-base identity.

This is a repository delta relative to the previously delivered Aider documentation PATCH; apply to the corresponding checkout on the same feature branch. Files absent from the text-only snapshot are not evidence of deletion.
