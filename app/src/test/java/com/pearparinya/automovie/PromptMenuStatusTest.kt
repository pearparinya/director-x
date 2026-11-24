package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptMenuStatusTest {
    @Test fun imageProgressSeparatesUnattemptedAndFailedItems() {
        assertEquals("คำสั่งสร้างภาพพร้อมใช้ 6/10 รายการ · รอดำเนินการ 3 รายการ · ต้องลองใหม่ 1 รายการ",
            PromptMenuStatus.image(6, 10, false, 1))
        assertEquals("ยังไม่ได้ปรับคำสั่งสร้างภาพ · 0/10 รายการ", PromptMenuStatus.image(0,10,false,0))
    }

    @Test fun flowReportsRunningAndReadyCountsFromScenes() {
        assertEquals("กำลังปรับคำสั่ง Flow · เสร็จแล้ว 3/8 ฉาก", PromptMenuStatus.flow(3,8,true,0))
        assertEquals("✅ คำสั่ง Flow พร้อมใช้ 8/8 ฉาก", PromptMenuStatus.flow(8,8,false,0))
    }
}
