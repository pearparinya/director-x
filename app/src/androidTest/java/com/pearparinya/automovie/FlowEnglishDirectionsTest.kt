package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowEnglishDirectionsTest {
    private fun scene()=FlowSceneSpec(listOf(FlowPerson("นายเปีย","adult","กลาง","shirt")),"","","เกิดน้ำท่วมในชุมชน", "นายเปีย ถือถุงเสบียง", "ริมคลอง", "ยืน", "ยืน", 8, "9:16", "ลมพัด")
    private fun result()=JSONObject().put("action","Character 1 holds a supply bag").put("positions",JSONArray().put("center")).put("sound","wind blows").put("clothing",JSONArray().put("shirt"))
    @Test fun translationPreservesOriginalNarrationAndDoesNotMutateScene() {
        val original=scene(); val translated=FlowEnglishDirections.apply(original,result())
        assertEquals("เกิดน้ำท่วมในชุมชน",translated.narration)
        assertEquals("นายเปีย ถือถุงเสบียง",original.action)
        val command=ShortFlowPrompt.build(translated,"Locked camera")
        assertTrue(command.contains("เกิดน้ำท่วมในชุมชน")); assertTrue(command.contains("Character 1 holds"))
        assertFalse(FlowEnglishDirections.prompt(original).contains(original.narration))
    }
    @Test fun pointsToActualUntranslatedFields() {
        val original=scene().copy(action="นายเปีย holds a bag",sound="wind")
        assertEquals(listOf("ตำแหน่งตัวละคร"),ShortFlowPrompt.untranslatedFields(original,"Locked camera"))
    }
    @Test fun rejectsMissingPositionsThaiAndLostSound() {
        val original=scene()
        assertTrue(runCatching { FlowEnglishDirections.apply(original,result().put("positions",JSONArray())) }.isFailure)
        assertTrue(runCatching { FlowEnglishDirections.apply(original,result().put("action","นายเปีย เดิน")) }.isFailure)
        assertTrue(runCatching { FlowEnglishDirections.apply(original,result().put("sound","")) }.isFailure)
        assertTrue(runCatching { FlowEnglishDirections.apply(original,result().put("action","Character 2 walks")) }.isFailure)
    }
}
