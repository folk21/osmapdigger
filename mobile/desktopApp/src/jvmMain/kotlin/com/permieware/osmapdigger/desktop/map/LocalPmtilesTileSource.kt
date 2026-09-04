package com.permieware.osmapdigger.desktop.map

import ch.poole.geo.pmtiles.Reader
import java.io.Closeable
import java.nio.file.Path

/**
 * Random-access adapter over one local PMTiles archive used by the Intel macOS web renderer.
 *
 * The web renderer supports only vector MVT tiles with no compression or gzip compression because
 * those are the response encodings the loopback HTTP boundary can expose without transforming tile data.
 */
internal class LocalPmtilesTileSource(pmtilesPath: Path) : Closeable {
    private val reader = Reader(pmtilesPath.toFile())

    val minZoom: Int = reader.minZoom.toInt() and 0xff
    val maxZoom: Int = reader.maxZoom.toInt() and 0xff
    val compressionCode: Int = reader.tileCompression.toInt()
    val isGzipCompressed: Boolean
        get() = compressionCode == COMPRESSION_GZIP

    init {
        require(reader.tileType.toInt() == TILE_TYPE_MVT) {
            "Intel macOS web map currently requires MVT PMTiles; tile type=${reader.tileType.toInt()}"
        }
        require(compressionCode in setOf(COMPRESSION_NONE, COMPRESSION_GZIP)) {
            "Unsupported PMTiles tile compression: $compressionCode"
        }
    }

    fun getTile(zoom: Int, x: Int, y: Int): ByteArray? = reader.getTile(zoom, x, y)

    override fun close() {
        reader.close()
    }

    private companion object {
        const val TILE_TYPE_MVT = 1
        const val COMPRESSION_NONE = 1
        const val COMPRESSION_GZIP = 2
    }
}
