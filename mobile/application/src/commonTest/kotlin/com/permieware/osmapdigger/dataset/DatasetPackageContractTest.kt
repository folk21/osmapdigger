package com.permieware.osmapdigger.dataset

import com.permieware.osmapdigger.error.OperationalFailureException
import com.permieware.osmapdigger.error.OperationalFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatasetPackageContractTest {
    @Test
    fun metadataParserIgnoresBuilderFieldsAndProducesRuntimeDatasetInfo() {
        val metadata =
            DatasetPackageMetadataParser.decode(
                """
                {
                  "datasetId": "belarus",
                  "displayName": "Belarus",
                  "countryCode": "BY",
                  "center": {"latitude": 53.7, "longitude": 27.9, "zoom": 6.0},
                  "artifacts": {"database": "georisk.sqlite", "map": "belarus.pmtiles"},
                  "propertySearch": {"site": "kufar.by", "terms": "дом недвижимость"},
                  "builder": {"metricProfile": "core10"}
                }
                """.trimIndent(),
            )

        assertEquals("belarus.pmtiles", metadata.artifacts.map)
        assertEquals("BY", metadata.toDatasetInfo(hasMap = true).countryCode)
        assertEquals("дом недвижимость", metadata.toDatasetInfo(hasMap = true).propertySearchTerms)
    }

    @Test
    fun malformedMetadataIsTypedAsDatasetFailure() {
        val failure =
            assertFailsWith<OperationalFailureException> {
                DatasetPackageMetadataParser.decode("{not-json")
            }

        assertEquals(OperationalFailureKind.DATASET_INVALID, failure.failure.kind)
    }
}
