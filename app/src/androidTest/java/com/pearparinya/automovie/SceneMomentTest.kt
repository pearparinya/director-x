package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneMomentTest {
    private fun resolve(firstTime: String, nextStart: String): SceneMoment {
        fun scene(number: Int) = JSONObject().put("scene", number).put("story_day", "1").put("continuity_group", "river")
        val first = scene(1).put("story_time", firstTime).put("end_state", "looking at camera")
        val next = scene(2).put("start_state", nextStart)
        val episode = JSONObject().put("ep", 1).put("scenes", JSONArray().put(first).put(next))
        return SceneMoment.resolve(JSONObject().put("episodes", JSONArray().put(episode)), 1, next)
    }

    @Test fun continuousRiverShot() {
        assertEquals(SceneMoment("พลบค่ำ", "looking at camera"), resolve("พลบค่ำ", ""))
    }

    @Test fun nextMorning() {
        assertEquals(SceneMoment("morning"), resolve("พลบค่ำ", "เช้าวันรุ่งขึ้น"))
    }

    @Test fun legacyUnknownTime() {
        assertEquals(SceneMoment.unknown, resolve("", "").time)
    }
}
