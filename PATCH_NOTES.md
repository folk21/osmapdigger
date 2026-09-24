# PATCH

Strengthen configured Android validation before the remaining native shortlist acceptance.

- Add `build-android` to the `make check-all` dependency set.
- Keep shared Android-host tests and additionally compile the real `androidApp` host with `:androidApp:assembleDebug`.
- Add a network-free repository contract test so the configured gate cannot silently stop compiling `androidApp`.
- Clarify that remaining Android acceptance is device/emulator runtime evidence for import, share, persistence, map, and local analysis rather than basic compilation.

Validation performed in the patch environment:
- `python -m pytest -q tests/test_mobile_architecture.py` -> 13 passed.
- `make check` -> 70 passed, 2 existing PyProj/NumPy warnings.
- `make -n check-all` -> includes Desktop tests, shared Android-host tests, and `:androidApp:assembleDebug`.
- `make check-all` was attempted but Gradle could not start because `services.gradle.org` DNS/network access is unavailable in this environment.

Not verified here:
- Actual `:androidApp:assembleDebug` compilation after this gate change.
- Device/emulator Android import/share/persistence/map behavior.
- Native/manual Desktop shortlist workflow acceptance.
