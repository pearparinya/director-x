package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SceneRealismTest {
    @Test fun daylightKeepsItsTimeAndFirstFrameBeforeSwitchOn() {
        val scene = JSONObject().put("story_time","กลางวัน").put("start_state","ถือไฟฉายที่ปิดอยู่")
            .put("action","เปิดไฟส่องใต้บันได")
        val command = SceneRealism.frame(scene)
        assertTrue(command.contains("กลางวัน"))
        assertTrue(command.contains("ถือไฟฉายที่ปิดอยู่"))
        assertTrue(command.contains("aim at the stated target"))
    }
    @Test fun scenesWithoutFlashlightsDoNotIntroduceOne() {
        assertFalse(SceneRealism.frame(JSONObject().put("action","เดินในสวน")).contains("beam"))
        assertTrue(GenreStoryCraft.direction("cinema_horror").contains("Daylight horror"))
        assertFalse(GenreStoryCraft.direction("cinema_legend").contains("Daylight horror"))
    }
}
