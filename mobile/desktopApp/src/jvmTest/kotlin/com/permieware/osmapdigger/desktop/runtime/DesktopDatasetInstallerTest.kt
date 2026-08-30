package com.permieware.osmapdigger.desktop.runtime

import com.permieware.osmapdigger.error.OperationalFailureException
import com.permieware.osmapdigger.error.OperationalFailureKind
import java.nio.file.Files
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopDatasetInstallerTest {
    @Test
    fun invalidReplacementKeepsPreviousInstalledDataset() {
        val root = Files.createTempDirectory("osmapdigger-install-test")
        val target = root.resolve("dataset")
        Files.createDirectories(target)
        target.resolve("marker.txt").writeText("previous")
        val invalidZip = root.resolve("invalid.omd.zip")
        writeZip(invalidZip, mapOf("unrelated.txt" to "broken".toByteArray()))

        val failure =
            assertFailsWith<OperationalFailureException> {
                DesktopDatasetChooser.installZip(invalidZip, target)
            }

        assertEquals(OperationalFailureKind.DATASET_INVALID, failure.failure.kind)
        assertEquals("previous", target.resolve("marker.txt").readText())
    }

    @Test
    fun corruptSqliteReplacementKeepsPreviousInstalledDataset() {
        val root = Files.createTempDirectory("osmapdigger-install-corrupt-db")
        val target = root.resolve("dataset")
        Files.createDirectories(target)
        target.resolve("marker.txt").writeText("previous")
        val zip = root.resolve("corrupt.omd.zip")
        writeZip(
            zip,
            mapOf(
                "metadata.json" to metadataJson(mapName = null).toByteArray(),
                "georisk.sqlite" to "not-a-sqlite-database".toByteArray(),
                "style.template.json" to "{}".toByteArray(),
            ),
        )

        val failure = assertFailsWith<OperationalFailureException> { DesktopDatasetChooser.installZip(zip, target) }

        assertEquals(OperationalFailureKind.DATASET_INVALID, failure.failure.kind)
        assertEquals("previous", target.resolve("marker.txt").readText())
    }

    @Test
    fun safeValidatedReplacementPublishesAfterStaging() {
        val root = Files.createTempDirectory("osmapdigger-install-success")
        val target = root.resolve("dataset")
        Files.createDirectories(target)
        target.resolve("marker.txt").writeText("previous")
        val validZip = root.resolve("valid.omd.zip")
        val sqlite = root.resolve("valid.sqlite")
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${sqlite.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement -> statement.execute("CREATE TABLE fixture(id INTEGER)") }
        }
        writeZip(
            validZip,
            mapOf(
                "metadata.json" to metadataJson(mapName = null).toByteArray(),
                "georisk.sqlite" to sqlite.readBytes(),
                "style.template.json" to "{}".toByteArray(),
                "replacement.txt" to "new".toByteArray(),
            ),
        )

        DesktopDatasetChooser.installZip(validZip, target)

        assertTrue(Files.notExists(target.resolve("marker.txt")))
        assertEquals("new", target.resolve("replacement.txt").readText())
    }

    @Test
    fun traversalEntryIsTypedAsInvalidAndDoesNotReplaceExistingDataset() {
        val root = Files.createTempDirectory("osmapdigger-install-traversal")
        val target = root.resolve("dataset")
        Files.createDirectories(target)
        target.resolve("marker.txt").writeText("previous")
        val zip = root.resolve("traversal.omd.zip")
        writeZip(zip, mapOf("../escape.txt" to "bad".toByteArray()))

        val failure = assertFailsWith<OperationalFailureException> { DesktopDatasetChooser.installZip(zip, target) }

        assertEquals(OperationalFailureKind.DATASET_INVALID, failure.failure.kind)
        assertEquals("previous", target.resolve("marker.txt").readText())
        assertTrue(Files.notExists(root.resolve("escape.txt")))
    }

    private fun writeZip(path: java.nio.file.Path, entries: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }

    private fun metadataJson(mapName: String?): String =
        """
        {
          "datasetId": "fixture",
          "displayName": "Fixture",
          "countryCode": null,
          "center": {"latitude": 0.0, "longitude": 0.0, "zoom": 5.0},
          "artifacts": {"map": ${mapName?.let { "\"$it\"" } ?: "null"}}
        }
        """.trimIndent()
}
