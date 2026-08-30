package com.permieware.osmapdigger.desktop.runtime

import com.permieware.osmapdigger.dataset.DatasetPackageLayout
import com.permieware.osmapdigger.dataset.DatasetPackageMetadataParser
import com.permieware.osmapdigger.dataset.OperationalGeoRepository
import com.permieware.osmapdigger.desktop.diagnostics.DesktopDiagnostics
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary
import com.permieware.osmapdigger.error.operationalFailure
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.map.MapPackage
import com.permieware.osmapdigger.runtime.OsmapDiggerRuntime
import java.awt.Desktop
import java.io.Closeable
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
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
         * Package metadata parsing is shared with Android. Filesystem access, local URI resolution,
         * JDBC lifecycle, and browser integration remain Desktop-owned operational boundaries.
         */
        fun open(directory: Path): DesktopDataset =
            operationalBoundary(OperationalFailureKind.DATASET_STORAGE, "Could not open Desktop dataset directory") {
                val manifest = DesktopDatasetInstaller.validate(directory)
                val metadata = manifest.metadata
                val mapName = metadata.artifacts.map
                val hasMap = mapName != null && Files.isRegularFile(directory.resolve(mapName))
                val info = metadata.toDatasetInfo(hasMap)

                val localMapUri =
                    if (hasMap) {
                        directory.resolve(requireNotNull(mapName)).toAbsolutePath().toUri().toString()
                    } else {
                        null
                    }
                val styleJson =
                    if (localMapUri != null) {
                        val template = Files.readString(directory.resolve(DatasetPackageLayout.STYLE_TEMPLATE_FILE))
                        template.replace(
                            DatasetPackageLayout.PMTILES_URI_PLACEHOLDER,
                            "pmtiles://$localMapUri",
                        )
                    } else {
                        null
                    }

                Class.forName("org.sqlite.JDBC")
                val databaseFile = directory.resolve(DatasetPackageLayout.DATABASE_FILE)
                val connection =
                    operationalBoundary(OperationalFailureKind.DATABASE, "Could not open dataset SQLite database") {
                        DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}").also {
                            // JDBC read-only is advisory for SQLite. Failure to set the hint must not alter
                            // the already read-only repository contract, so this remains best-effort diagnostics.
                            runCatching { it.isReadOnly = true }
                        }
                    }

                try {
                    DesktopDiagnostics.info(
                        "dataset",
                        "opened id=${info.id} hasMap=${info.hasMap} directory=${directory.toAbsolutePath()} database=${databaseFile.toAbsolutePath()}",
                    )

                    val repository = OperationalGeoRepository(JdbcGeoRepository(connection, info))
                    val opener =
                        ExternalLinkOpener { url ->
                            operationalBoundary(OperationalFailureKind.EXTERNAL_ACTION, "Could not open external URL") {
                                if (!Desktop.isDesktopSupported()) {
                                    operationalFailure(
                                        OperationalFailureKind.EXTERNAL_ACTION,
                                        "Desktop browser integration is unavailable",
                                    )
                                }
                                Desktop.getDesktop().browse(URI(url))
                            }
                        }

                    DesktopDataset(
                        runtime = OsmapDiggerRuntime(repository, MapPackage(styleJson, localMapUri), opener),
                        connection = connection,
                    )
                } catch (failure: Throwable) {
                    connection.close()
                    throw failure
                }
            }

        /** Install an immutable portable package through staging and open the published result. */
        fun installAndOpen(packageFile: Path): DesktopDataset {
            if (!Files.exists(packageFile)) {
                operationalFailure(
                    OperationalFailureKind.DATASET_NOT_FOUND,
                    "Dataset package not found: $packageFile",
                )
            }

            val installRoot = Paths.get(System.getProperty("user.home"), ".osmapdigger", "datasets")
            operationalBoundary(OperationalFailureKind.DATASET_STORAGE, "Could not create dataset install directory") {
                Files.createDirectories(installRoot)
            }

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

/** Desktop file chooser and portable-package installer. */
object DesktopDatasetChooser {
    /** User cancellation is normal absence; selected package failures remain typed operational failures. */
    fun chooseAndOpen(): DesktopDataset? {
        val chooser =
            JFileChooser().apply {
                dialogTitle = "Open OsmapDigger dataset directory or .omd.zip"
                fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
                fileFilter = FileNameExtensionFilter("OsmapDigger package (*.zip)", "zip")
            }

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null

        val selected = chooser.selectedFile.toPath()
        DesktopDiagnostics.info("dataset.import", "selected=${selected.toAbsolutePath()}")
        return if (Files.isDirectory(selected)) {
            DesktopDataset.open(selected)
        } else {
            val installRoot = Paths.get(System.getProperty("user.home"), ".osmapdigger", "datasets")
            operationalBoundary(OperationalFailureKind.DATASET_STORAGE, "Could not create dataset install directory") {
                Files.createDirectories(installRoot)
            }
            val name = selected.fileName.toString().removeSuffix(".omd.zip").removeSuffix(".zip")
            val target = installRoot.resolve(name)
            installZip(selected, target)
            DesktopDataset.open(target)
        }
    }

    /**
     * Extract, validate, and publish a package without exposing partial replacement state.
     * Existing installs move to a same-filesystem backup immediately before publication and are
     * restored if publication fails. ZIP path traversal remains rejected during staging extraction.
     */
    internal fun installZip(source: Path, target: Path) {
        DesktopDatasetInstaller.install(source, target)
    }
}

internal data class ValidatedDesktopPackage(
    val metadata: com.permieware.osmapdigger.dataset.DatasetPackageMetadata,
)

/** Filesystem-specific staging/validation/publication implementation for Desktop packages. */
internal object DesktopDatasetInstaller {
    fun validate(directory: Path): ValidatedDesktopPackage =
        operationalBoundary(OperationalFailureKind.DATASET_INVALID, "Invalid Desktop dataset package") {
            val metadataFile = directory.resolve(DatasetPackageLayout.METADATA_FILE)
            val databaseFile = directory.resolve(DatasetPackageLayout.DATABASE_FILE)
            val styleFile = directory.resolve(DatasetPackageLayout.STYLE_TEMPLATE_FILE)
            if (!Files.isRegularFile(metadataFile)) {
                operationalFailure(OperationalFailureKind.DATASET_INVALID, "metadata.json is missing")
            }
            if (!Files.isRegularFile(databaseFile)) {
                operationalFailure(OperationalFailureKind.DATASET_INVALID, "georisk.sqlite is missing")
            }
            if (!Files.isRegularFile(styleFile)) {
                operationalFailure(OperationalFailureKind.DATASET_INVALID, "style.template.json is missing")
            }

            val metadata = DatasetPackageMetadataParser.decode(Files.readString(metadataFile))
            validateDatabase(databaseFile)
            metadata.artifacts.map?.let { mapName ->
                if (!Files.isRegularFile(directory.resolve(mapName))) {
                    operationalFailure(
                        OperationalFailureKind.DATASET_INVALID,
                        "Metadata references missing map artifact: $mapName",
                    )
                }
            }
            ValidatedDesktopPackage(metadata)
        }

    /** SQLite quick-check catches corrupt staged databases before any existing install is moved. */
    private fun validateDatabase(databaseFile: Path) {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA quick_check").use { result ->
                    if (!result.next() || result.getString(1) != "ok") {
                        operationalFailure(OperationalFailureKind.DATASET_INVALID, "Dataset SQLite quick_check failed")
                    }
                }
            }
        }
    }

    fun install(source: Path, target: Path) =
        operationalBoundary(OperationalFailureKind.DATASET_STORAGE, "Could not install Desktop dataset package") {
            if (!Files.isRegularFile(source)) {
                operationalFailure(OperationalFailureKind.DATASET_NOT_FOUND, "Dataset package not found: $source")
            }
            Files.createDirectories(target.toAbsolutePath().parent)
            val suffix = UUID.randomUUID().toString()
            val staging = target.resolveSibling(".${target.fileName}.staging-$suffix")
            val backup = target.resolveSibling(".${target.fileName}.backup-$suffix")
            var backupPublished = false

            try {
                Files.createDirectories(staging)
                extract(source, staging)
                validate(staging)

                if (Files.exists(target)) {
                    moveDirectory(target, backup)
                    backupPublished = true
                }
                try {
                    moveDirectory(staging, target)
                } catch (failure: Throwable) {
                    if (Files.exists(target)) target.toFile().deleteRecursively()
                    if (backupPublished && Files.exists(backup)) moveDirectory(backup, target)
                    backupPublished = false
                    throw failure
                }

                if (backupPublished && Files.exists(backup)) {
                    backup.toFile().deleteRecursively()
                    backupPublished = false
                }
            } finally {
                if (Files.exists(staging)) staging.toFile().deleteRecursively()
                if (backupPublished && Files.exists(backup) && !Files.exists(target)) {
                    moveDirectory(backup, target)
                }
                if (Files.exists(backup) && Files.exists(target)) backup.toFile().deleteRecursively()
            }
        }

    private fun extract(source: Path, staging: Path) {
        val canonicalTarget = staging.toAbsolutePath().normalize()
        ZipInputStream(Files.newInputStream(source)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val resolved = canonicalTarget.resolve(entry.name).normalize()
                if (!resolved.startsWith(canonicalTarget)) {
                    operationalFailure(OperationalFailureKind.DATASET_INVALID, "Unsafe ZIP entry: ${entry.name}")
                }
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

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }
}
