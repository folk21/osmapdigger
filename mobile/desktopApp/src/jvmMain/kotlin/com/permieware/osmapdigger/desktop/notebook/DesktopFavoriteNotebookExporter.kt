package com.permieware.osmapdigger.desktop.notebook

import com.permieware.osmapdigger.notebook.FavoriteNotebookExportBundle
import com.permieware.osmapdigger.notebook.FavoriteNotebookExportOutcome
import com.permieware.osmapdigger.notebook.FavoriteNotebookExporter
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Desktop save/reveal adapter for the portable deterministic Favorites archive. */
class DesktopFavoriteNotebookExporter : FavoriteNotebookExporter {
    override suspend fun export(bundle: FavoriteNotebookExportBundle): FavoriteNotebookExportOutcome {
        val selected = chooseDestination(bundle.fileName) ?: return FavoriteNotebookExportOutcome.CANCELLED
        val destination = ensureZipExtension(selected)

        withContext(Dispatchers.IO) {
            FavoriteNotebookZipWriter.write(bundle, destination)
        }
        reveal(destination)
        return FavoriteNotebookExportOutcome.COMPLETED
    }

    private fun chooseDestination(defaultFileName: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export Favorites"
            selectedFile = File(defaultFileName)
            fileFilter = FileNameExtensionFilter("ZIP archives (*.zip)", "zip")
        }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun ensureZipExtension(file: File): File =
        if (file.name.endsWith(".zip", ignoreCase = true)) file else File(file.parentFile, "${file.name}.zip")

    private fun reveal(file: File) {
        runCatching {
            val desktop = Desktop.getDesktop()
            when {
                desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR) -> desktop.browseFileDirectory(file)
                desktop.isSupported(Desktop.Action.OPEN) -> desktop.open(file.parentFile)
            }
        }
    }
}

/** ZIP entry order, timestamps, storage method, and CRC values are fixed for reproducible exports. */
internal object FavoriteNotebookZipWriter {
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

    fun write(bundle: FavoriteNotebookExportBundle, destination: File) {
        val parent = destination.absoluteFile.parentFile ?: error("Export destination has no parent directory")
        Files.createDirectories(parent.toPath())
        val temporary = Files.createTempFile(parent.toPath(), ".osmapdigger-favorites-", ".tmp")
        try {
            Files.write(temporary, encode(bundle))
            try {
                Files.move(
                    temporary,
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
