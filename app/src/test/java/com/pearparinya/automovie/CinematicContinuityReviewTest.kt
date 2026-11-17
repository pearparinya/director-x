package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicContinuityReviewTest {
    private fun story(vararg scenes: JSONObject): JSONObject =
        JSONObject().put("episodes", JSONArray().put(JSONObject().put("scenes", JSONArray().apply {
            scenes.forEach { put(it) }
        })))

    private fun scene(number: Int, start: String, end: String): JSONObject =
        JSONObject().put("scene", number).put("continuity_group", "same-shot")
            .put("story_day", "1").put("location", "room")
            .put("start_state", start).put("end_state", end)

    @Test fun detectsExplicitStateMismatchBetweenScenes() {
        val findings = ContinuityReview.review(story(
            scene(1, "door closed", "door open"),
            scene(2, "door closed", "person enters")
        ))
        assertTrue(findings.any { it.contains("สถานะเริ่มต้น") && it.contains("ซีน 2") })
    }

    @Test fun matchingStatesDoNotRaiseStateMismatch() {
        val findings = ContinuityReview.review(story(
            scene(1, "door closed", "door open"),
            scene(2, "door open", "person enters")
        ))
        assertTrue(findings.none { it.contains("ไม่ตรงกับสถานะสิ้นสุด") })
    }
    @Test fun detectsLocationChangeWithinSameContinuityGroup() {
        val second = scene(2, "door open", "person enters").put("location", "garden")
        val findings = ContinuityReview.review(story(
            scene(1, "door closed", "door open"),
            second
        ))
        assertTrue(findings.any { it.contains("สถานที่ของกลุ่มต่อเนื่อง") && it.contains("ซีน 2") })
    }

    @Test fun caseOnlyDifferencesDoNotTriggerStateMismatch() {
        val findings = ContinuityReview.review(story(
            scene(1, "door closed", "Door Open"),
            scene(2, "door open", "person enters")
        ))
        assertTrue(findings.none { it.contains("ไม่ตรงกับสถานะสิ้นสุด") })
    }

}
