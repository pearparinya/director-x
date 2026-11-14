package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuityReviewTest {
    @Test fun flagsChangedDayWithinSameGroupAcrossEpisodes() {
        fun scene(day: String) = JSONObject().put("scene", 1).put("continuity_group", "G1")
            .put("story_day", day).put("start_state", "standing").put("end_state", "sitting")
        val story = JSONObject().put("episodes", JSONArray()
            .put(JSONObject().put("scenes", JSONArray().put(scene("DAY01"))))
            .put(JSONObject().put("scenes", JSONArray().put(scene("DAY02")))))
        assertTrue(ContinuityReview.review(story).any { it.contains("DAY01 / DAY02") })
    }
}
