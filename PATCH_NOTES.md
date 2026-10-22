# PATCH

Reduce repeated contributor guidance and make direct `DEFAULT` implementation unambiguous.

- Keep architecture, code, compatibility, and testing invariants in root/local `AGENTS.md` and their owning contracts.
- Keep `STATE.json`, schema, validator, and the active pipeline specification unchanged; do not require or trigger delegation from ordinary work.
- Remove ChatGPT-specific handoff instructions from the repository usage guide; keep optional tooling only as brief references.
- Add a documentation regression test for independent `DEFAULT` operation.

## Verification on reconstructed text snapshot

- `git diff --check` against a temporary reconstructed baseline: PASS.
- `python tools/specs/validate_state.py`: PASS (read-only checkpoint validation).
- Documentation and checkpoint tests: 41 passed.
- Python suite excluding the unavailable `geo-builder/tests/test_map_builder.py`: 107 passed, 2 warnings.
- `PYTHON=/opt/pyvenv/bin/python make check`: 107 passed, 2 failed because the supplied text snapshot does not include `geo-builder/tilemaker/process.lua`.
- A new isolated `.venv` was created, but `pip install -e './geo-builder[dev]'` could not resolve build dependencies because the package index/network is unavailable. Tests used the container's preinstalled Python environment instead.

Not verified: full `make check` on the user's checkout, `make check-all`, native/platform tests, live Aider, real Git HEAD/branch/clean state. The concatenated snapshot does not establish an OsmapDigger Git baseline. Do not infer deletion of files omitted from snapshot.
