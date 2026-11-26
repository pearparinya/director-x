package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryClockTest {
    private fun scene(time: String, day: String = "1") = JSONObject()
        .put("scene", 1).put("story_day", day).put("story_time", time)

    @Test fun continuousShotsAndLaterEvents() {
        val rows = JSONArray().put(scene("16.00 afternoon")).put(scene("16:00 afternoon"))
            .put(scene("19.00 dusk")).put(scene("20.00 night"))
        StoryClock.validate(rows, null, 1)
        assertEquals("16.00 afternoon", rows.getJSONObject(1).getString("story_time"))
    }

    @Test fun earlierTimeAcrossBatchesNeedsStoryReason() {
        val earlier = scene("19.00 dusk")
        assertThrows(IllegalArgumentException::class.java) {
            StoryClock.validate(JSONArray().put(earlier), scene("20.00 night"), 1)
        }
        earlier.put("time_transition", "flashback")
        StoryClock.validate(JSONArray().put(earlier), scene("20.00 night"), 1)
        StoryClock.validate(JSONArray().put(scene("00.01 night", "2")), scene("23.59 night"), 1)
    }

    @Test fun invalidTimesAndLegacyReading() {
        for (time in listOf("24.00 night", "16.75 afternoon", "19.00", "night")) {
            assertThrows(IllegalArgumentException::class.java) {
                StoryClock.validate(JSONArray().put(scene(time)), null, 1)
            }
        }
        assertEquals("พลบค่ำ", SceneMoment.resolve(null, 1, scene("พลบค่ำ")).time)
        val morning = scene("06.30 dawn").put("start_state", "เช้าวันรุ่งขึ้น")
        assertEquals("06.30 dawn", SceneMoment.resolve(null, 1, morning).time)
    }
}
