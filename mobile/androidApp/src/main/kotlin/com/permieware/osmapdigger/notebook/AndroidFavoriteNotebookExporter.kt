package com.permieware.osmapdigger.notebook

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android share-sheet adapter for the portable Favorites archive. */
class AndroidFavoriteNotebookExporter(
    private val context: Context,
) : FavoriteNotebookExporter {
    override suspend fun export(bundle: FavoriteNotebookExportBundle): FavoriteNotebookExportOutcome {
        val archive = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            File(directory, bundle.fileName).also { file ->
                file.writeBytes(FavoriteNotebookZipWriter.encode(bundle))
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            archive,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Favorites"))
        return FavoriteNotebookExportOutcome.COMPLETED
    }
}

/** Keep Android archive bytes aligned with the Desktop deterministic ZIP contract. */
private object FavoriteNotebookZipWriter {
    private const val FIXED_ZIP_TIME_EPOCH_MS = 315_532_800_000L // 1980-01-01T00:00:00Z

    fun encode(bundle: FavoriteNotebookExportBundle): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            bundle.files.forEach { file ->
                val crc = CRC32().apply { update(file.content) }.value
                val entry = ZipEntry(file.name).apply {
                    method = ZipEntry.STORED
                    size = file.content.size.toLong()
                    compressedSize = file.content.size.toLong()
                    this.crc = crc
                    time = FIXED_ZIP_TIME_EPOCH_MS
                }
                zip.putNextEntry(entry)
                zip.write(file.content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
