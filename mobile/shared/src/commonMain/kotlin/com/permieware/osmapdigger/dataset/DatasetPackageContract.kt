package com.permieware.osmapdigger.dataset

import com.permieware.osmapdigger.domain.DatasetInfo
import com.permieware.osmapdigger.domain.GeoPoint
import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.error.operationalBoundary
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Compatibility-sensitive filenames/placeholders shared by Android and Desktop package loaders. */
object DatasetPackageLayout {
    const val METADATA_FILE = "metadata.json"
    const val DATABASE_FILE = "georisk.sqlite"
    const val STYLE_TEMPLATE_FILE = "style.template.json"
    const val PMTILES_URI_PLACEHOLDER = "{{PMTILES_URI}}"
}

@Serializable
data class DatasetPackageCenter(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

@Serializable
data class DatasetPackageArtifacts(
    val map: String? = null,
)

@Serializable
data class DatasetPackagePropertySearch(
    val site: String? = null,
    val terms: String? = null,
)

/** Platform-independent subset of metadata.json required by the runtime. */
@Serializable
data class DatasetPackageMetadata(
    val datasetId: String,
    val displayName: String,
    val countryCode: String? = null,
    val center: DatasetPackageCenter,
    val artifacts: DatasetPackageArtifacts,
    val propertySearch: DatasetPackagePropertySearch? = null,
) {
    init {
        require(datasetId.isNotBlank())
        require(displayName.isNotBlank())
        require(center.latitude.isFinite() && center.latitude in -90.0..90.0)
        require(center.longitude.isFinite() && center.longitude in -180.0..180.0)
        require(center.zoom.isFinite())
        require(artifacts.map == null || artifacts.map.isNotBlank())
    }

    fun toDatasetInfo(hasMap: Boolean): DatasetInfo =
        DatasetInfo(
            id = datasetId,
            displayName = displayName,
            countryCode = countryCode,
            center = GeoPoint(center.latitude, center.longitude),
            initialZoom = center.zoom,
            hasMap = hasMap,
            propertySearchSite = propertySearch?.site?.takeIf { it.isNotBlank() },
            propertySearchTerms = propertySearch?.terms?.takeIf { it.isNotBlank() } ?: "property",
        )
}

object DatasetPackageMetadataParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: String): DatasetPackageMetadata =
        operationalBoundary(
            kind = OperationalFailureKind.DATASET_INVALID,
            technicalContext = "Could not parse dataset metadata",
        ) {
            json.decodeFromString<DatasetPackageMetadata>(payload)
        }
}
