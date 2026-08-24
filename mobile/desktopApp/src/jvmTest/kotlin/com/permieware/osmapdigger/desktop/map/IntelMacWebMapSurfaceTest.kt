package com.permieware.osmapdigger.desktop.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntelMacWebMapSurfaceTest {
    @Test
    fun supportsOnlyIntelMacHosts() {
        assertTrue(IntelMacWebMapSurface.isSupportedHost("Mac OS X", "x86_64"))
        assertTrue(IntelMacWebMapSurface.isSupportedHost("Mac OS X", "amd64"))
        assertFalse(IntelMacWebMapSurface.isSupportedHost("Mac OS X", "aarch64"))
        assertFalse(IntelMacWebMapSurface.isSupportedHost("Linux", "x86_64"))
        assertFalse(IntelMacWebMapSurface.isSupportedHost("Windows 11", "amd64"))
    }
}
