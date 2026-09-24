# PATCH

Record successful representative Andorra full-package publication acceptance.

The current Andorra data-only and strict full-package builder/publication evidence is now complete. The roadmap and active umbrella no longer treat PMTiles/package generation as pending. Native Desktop rendering and Android package import remain separate verification-pending acceptance layers. Acceptance-map wording was also reconciled with already recorded configured and calibration evidence.

Validation performed:
- `python -m pytest -q tests/test_documentation_contracts.py` -> 6 passed;
- `make check` -> 73 passed, 2 existing PyProj/NumPy warnings.

Not re-run here:
- `make check-all`, because this patch changes acceptance documentation only;
- real full-package build, which was recorded from the developer workstation before this evidence patch.
