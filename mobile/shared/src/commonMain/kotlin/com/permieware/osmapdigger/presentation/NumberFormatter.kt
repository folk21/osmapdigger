package com.permieware.osmapdigger.presentation

import kotlin.math.roundToInt

/** Compact deterministic numeric formatting shared by human-readable runtime presentation. */
object NumberFormatter {
    fun compact(value: Double): String {
        if (value % 1.0 == 0.0) return value.toInt().toString()
        return ((value * 100.0).roundToInt() / 100.0).toString()
    }
}
