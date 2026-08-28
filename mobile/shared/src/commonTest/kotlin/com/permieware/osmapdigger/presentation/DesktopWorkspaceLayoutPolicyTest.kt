package com.permieware.osmapdigger.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopWorkspaceLayoutPolicyTest {
    @Test
    fun `default sidebar is roughly one third of a normal Desktop window`() {
        assertEquals(
            476f,
            DesktopWorkspaceLayoutPolicy.initialSidebarWidthDp(1400f),
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun `default sidebar remains inside supported bounds`() {
        assertEquals(420f, DesktopWorkspaceLayoutPolicy.initialSidebarWidthDp(900f))
        assertEquals(620f, DesktopWorkspaceLayoutPolicy.initialSidebarWidthDp(2400f))
    }

    @Test
    fun `drag resizing is clamped to supported bounds`() {
        assertEquals(420f, DesktopWorkspaceLayoutPolicy.resizedSidebarWidthDp(450f, -100f))
        assertEquals(620f, DesktopWorkspaceLayoutPolicy.resizedSidebarWidthDp(580f, 100f))
        assertEquals(500f, DesktopWorkspaceLayoutPolicy.resizedSidebarWidthDp(470f, 30f))
    }

    @Test
    fun `invalid window width fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopWorkspaceLayoutPolicy.initialSidebarWidthDp(0f)
        }
    }
}
