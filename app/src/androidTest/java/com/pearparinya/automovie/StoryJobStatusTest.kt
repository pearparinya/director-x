package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryJobStatusTest {
    @Test fun diagnosticHistorySurvivesPauseAndNeverStoresPayload() {
        val live=StoryJobStatus(null,0,{0L},{1000L})
        live.setTask("กำลังวางบทบาท เลือก MASTER และตั้งชื่อนักแสดง")
        live.recordFailure(StoryProvider.GROQ,"HTTP 400 • invalid_schema")
        live.recordFailure(StoryProvider.CEREBRAS,"secret-key and private story")
        val paused=live.update("paused",0,"หยุดแล้ว","openrouter","paused")
        val text=StoryStatusText.format(paused,false,1000L)
        assertTrue(text.contains("เลือกตัวละคร")); assertTrue(text.contains("Groq: HTTP 400"))
        assertFalse(text.contains("Cerebras")); assertFalse(text.contains("secret-key"))
        val resumed=StoryJobStatus(paused,0,{0L},{2000L}).update("running",0,"ต่อ","groq","requesting")
        assertEquals(2,resumed.getJSONArray("failures").length())
    }
    @Test fun oldProviderStatusIsHiddenWithoutErasingSavedDiagnostics() {
        val old=JSONObject().put("state","paused").put("provider","cerebras")
            .put("message","Cerebras HTTP 401").put("percent",8)
            .put("failures",org.json.JSONArray().put(JSONObject().put("provider","Cerebras").put("reason","HTTP 401")))
        val text=StoryStatusText.format(old,false)
        assertFalse(text.contains("cerebras",ignoreCase=true)); assertTrue(text.contains("8%"))
        assertEquals(1,old.getJSONArray("failures").length())
    }
    @Test fun heartbeatAdvancesTimeWithoutInventingProgressAndResumeExcludesIdleTime() {
        var clock=0L; var wall=1_000_000L
        val old=JSONObject().put("elapsed_ms",10_000L).put("started_at",10L)
        val live=StoryJobStatus(old,25,{clock},{wall})
        val first=live.update("running",25,"รอคำตอบ","groq","requesting")
        clock+=10_000; wall+=10_000
        val pulse=live.heartbeat()
        assertEquals(25,pulse.getInt("percent")); assertEquals(20_000L,pulse.getLong("elapsed_ms"))
        assertEquals(first.getLong("updated"),pulse.getLong("updated"))
        assertEquals(wall,pulse.getLong("heartbeat_at")); assertEquals(10_000L,pulse.getLong("phase_elapsed_ms"))
        val saved=live.update("running",30,"บันทึกแล้ว","groq","saved")
        assertEquals(30,saved.getInt("percent")); assertEquals(30,live.update("running",0,"เริ่มช่วงใหม่","groq","requesting").getInt("percent"))
        val failed=live.update("failed",30,"HTTP 400","groq","failed")
        clock+=60_000; wall+=60_000
        assertEquals(failed.toString(),live.heartbeat().toString())
        val resumed=StoryJobStatus(failed,30,{clock},{wall}).update("running",30,"ทำต่อ","gemini","requesting")
        assertEquals(20_000L,resumed.getLong("elapsed_ms"))
        assertEquals(10L,resumed.getLong("started_at"))
    }
    @Test fun failurePauseAndStaleStatusAreNeverPresentedAsActiveProgress() {
        val live=StoryJobStatus(null,75,{0L},{100_000L})
        val status=live.update("running",75,"รอคำตอบ","groq","requesting")
        assertTrue(StoryStatusText.format(status,true,100_000).contains("ทำต่ออัตโนมัติ"))
        assertTrue(StoryStatusText.format(status,false,100_000).contains("งานถูกปิด"))
        assertTrue(StoryStatusText.format(status,true,120_000).contains("ขาดการอัปเดต"))
        val failed=live.update("failed",75,"HTTP 400","groq","failed")
        val text=StoryStatusText.format(failed,false,120_000)
        assertTrue(text.contains("หยุดเพราะข้อผิดพลาด")); assertFalse(text.contains("ทำต่ออัตโนมัติ"))
        assertTrue(text.contains("75%")); assertTrue(text.contains("HTTP 400"))
        assertTrue(StoryStatusText.format(live.update("paused",75,"หยุดแล้ว","groq","paused"),false).contains("กดทำต่อได้"))
        val complete=live.update("complete",99,"บทพร้อม","gemini","complete")
        assertEquals(100,complete.getInt("percent")); assertTrue(StoryStatusText.format(complete,false).contains("เสร็จสมบูรณ์"))
        assertEquals("01:01:01",StoryStatusText.duration(3_661_000))
    }
}
