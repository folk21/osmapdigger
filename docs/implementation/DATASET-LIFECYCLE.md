---
type: Implementation
title: Desktop runtime diagnostics
description: Desktop startup, dataset, JCEF map, fatal-error, and shutdown diagnostic behavior.
---
# Desktop runtime diagnostics

Desktop diagnostics are application-owned infrastructure and stay out of shared domain/search code.
The JUL root logger is mirrored to `~/.osmapdigger/logs/desktop.log`, so OsmapDigger lifecycle
messages and third-party JUL output such as jcefmaven initialization can be inspected together.

Startup records a compact runtime fingerprint containing OS/architecture, Java version/vendor/home,
working directory, headless state, and JVM input arguments. Dataset diagnostics cover configuration
discovery, package path/existence/size, install/open timing, opened dataset identity/map availability,
and exceptions. Critical startup failures must not be silently hidden behind an empty dataset state.

On Intel macOS the JCEF path additionally records:

- runtime installation and application-specific CEF root-cache paths;
- JCEF initialization timing and browser/session lifecycle;
- MapLibre GL JS console messages and browser load failures;
- loopback map-server startup/shutdown, PMTiles metadata, request/tile/error counters, and request
  exceptions without logging every successful tile request.

CEF uses `~/.osmapdigger/runtime/jcef-cache` as its explicit `root_cache_path`. JVM fatal-error
reports are directed to `~/.osmapdigger/logs/hs_err_pid<pid>.log`. A
`~/.osmapdigger/runtime/desktop-running` marker is created for the process lifetime and removed by
the normal JVM shutdown hook; if it remains, the next startup reports the previous session as not
cleanly shut down and points to the latest fatal-error report when one exists.

Persistent diagnostics intentionally avoid logging search filter values, settlement names, query
text, or external-search URLs.

During Desktop development, inspect the live log with:

```bash
tail -f ~/.osmapdigger/logs/desktop.log
```

After a native JVM/CEF failure, inspect `~/.osmapdigger/logs/hs_err_pid*.log` together with the
preceding Desktop log entries from the same startup session.
