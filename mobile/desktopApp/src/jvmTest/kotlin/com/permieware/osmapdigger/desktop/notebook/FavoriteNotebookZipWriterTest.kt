package com.permieware.osmapdigger.desktop.notebook

import com.permieware.osmapdigger.notebook.FavoriteNotebookExportBundle
import com.permieware.osmapdigger.notebook.FavoriteNotebookExportFile
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FavoriteNotebookZipWriterTest {
    @Test
    fun archiveBytesAndEntryOrderAreDeterministic() {
        val bundle = FavoriteNotebookExportBundle(
            fileName = "favorites.zip",
            files = listOf(
                FavoriteNotebookExportFile("favorites.json", "{\"version\":1}".encodeToByteArray()),
                FavoriteNotebookExportFile("favorites.md", "# Favorites\n".encodeToByteArray()),
            ),
        )

        val first = FavoriteNotebookZipWriter.encode(bundle)
        val second = FavoriteNotebookZipWriter.encode(bundle)
        assertContentEquals(first, second)

        val entries = mutableListOf<Pair<String, String>>()
        ZipInputStream(ByteArrayInputStream(first)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name to zip.readBytes().decodeToString()
                entry = zip.nextEntry
            }
        }
        assertEquals(
            listOf(
                "favorites.json" to "{\"version\":1}",
                "favorites.md" to "# Favorites\n",
            ),
            entries,
        )
    }
}
