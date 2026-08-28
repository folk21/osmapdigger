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
            decodeSettlementInteraction("""{"id":"relation/12345"}"""),
        )
    }


    @Test
    fun decodesValidatedMapLocationInteraction() {
        val location = decodeMapLocationInteraction("""{"latitude":54.6872,"longitude":25.2797}""")
        assertEquals(54.6872, location?.latitude)
        assertEquals(25.2797, location?.longitude)
    }

    @Test
    fun rejectsInvalidMapLocationInteraction() {
        assertNull(decodeMapLocationInteraction("{}"))
        assertNull(decodeMapLocationInteraction("""{"latitude":91,"longitude":10}"""))
        assertNull(decodeMapLocationInteraction("""{"latitude":10,"longitude":181}"""))
        assertNull(decodeMapLocationInteraction("not-json"))
    }

    @Test
    fun usesPracticalPointerToleranceForSettlementMarkers() {
        assertTrue(LocalWebMapServer.SETTLEMENT_INTERACTION_HIT_RADIUS_PX >= 8)
    }

    @Test
    fun rejectsMissingBlankAndMalformedSettlementIds() {
        assertNull(decodeSettlementInteraction("{}"))
        assertNull(decodeSettlementInteraction("""{"id":"   "}"""))
        assertNull(decodeSettlementInteraction("not-json"))
    }
}
