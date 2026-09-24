# PATCH

Record the completed current-package Andorra Preference calibration as shortlist acceptance evidence.

- Keep Belarus as the representative product-default calibration dataset.
- Record Andorra as an independent 75-settlement sanity check with expected compact-geography saturation.
- Record that missing `beach.distance_km` remains unknown and yields 83.673% weighted coverage instead of zero quality.
- Remove Andorra calibration from the remaining `settlement-shortlist-workflow` acceptance gap.
- Leave product defaults unchanged; the calibration warning remains review evidence rather than runtime adaptation.

Validation performed in the patch environment:
- `python -m pytest -q tests/test_documentation_contracts.py` -> 6 passed.
- `make check` -> 69 passed, 2 existing PyProj/NumPy warnings.

Not verified here:
- Native/manual Desktop shortlist workflow acceptance.
- Android import/share/persistence acceptance.
