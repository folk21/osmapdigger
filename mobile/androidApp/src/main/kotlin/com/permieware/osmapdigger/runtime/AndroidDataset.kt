package com.permieware.osmapdigger.runtime

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import org.json.JSONObject
import java.io.Closeable
import java.io.File
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
        /**
         * Open an installed app-private dataset and create the shared runtime wiring.
         *
         * SQLite is opened read-only and the generated style template is rewritten only
         * with the installed PMTiles file URI; source PBF data is never read at runtime.
         */
        fun open(context: Context, directory: File): AndroidDataset {
            val metadataFile = File(directory, "metadata.json")
            val databaseFile = File(directory, "georisk.sqlite")
            require(metadataFile.isFile) { "metadata.json missing" }
            require(databaseFile.isFile) { "georisk.sqlite missing" }

            val metadata = JSONObject(metadataFile.readText())
            val center = metadata.getJSONObject("center")
            val artifacts = metadata.getJSONObject("artifacts")
            val propertySearch = metadata.optJSONObject("propertySearch")
            val mapName = if (artifacts.isNull("map")) null else artifacts.getString("map")

            val info =
                DatasetInfo(
                    id = metadata.getString("datasetId"),
                    displayName = metadata.getString("displayName"),
                    countryCode = if (metadata.isNull("countryCode")) null else metadata.getString("countryCode"),
                    center = GeoPoint(center.getDouble("latitude"), center.getDouble("longitude")),
                    initialZoom = center.getDouble("zoom"),
                    hasMap = mapName != null && File(directory, mapName).isFile,
                    propertySearchSite = propertySearch?.optString("site")?.takeIf { it.isNotBlank() && it != "null" },
                    propertySearchTerms = propertySearch?.optString("terms", "property") ?: "property",
                )

            val database =
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )

            val styleJson =
                if (info.hasMap) {
                    val template = File(directory, "style.template.json").readText()
                    val mapFile = File(directory, requireNotNull(mapName))
                    template.replace("{{PMTILES_URI}}", "pmtiles://file://${mapFile.absolutePath}")
                } else {
                    null
                }

            val repository = AndroidGeoRepository(database, info)
            val opener =
                ExternalLinkOpener { url ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }

            return AndroidDataset(
                OsmapDiggerRuntime(repository, MapPackage(styleJson), opener),
                database,
            )
        }

        fun currentDirectory(context: Context): File = File(context.filesDir, "datasets/current")
    }
}

/** Secure package installer for a `.omd.zip` selected through Storage Access Framework. */
object AndroidDatasetInstaller {
    /**
     * Install a user-selected portable package into app-private storage.
     *
     * Every ZIP entry is canonicalized and required to remain below the installation
     * root. This check is the Android package-import path traversal boundary.
     */
    fun install(context: Context, uri: Uri): File {
        val target = AndroidDataset.currentDirectory(context)
        if (target.exists()) {
            target.deleteRecursively()
        }
        target.mkdirs()
        val canonicalRoot = target.canonicalFile

        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw) { "Cannot open selected dataset" }
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = File(canonicalRoot, entry.name).canonicalFile
                    require(output.path.startsWith(canonicalRoot.path + File.separator)) {
                        "Unsafe ZIP entry: ${entry.name}"
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

        require(File(target, "metadata.json").isFile) { "Invalid package: metadata.json missing" }
        require(File(target, "georisk.sqlite").isFile) { "Invalid package: georisk.sqlite missing" }
        return target
    }
}
