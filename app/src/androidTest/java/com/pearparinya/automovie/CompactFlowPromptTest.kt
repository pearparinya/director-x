package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactFlowPromptTest {
    private fun scene(dialogue: String="",narration: String="",speaker: String="",offscreen: List<String> = emptyList()) =
        FlowSceneSpec(listOf(FlowPerson("ปิ่นมณี","adult woman","left","navy suit")),speaker,dialogue,narration,
            "ปิ่นมณี opens the report","office","holding report","turning page",8,"9:16","music",offscreen)
    @Test fun narrationAndSilentOptionsKeepActionAndSeparateSpeech() {
        val s=scene(narration="ความผิดปกติเริ่มต้นจากตัวเลข")
        val audio=CompactFlowPrompt.build(s,"Slow pan.",false)
        assertTrue(audio.contains("ความผิดปกติเริ่มต้นจากตัวเลข"))
        assertTrue(audio.contains("off-screen")); assertTrue(audio.contains("first frame"))
        assertFalse(audio.contains("<scene")); assertFalse(audio.contains("music"))
        val quiet=CompactFlowPrompt.build(s,"Slow pan.",true)
        assertTrue(quiet.contains("ปิ่นมณี opens the report"))
        assertFalse(quiet.contains("ความผิดปกติเริ่มต้นจากตัวเลข"))
        assertTrue(quiet.contains("No speech")); assertTrue(CompactFlowPrompt.voiceText(s).contains("ความผิดปกติเริ่มต้นจากตัวเลข"))
    }
    @Test fun dialogueKeepsExactSpeakerAndWords() {
        val s=scene(dialogue="เธอรู้ใช่ไหม",speaker="ปิ่นมณี")
        val text=CompactFlowPrompt.build(s,"Locked camera.",false)
        assertTrue(text.contains("Only ปิ่นมณี")); assertTrue(text.contains("เธอรู้ใช่ไหม"))
        assertEquals("ปิ่นมณี: เธอรู้ใช่ไหม",CompactFlowPrompt.voiceText(s))
    }
}
