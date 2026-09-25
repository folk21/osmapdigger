# PATCH

Harden Intel macOS JCEF startup after repeated native `libjcef.dylib` crashes.

The fatal reports show the same SIGSEGV inside the JCEF `NSApplication(JCEFApplication)` AppKit hook while `libjcef.dylib` is loaded after the Compose/AWT event loop is already active. The Desktop host now prepares the Intel macOS JCEF runtime before entering Compose `application {}` instead of lazily loading it from the map Composable. An explicit `OSMAPDIGGER_DISABLE_INTEL_JCEF=1` safe-mode switch keeps the analytical fallback usable during platform diagnostics.

Validation performed:
- `make check` -> 74 passed, 2 existing PyProj/NumPy warnings;
- network-free architecture coverage verifies JCEF bootstrap remains before Compose application startup;
- Kotlin unit coverage was added for Intel-host renderer disable selection.

Attempted but not completed in this environment:
- `./gradlew :desktopApp:jvmTest --tests "com.permieware.osmapdigger.desktop.map.IntelMacWebMapSurfaceTest"` -> Gradle wrapper could not resolve `services.gradle.org` because network/DNS is unavailable.

Required configured/native follow-up:
- run `make check-all` on the configured workstation;
- run at least three fresh Intel macOS `make run-desktop` launches with JCEF enabled and verify no new fatal `hs_err_pid*.log` is created;
- then run the normal offline map marker/selection/focus checks;
- if a native crash still occurs, use `OSMAPDIGGER_DISABLE_INTEL_JCEF=1 make run-desktop` to keep the analytical workflow usable while the native integration remains verification-pending.
