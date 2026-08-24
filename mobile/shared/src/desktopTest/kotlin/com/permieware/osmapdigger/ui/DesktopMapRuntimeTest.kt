package com.permieware.osmapdigger.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopMapRuntimeTest {
    @Test
    fun resolvesPublishedDesktopCapabilities() {
        assertEquals(
            "macos-aarch64-metal",
            DesktopMapRuntime.capability("Mac OS X", "aarch64"),
        )
        assertEquals(
            "macos-aarch64-metal",
            DesktopMapRuntime.capability("Mac OS X", "arm64"),
        )
        assertEquals(
            "linux-amd64-opengl",
            DesktopMapRuntime.capability("Linux", "x86_64"),
        )
        assertEquals(
            "windows-amd64-opengl",
            DesktopMapRuntime.capability("Windows 11", "amd64"),
        )
    }

    @Test
    fun rejectsHostsWithoutPublishedRuntime() {
        assertNull(DesktopMapRuntime.capability("Mac OS X", "x86_64"))
        assertNull(DesktopMapRuntime.capability("Linux", "aarch64"))
    }
}
