package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MiniStoryRecoveryTest {
    @Test fun detectsCharacterIssueAndKeepsRecoveryManual() {
        val status=JSONObject().put("state","failed").put("percent",87)
            .put("message","EP 2 ซีน 3: พบชื่อภาษาอังกฤษ 'John'")
        assertTrue(MiniStoryRecovery.characterNameIssue(status))
        assertTrue(MiniStoryRecovery.needsAttention(status,false))
        assertFalse(MiniStoryRecovery.needsAttention(status,true))
        assertEquals("ชื่อตัวละครไม่ตรงกับบท",MiniStoryRecovery.reason(status))
        assertEquals(87,MiniStoryProgress.percent(status))
    }
    @Test fun doesNotMislabelOtherErrorsAsCharacterIssues() {
        val status=JSONObject().put("state","failed").put("message","HTTP 429")
        assertFalse(MiniStoryRecovery.characterNameIssue(status))
        assertEquals("งานหยุดเพราะข้อผิดพลาด",MiniStoryRecovery.reason(status))
    }
}
