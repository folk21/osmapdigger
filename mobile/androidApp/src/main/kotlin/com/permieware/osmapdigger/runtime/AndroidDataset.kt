package com.permieware.osmapdigger.runtime

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.permieware.osmapdigger.dataset.DatasetPackageLayout
import com.permieware.osmapdigger.dataset.DatasetPackageMetadata
import com.permieware.osmapdigger.dataset.DatasetPackageMetadataParser
import com.permieware.osmapdigger.dataset.OperationalGeoRepository
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary
import com.permieware.osmapdigger.error.operationalFailure
import com.permieware.osmapdigger.external.ExternalLinkOpener
import com.permieware.osmapdigger.map.MapPackage
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** Installed Android dataset and SQLite lifecycle owner. */
class AndroidDataset private constructor(
    val runtime: OsmapDiggerRuntime,
    private val database: SQLiteDatabase,
) : Closeable {
    override fun close() {
        database.close()
    }

    companion object {
        /** Open an installed package using shared package metadata semantics and Android-owned resources. */
        fun open(context: Context, directory: File): AndroidDataset =
            operationalBoundary(OperationalFailureKind.DATASET_STORAGE, "Could not open Android dataset directory") {
                val metadata = AndroidDatasetInstaller.validate(directory)
                val mapName = metadata.artifacts.map
                val hasMap = mapName != null && File(directory, mapName).isFile
                val info = metadata.toDatasetInfo(hasMap)
                val databaseFile = File(directory, DatasetPackageLayout.DATABASE_FILE)

                val styleJson =
                    if (hasMap) {
                        val template = File(directory, DatasetPackageLayout.STYLE_TEMPLATE_FILE).readText()
                        val mapFile = File(directory, requireNotNull(mapName))
                        template.replace(
                            DatasetPackageLayout.PMTILES_URI_PLACEHOLDER,
                            "pmtiles://file://${mapFile.absolutePath}",
                        )
                    } else {
                        null
                    }

                val database =
                    operationalBoundary(OperationalFailureKind.DATABASE, "Could not open dataset SQLite database") {
                        SQLiteDatabase.openDatabase(
                            databaseFile.absolutePath,
                            null,
                            SQLiteDatabase.OPEN_READONLY,
                        )
                    }

                try {
                    val repository = OperationalGeoRepository(AndroidGeoRepository(database, info))
                    val opener =
                        ExternalLinkOpener { url ->
                            operationalBoundary(OperationalFailureKind.EXTERNAL_ACTION, "Could not open external URL") {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    AndroidDataset(
                        OsmapDiggerRuntime(repository, MapPackage(styleJson), opener),
                        database,
                    )
                } catch (failure: Throwable) {
                    database.close()
                    throw failure
                }
            }

        fun currentDirectory(context: Context): File = File(context.filesDir, "datasets/current")
    }
}

/** Secure staging-based package installer for a `.omd.zip` selected through Storage Access Framework. */
object AndroidDatasetInstaller {
    /**
     * Extract and validate a replacement before publishing it as the current package.
     * The previous package is renamed to a same-filesystem backup only after staging validation;
     * publication failure restores that backup. ZIP path traversal remains rejected.
     */
    fun install(context: Context, uri: Uri): File =
        operationalBoundary(OperationalFailureKind.DATASET_STORAGE, "Could not install Android dataset package") {
            val target = AndroidDataset.currentDirectory(context)
            val parent = target.parentFile ?: operationalFailure(
                OperationalFailureKind.DATASET_STORAGE,
                "Dataset install directory has no parent",
            )
            if (!parent.exists() && !parent.mkdirs()) {
                operationalFailure(OperationalFailureKind.DATASET_STORAGE, "Could not create dataset install directory")
            }

            val suffix = UUID.randomUUID().toString()
            val staging = File(parent, ".${target.name}.staging-$suffix")
            val backup = File(parent, ".${target.name}.backup-$suffix")
            var backupPublished = false

            try {
                if (!staging.mkdirs()) {
                    operationalFailure(OperationalFailureKind.DATASET_STORAGE, "Could not create staging directory")
                }
                val raw = context.contentResolver.openInputStream(uri)
                    ?: operationalFailure(OperationalFailureKind.DATASET_STORAGE, "Cannot open selected dataset")
                raw.use { input -> extract(input = input, staging = staging) }
                validate(staging)

                if (target.exists()) {
                    if (!target.renameTo(backup)) {
                        operationalFailure(OperationalFailureKind.DATASET_STORAGE, "Could not stage previous dataset backup")
                    }
                    backupPublished = true
                }
                if (!staging.renameTo(target)) {
                    if (target.exists()) target.deleteRecursively()
                    if (backupPublished && backup.exists()) {
                        if (!backup.renameTo(target)) {
                            operationalFailure(
                                OperationalFailureKind.DATASET_STORAGE,
                                "Dataset publication and rollback both failed",
                            )
                        }
                    }
                    backupPublished = false
                    operationalFailure(OperationalFailureKind.DATASET_STORAGE, "Could not publish staged dataset")
                }

                if (backupPublished && backup.exists()) {
                    backup.deleteRecursively()
                    backupPublished = false
                }
                target
            } finally {
                if (staging.exists()) staging.deleteRecursively()
                if (backupPublished && backup.exists() && !target.exists()) {
                    backup.renameTo(target)
                }
                if (backup.exists() && target.exists()) backup.deleteRecursively()
            }
        }

    internal fun validate(directory: File): DatasetPackageMetadata =
        operationalBoundary(OperationalFailureKind.DATASET_INVALID, "Invalid Android dataset package") {
            val metadataFile = File(directory, DatasetPackageLayout.METADATA_FILE)
            val databaseFile = File(directory, DatasetPackageLayout.DATABASE_FILE)
            val styleFile = File(directory, DatasetPackageLayout.STYLE_TEMPLATE_FILE)
            if (!metadataFile.isFile) {
                operationalFailure(OperationalFailureKind.DATASET_INVALID, "metadata.json is missing")
            }
            if (!databaseFile.isFile) {
                operationalFailure(OperationalFailureKind.DATASET_INVALID, "georisk.sqlite is missing")
            }
            if (!styleFile.isFile) {
                operationalFailure(OperationalFailureKind.DATASET_INVALID, "style.template.json is missing")
            }

            val metadata = DatasetPackageMetadataParser.decode(metadataFile.readText())
            validateDatabase(databaseFile)
            metadata.artifacts.map?.let { mapName ->
                if (!File(directory, mapName).isFile) {
                    operationalFailure(
                        OperationalFailureKind.DATASET_INVALID,
                        "Metadata references missing map artifact: $mapName",
                    )
                }
            }
            metadata
        }

    /** SQLite quick-check catches corrupt staged databases before the current install is renamed. */
    private fun validateDatabase(databaseFile: File) {
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA quick_check", emptyArray<String>()).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    operationalFailure(OperationalFailureKind.DATASET_INVALID, "Dataset SQLite quick_check failed")
                }
            }
        }
    }

    private fun extract(input: java.io.InputStream, staging: File) {
        val canonicalRoot = staging.canonicalFile
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val output = File(canonicalRoot, entry.name).canonicalFile
                if (!output.path.startsWith(canonicalRoot.path + File.separator)) {
                    operationalFailure(OperationalFailureKind.DATASET_INVALID, "Unsafe ZIP entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { destination -> zip.copyTo(destination) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
