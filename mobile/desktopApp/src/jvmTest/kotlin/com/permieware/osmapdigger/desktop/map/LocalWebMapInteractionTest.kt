package com.permieware.osmapdigger.desktop.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalWebMapInteractionTest {
    @Test
    fun decodesStableSettlementIdFromInteractionPayload() {
        assertEquals(
            "relation/12345",
            LocalWebMapInteraction.decodeSettlement("""{"id":"relation/12345"}"""),
        )
    }

    @Test
    fun decodesValidatedMapLocationInteraction() {
        val location = LocalWebMapInteraction.decodeMapLocation("""{"latitude":54.6872,"longitude":25.2797}""")
        assertEquals(54.6872, location?.latitude)
        assertEquals(25.2797, location?.longitude)
    }

    @Test
    fun rejectsInvalidMapLocationInteraction() {
        assertNull(LocalWebMapInteraction.decodeMapLocation("{}"))
        assertNull(LocalWebMapInteraction.decodeMapLocation("""{"latitude":91,"longitude":10}"""))
        assertNull(LocalWebMapInteraction.decodeMapLocation("""{"latitude":10,"longitude":181}"""))
        assertNull(LocalWebMapInteraction.decodeMapLocation("not-json"))
    }

    @Test
    fun usesPracticalPointerToleranceForSettlementMarkers() {
        assertTrue(LocalWebMapInteraction.SETTLEMENT_HIT_RADIUS_PX >= 8)
    }

    @Test
    fun rejectsMissingBlankAndMalformedSettlementIds() {
        assertNull(LocalWebMapInteraction.decodeSettlement("{}"))
        assertNull(LocalWebMapInteraction.decodeSettlement("""{"id":"   "}"""))
        assertNull(LocalWebMapInteraction.decodeSettlement("not-json"))
    }
}
