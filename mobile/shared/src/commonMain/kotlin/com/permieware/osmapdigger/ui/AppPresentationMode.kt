package com.permieware.osmapdigger.ui

/** Host-selected top-level presentation mode for platform-specific product workflows. */
enum class AppPresentationMode {
    /** Existing shared responsive Search/Map workflow used by Android and compatibility hosts. */
    RESPONSIVE,

    /** Wide map-first analytical workspace enabled explicitly by the Desktop host. */
    DESKTOP_ANALYSIS,
}
