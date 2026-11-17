package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicPropCodecTest {
    @Test fun emptyAndLegacyInventoryDecodeToEmpty() {
        assertTrue(CinematicPropCodec.decode(null).isEmpty())
        assertTrue(CinematicPropCodec.decode("").isEmpty())
        assertTrue(CinematicPropCodec.decode("[]").isEmpty())
    }

    @Test fun explicitInventoryRoundTripsWithoutInventingCounts() {
        val props = listOf(
            CinematicProp("cup", true, true, "left", "left", 2, 2),
            CinematicProp("background", true, false, firstFrameCount = null)
        )
        assertEquals(props, CinematicPropCodec.decode(CinematicPropCodec.encode(props)))
    }

    @Test fun missingOptionalFieldsKeepLegacyDefaults() {
        val raw = """[{"id":"table","presentInFirstFrame":true,"requiredForAction":false}]"""
        assertEquals(listOf(CinematicProp("table", true, false)), CinematicPropCodec.decode(raw))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidInventoryCannotBeSaved() {
        CinematicPropCodec.encode(listOf(CinematicProp("missing", false, true)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidInventoryCannotBeLoaded() {
        CinematicPropCodec.decode("""[{"id":"<bad>","presentInFirstFrame":true,"requiredForAction":false}]""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedInventoryCannotBeLoaded() {
        val raw = "[" + (1..51).joinToString(",") { """{"id":"p$it","presentInFirstFrame":true,"requiredForAction":false}""" } + "]"
        CinematicPropCodec.decode(raw)
    }
    @Test fun sceneJsonInventoryReachesProductionFlowSpec() {
        val scene = org.json.JSONObject().apply {
            put("cinematic_props", org.json.JSONArray(CinematicPropCodec.encode(
                listOf(CinematicProp("cup", true, true, "left", "left", 1, 1))
            )))
        }
        val spec = ProductionCommandCheck.spec(scene)
        assertEquals("cup", spec.cinematicProps.single().id)
    }

}
