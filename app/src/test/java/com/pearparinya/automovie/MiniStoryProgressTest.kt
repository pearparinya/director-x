package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MiniStoryProgressTest {
    @Test fun validatedPercentIsClampedAndNotTimeBased() {
        val job=JSONObject().put("percent",42).put("elapsed_ms",900000L)
        assertEquals(42,MiniStoryProgress.percent(job))
        assertEquals(100,MiniStoryProgress.percent(JSONObject().put("percent",999)))
        assertEquals(0,MiniStoryProgress.percent(JSONObject().put("percent",-2)))
    }
    @Test fun providerAndStageFollowLiveStatus() {
        val job=JSONObject().put("provider","groq").put("state","running").put("phase","requesting")
        assertEquals("Groq",MiniStoryProgress.provider(job))
        assertEquals("กำลังรอคำตอบ AI",MiniStoryProgress.stage(job,true))
        assertEquals("รอทำงานต่อ",MiniStoryProgress.stage(job,false))
        job.put("phase","retry_wait")
        assertTrue(MiniStoryProgress.waiting(job))
        assertEquals("กำลังรอ AI",MiniStoryProgress.stage(job,true))
        job.put("state","complete")
        assertEquals("เขียนบทสำเร็จ",MiniStoryProgress.stage(job,false))
    }
    @Test fun hidesRestrictedProviderName() {
        assertEquals("API อัตโนมัติ",MiniStoryProgress.provider(JSONObject().put("provider","cerebras")))
    }

    @Test fun progressSnapshotTracksProviderSwitchAndTerminalState() {
        val job=JSONObject().put("state","running").put("phase","requesting")
            .put("hybrid",true).put("provider","groq").put("percent",42).put("elapsed_ms",65000L)
        assertEquals("AI Hybrid • Groq",MiniStoryProgress.provider(job))
        assertEquals(42,MiniStoryProgress.percent(job))
        assertEquals(StoryStatusText.duration(65000L),MiniStoryProgress.elapsed(job))
        job.put("provider","gemini").put("phase","retry_wait")
        assertEquals("AI Hybrid • Gemini",MiniStoryProgress.provider(job))
        assertTrue(MiniStoryProgress.waiting(job))
        assertEquals(42,MiniStoryProgress.percent(job))
        job.put("state","paused")
        assertFalse(MiniStoryProgress.waiting(job))
        assertEquals("หยุดชั่วคราว",MiniStoryProgress.stage(job,false))
        job.put("state","failed")
        assertFalse(MiniStoryProgress.waiting(job))
        assertEquals("เกิดข้อผิดพลาด",MiniStoryProgress.stage(job,false))
        job.put("state","complete").put("percent",100)
        assertFalse(MiniStoryProgress.waiting(job))
        assertEquals(100,MiniStoryProgress.percent(job))
        assertEquals("เขียนบทสำเร็จ",MiniStoryProgress.stage(job,false))
    }
    @Test fun missingOrNegativeElapsedIsSafe() {
        assertEquals(StoryStatusText.duration(0L),MiniStoryProgress.elapsed(null))
        assertEquals(StoryStatusText.duration(0L),MiniStoryProgress.elapsed(JSONObject().put("elapsed_ms",-100L)))
        assertFalse(MiniStoryProgress.waiting(null))
    }
}
