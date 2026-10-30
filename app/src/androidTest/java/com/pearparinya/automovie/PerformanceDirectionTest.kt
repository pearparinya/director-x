package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformanceDirectionTest {
    private fun performance(emotion: String,voice: String)=JSONObject().apply {
        PerformanceDirection.fields.forEach { put(it,"controlled playable choice") }
        put("emotion",emotion); put("voice",voice); put("thai_direction","กลั้นความเจ็บไว้ สบตาอีกฝ่าย มือเกร็งแล้วค่อยคลาย")
        put("emotional_arc","composure -> hurt -> reclaimed dignity")
    }
    @Test fun silentVoiceNormalizationPreservesDialogueSpeakerAndMakesOffscreenAndNarratedScenesSilent() {
        val speaker=JSONObject().put("name","กวิน").put("master","GAWIN_MASTER.png").put("performance",performance("fear","low measured voice"))
        val listener=JSONObject().put("name","อัคนี").put("master","SUPPORT_0000008B_MASTER.png").put("performance",performance("worry","whispers softly"))
        val scene=JSONObject().put("characters",JSONArray().put(speaker).put(listener)).put("speaker","กวิน").put("dialogue","ฟังผมก่อน")
        PerformanceDirection.normalizeSilentVoices(scene)
        assertEquals("low measured voice",speaker.getJSONObject("performance").getString("voice"))
        assertEquals("silent",listener.getJSONObject("performance").getString("voice"))
        PerformanceDirection.validate(scene)
        scene.put("speaker","นอกฉาก").put("dialogue","รอผมก่อน")
        PerformanceDirection.normalizeSilentVoices(scene)
        assertEquals("silent",speaker.getJSONObject("performance").getString("voice"))
        PerformanceDirection.validate(scene)
        scene.put("speaker","").put("dialogue","").put("narration","เรื่องราวเริ่มต้น")
        listener.getJSONObject("performance").put("voice","quiet breath")
        PerformanceDirection.normalizeSilentVoices(scene)
        PerformanceDirection.validate(scene)
        scene.put("speaker","กวิน").put("dialogue","ฟังผมก่อน").put("narration","")
        assertTrue(runCatching { PerformanceDirection.validate(scene) }.isFailure)
    }
    @Test fun everyCharacterHasDistinctPerformanceAndOnlyTheSpeakerGetsAVoice() {
        val man=JSONObject().put("name","กวิน").put("master","GAWIN_MASTER.png").put("performance",performance("fear beneath reassurance","low measured Thai voice"))
        val wife=JSONObject().put("name","รินรดา").put("master","RINLADA_MASTER.png").put("performance",performance("hurt beneath dignity","silent"))
        val scene=JSONObject().put("characters",JSONArray().put(man).put(wife)).put("speaker","กวิน").put("dialogue","ฟังผมก่อน")
        PerformanceDirection.validate(scene)
        assertTrue(PerformanceDirection.continuity(scene).contains("รินรดา: arc=composure -> hurt -> reclaimed dignity"))
        val text=PerformanceDirection.scene(scene)
        assertTrue(text.contains("กวิน PERFORMANCE: emotion: fear beneath reassurance"))
        assertTrue(text.contains("รินรดา PERFORMANCE: emotion: hurt beneath dignity"))
        assertTrue(PerformanceDirection.script(scene).contains("กำกับ รินรดา:"))
        wife.getJSONObject("performance").put("voice","angry whisper")
        assertTrue(runCatching { PerformanceDirection.validate(scene) }.isFailure)
        wife.getJSONObject("performance").put("voice","silent")
        scene.put("dialogue","").put("speaker","")
        assertTrue(runCatching { PerformanceDirection.validate(scene) }.isFailure)
        man.getJSONObject("performance").put("voice","silent")
        PerformanceDirection.validate(scene)
        wife.getJSONObject("performance").remove("subtext")
        assertTrue(runCatching { PerformanceDirection.validate(scene) }.isFailure)
    }
    @Test fun schemaAndVerifiedVideoCarryActorDirectionsWithoutAddingSpeech() {
        val schema=GeminiStoryEngine.batchSchema().getJSONObject("properties").getJSONObject("scenes").getJSONObject("items")
            .getJSONObject("properties").getJSONObject("characters").getJSONObject("items").getJSONObject("properties").getJSONObject("performance")
        assertEquals(PerformanceDirection.fields.toSet(),schema.getJSONArray("required").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
        val spec=FlowSceneSpec(listOf(FlowPerson("กวิน","short dark hair","left","shirt","fear beneath reassurance; voice: low measured Thai voice"),
            FlowPerson("รินรดา","long black hair","right","blouse","hurt beneath dignity; voice: silent")),"กวิน","ฟังผมก่อน","","place the envelope","room","envelope in hand","envelope on table",8,"9:16")
        val prompt=FlowScenePrompt.build(spec)
        assertTrue(prompt.contains("INDIVIDUAL PERFORMANCE — กวิน: fear beneath reassurance"))
        assertTrue(prompt.contains("INDIVIDUAL PERFORMANCE — รินรดา: hurt beneath dignity"))
        assertTrue(prompt.contains("0-2s receive the cue")); assertTrue(prompt.contains("6-8s small silent response"))
        assertTrue(prompt.contains("SILENT LISTENER")); assertTrue(prompt.contains("no shared or overlapping delivery"))
        assertEquals(1,Regex("ฟังผมก่อน").findAll(prompt).count())
        assertFalse(prompt.contains("angry whisper"))
    }
}
