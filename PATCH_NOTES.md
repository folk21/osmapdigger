# PATCH

Record the completed representative Belarus Preference calibration for the current `balanced-living` defaults and narrow `settlement-shortlist-workflow` to genuinely remaining acceptance.

The recorded Belarus package contains 22,771 settlements. With the current forest thresholds, `forest.distance_km` is 53.4% full quality, 46.4% in transition, and 0.2% at zero quality; no enabled Preference crosses the configured 90% endpoint-saturation warning.

The local Andorra package reported `no such table: metric_preference_default`, so it is explicitly treated as stale relative to the current additive Preference-default contract and must be rebuilt before it can supply independent calibration evidence.

Validation evidence supplied from the configured development workstation:
- `make shortlist-acceptance` -> BUILD SUCCESSFUL.
- `make desktop-headless-acceptance` -> BUILD SUCCESSFUL.
- `make check-all` -> 68 Python tests passed; Desktop and Android host Gradle test tasks BUILD SUCCESSFUL.
- Belarus `make preference-calibration` -> 22,771 settlements; revised forest distribution 53.4% full / 46.4% transition / 0.2% zero; no enabled metric crossed the 90% saturation warning.
- Andorra calibration -> blocked by stale local package without `metric_preference_default`; rebuild required.

Validation run again in the patch workspace: `make check` -> 68 passed, 2 existing PyProj/NumPy warnings.
