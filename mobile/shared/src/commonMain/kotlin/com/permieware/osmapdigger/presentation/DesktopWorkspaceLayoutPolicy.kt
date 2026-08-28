package com.permieware.osmapdigger.presentation

/** Deterministic wide-Desktop geometry that keeps Compose panels outside the platform map rectangle. */
object DesktopWorkspaceLayoutPolicy {
    const val DEFAULT_SIDEBAR_FRACTION = 0.34f
    const val MIN_SIDEBAR_WIDTH_DP = 420f
    const val MAX_SIDEBAR_WIDTH_DP = 620f
    const val MAP_HEIGHT_WEIGHT = 2f
    const val DETAILS_HEIGHT_WEIGHT = 1f

    fun initialSidebarWidthDp(windowWidthDp: Float): Float {
        require(windowWidthDp > 0f && windowWidthDp.isFinite()) {
            "Window width must be finite and positive."
        }
        return (windowWidthDp * DEFAULT_SIDEBAR_FRACTION)
            .coerceIn(MIN_SIDEBAR_WIDTH_DP, MAX_SIDEBAR_WIDTH_DP)
    }

    fun resizedSidebarWidthDp(currentWidthDp: Float, deltaDp: Float): Float {
        require(currentWidthDp.isFinite() && deltaDp.isFinite()) {
            "Sidebar widths must be finite."
        }
        return (currentWidthDp + deltaDp)
            .coerceIn(MIN_SIDEBAR_WIDTH_DP, MAX_SIDEBAR_WIDTH_DP)
    }
}
