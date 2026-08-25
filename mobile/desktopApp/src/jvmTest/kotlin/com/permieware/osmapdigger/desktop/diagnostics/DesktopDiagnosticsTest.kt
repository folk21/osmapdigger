package com.permieware.osmapdigger.desktop.diagnostics

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDiagnosticsTest {
    @Test
    fun diagnosticPathsStayInsideApplicationStorage() {
        val home = Files.createTempDirectory("osmapdigger-diagnostics-home-")
        try {
            val paths = DesktopDiagnosticsPaths.underHome(home)

            assertEquals(home.resolve(".osmapdigger/logs"), paths.logsDirectory)
            assertEquals(home.resolve(".osmapdigger/logs/desktop.log"), paths.logFile)
            assertEquals(home.resolve(".osmapdigger/runtime"), paths.runtimeDirectory)
            assertEquals(home.resolve(".osmapdigger/runtime/desktop-running"), paths.sessionMarker)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun sessionMarkerReportsPreviousSessionAndCleansUp() {
        val directory = Files.createTempDirectory("osmapdigger-session-marker-")
        try {
            val path = directory.resolve("desktop-running")
            val marker = DesktopSessionMarker(path)

            assertNull(marker.begin("sessionId=first\n"))
            assertTrue(Files.exists(path))
            assertEquals("sessionId=first\n", marker.begin("sessionId=second\n"))
            assertEquals("sessionId=second\n", Files.readString(path))

            marker.complete()
            assertFalse(Files.exists(path))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
