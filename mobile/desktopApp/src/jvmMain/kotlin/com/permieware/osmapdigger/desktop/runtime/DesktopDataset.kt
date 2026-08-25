package com.permieware.osmapdigger.desktop.runtime

import com.permieware.osmapdigger.desktop.diagnostics.DesktopDiagnostics
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.runtime.*
import kotlinx.serialization.json.*
import java.awt.Desktop
import java.io.Closeable
import java.net.URI
import java.nio.file.*
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.ZipInputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Desktop installed/open dataset with JDBC lifecycle ownership. */
class DesktopDataset private constructor(
    val runtime: OsmapDiggerRuntime,
    private val connection: Connection,
) : Closeable {
    override fun close() {
        DesktopDiagnostics.info("dataset", "Closing Desktop dataset")
        connection.close()
    }

    companion object {
        /**
         * Open one unpacked generated dataset directory and wire its platform runtime.
         *
         * The method validates required files before opening SQLite, requests read-only
         * JDBC behavior, and resolves the PMTiles placeholder against the absolute local
         * installation path. It performs no OSM/PBF processing.
         */
        fun open(directory: Path): DesktopDataset {
            val metadataFile = directory.resolve("metadata.json")
            val databaseFile = directory.resolve("georisk.sqlite")
            require(Files.exists(metadataFile)) { "metadata.json not found in $directory" }
            require(Files.exists(databaseFile)) { "georisk.sqlite not found in $directory" }

            val metadata = Json.parseToJsonElement(Files.readString(metadataFile)).jsonObject
            val center = metadata.getValue("center").jsonObject
            val datasetId = metadata.getValue("datasetId").jsonPrimitive.content
            val propertySearch = metadata["propertySearch"]?.jsonObject
            val mapName =
                metadata["artifacts"]
                    ?.jsonObject
                    ?.get("map")
                    ?.jsonPrimitive
                    ?.contentOrNull

            val info =
                DatasetInfo(
                    id = datasetId,
                    displayName = metadata.getValue("displayName").jsonPrimitive.content,
                    countryCode = metadata["countryCode"]?.jsonPrimitive?.contentOrNull,
                    center =
                        GeoPoint(
                            latitude = center.getValue("latitude").jsonPrimitive.double,
                            longitude = center.getValue("longitude").jsonPrimitive.double,
                        ),
                    initialZoom = center.getValue("zoom").jsonPrimitive.double,
                    hasMap = mapName != null && Files.exists(directory.resolve(mapName)),
                    propertySearchSite = propertySearch?.get("site")?.jsonPrimitive?.contentOrNull,
                    propertySearchTerms = propertySearch?.get("terms")?.jsonPrimitive?.contentOrNull ?: "property",
                )

            Class.forName("org.sqlite.JDBC")
            val connection =
                DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}").also {
                    runCatching { it.isReadOnly = true }
                }

            val localMapUri =
                if (info.hasMap) {
                    directory.resolve(mapName!!).toAbsolutePath().toUri().toString()
                } else {
                    null
                }
            val styleJson =
                if (localMapUri != null) {
                    val template = Files.readString(directory.resolve("style.template.json"))
                    template.replace("{{PMTILES_URI}}", "pmtiles://$localMapUri")
                } else {
                    null
                }

            DesktopDiagnostics.info(
                "dataset",
                "opened id=${info.id} hasMap=${info.hasMap} directory=${directory.toAbsolutePath()} database=${databaseFile.toAbsolutePath()}",
            )

            val repository = JdbcGeoRepository(connection, info)
            val opener =
                ExternalLinkOpener { url ->
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(url))
                    }
                }

            return DesktopDataset(
                runtime = OsmapDiggerRuntime(repository, MapPackage(styleJson, localMapUri), opener),
                connection = connection,
            )
        }

        /**
         * Install an .omd.zip package and open it.
         *
         * Generated packages are immutable input artifacts. Installed datasets are stored
         * separately under the user's OsmapDigger directory.
         */
        fun installAndOpen(packageFile: Path): DesktopDataset {
            require(Files.exists(packageFile)) { "Dataset package not found: $packageFile" }

            val installRoot =
                Paths.get(System.getProperty("user.home"), ".osmapdigger", "datasets")
            Files.createDirectories(installRoot)

            val name = packageFile.fileName.toString().removeSuffix(".omd.zip")
            val target = installRoot.resolve(name)

            DesktopDiagnostics.info(
                "dataset.install",
                "source=${packageFile.toAbsolutePath()} target=${target.toAbsolutePath()}",
            )
            DesktopDatasetChooser.installZip(packageFile, target)

            return open(target)
        }
    }
}

/** Desktop file chooser and safe ZIP installer. */
object DesktopDatasetChooser {
    /**
     * Let the user select either an unpacked package directory or a portable ZIP.
     * ZIP packages are installed under the user's OsmapDigger data directory before
     * opening so MapLibre and SQLite receive stable local filesystem paths.
     */
    fun chooseAndOpen(): DesktopDataset? {
        val chooser =
            JFileChooser().apply {
                dialogTitle = "Open OsmapDigger dataset directory or .omd.zip"
                fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
                fileFilter = FileNameExtensionFilter("OsmapDigger package (*.zip)", "zip")
            }

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null
        }

        val selected = chooser.selectedFile.toPath()
        DesktopDiagnostics.info("dataset.import", "selected=${selected.toAbsolutePath()}")
        return if (Files.isDirectory(selected)) {
            DesktopDataset.open(selected)
        } else {
            val installRoot = Paths.get(System.getProperty("user.home"), ".osmapdigger", "datasets")
            Files.createDirectories(installRoot)
            val name = selected.fileName.toString().removeSuffix(".omd.zip").removeSuffix(".zip")
            val target = installRoot.resolve(name)
            installZip(selected, target)
            DesktopDataset.open(target)
        }
    }

    /**
     * Extract a portable package while rejecting entries that escape the destination.
     * The normalized path check is a security boundary and must not be removed when
     * changing package import behavior.
     */
    internal fun installZip(source: Path, target: Path) {
        if (Files.exists(target)) {
            target.toFile().deleteRecursively()
        }
        Files.createDirectories(target)
        val canonicalTarget = target.toAbsolutePath().normalize()

        ZipInputStream(Files.newInputStream(source)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val resolved = canonicalTarget.resolve(entry.name).normalize()
                require(resolved.startsWith(canonicalTarget)) { "Unsafe ZIP entry: ${entry.name}" }
                if (entry.isDirectory) {
                    Files.createDirectories(resolved)
                } else {
                    Files.createDirectories(resolved.parent)
                    Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
