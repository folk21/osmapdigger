# PATCH

Align OsmapDigger contributor documentation and staged specification conventions with the safe MAIN_MODEL / WORKER_MODEL design reference from SignalHarvester, without claiming its agent orchestration code is present here.

- Add the normative A1–A5 development-agent-pipeline sub-spec and an advisory file-by-file implementation plan.
- Clarify explicit ChatGPT workflow modes, single feature-branch ownership, developer acceptance, source-bound verification, and paired spec/plan lifecycle.
- Preserve the existing product P0 acceptance focus and real OsmapDigger `make check` / `make check-all` commands.
- Teach the documentation contract tests to exclude advisory companion plans from the normative active-spec index.
- No Aider executable, independent reviewer, Git promotion, credentials or Git-tracked runtime STATE register has been added.

Validation performed on the reconstructed concatenated text snapshot:
- `PYTHONPATH=geo-builder/src python -m pytest tests/test_documentation_contracts.py -q`: 7 passed.
- `make check`: 73 passed, 2 failed. Both failures are in `geo-builder/tests/test_map_builder.py` because `geo-builder/tilemaker/process.lua` is omitted by the text snapshot (the actual project checkout was not available). The failures are not attributed to documentation edits.
- `PYTHONPATH=geo-builder/src python -m pytest geo-builder/tests tests -q -k 'not map_builder'`: 73 passed, 2 deselected, 2 existing PyProj/NumPy deprecation warnings.

Not verified: canonical `make check` on a complete checkout, `make check-all`/KMP/Android configured gates, native smoke, Git source/HEAD integrity, any actual Aider worker/reviewer/controller functionality. No Git HEAD/branch metadata is provided by this OsmapDigger snapshot.
