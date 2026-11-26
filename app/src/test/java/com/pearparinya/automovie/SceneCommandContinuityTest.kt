package com.pearparinya.automovie

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SceneCommandContinuityTest {
    private fun verify(time: String, start: String, expected: String, ai: Boolean) {
        fun scene(n: Int) = JSONObject().put("scene", n).put("ep", 1).put("story_day", "1")
            .put("continuity_group", "river").put("location", "river").put("characters", JSONArray())
            .put("start_state", "standing still").put("end_state", "looking at the camera")
            .put("action", "looks down").put("flow_action", "looks down")
        val first = scene(1)
        if (time.isNotEmpty()) first.put("story_time", time)
        val second = scene(2).put("start_state", start)
        val ep = JSONObject().put("ep", 1).put("title", "River").put("scenes", JSONArray().put(first).put(second))
        val story = JSONObject().put("channel", "Drama").put("title", "River").put("tagline", "River").put("episodes", JSONArray().put(ep))
        val original = story.toString()
        val image = SimpleProductionCommands.imageTasks(story, 1).last().command
        val output = if (ai) SimpleProductionCommands.withCameraNote(image, "Close shot.") else image
        val flow = SimpleProductionCommands.videoTasks(story, 1).last().command
        assertTrue(output, output.contains("Story time: " + expected))
        assertTrue(flow, flow.contains("Story time: " + expected))
        assertEquals(start == "looks down", output.contains("Match the preceding end pose"))
        assertEquals(original, story.toString())
    }

    @Test fun kotlinContinuous() = verify("พลบค่ำ", "looks down", "พลบค่ำ", false)
    @Test fun aiContinuous() = verify("พลบค่ำ", "looks down", "พลบค่ำ", true)
    @Test fun kotlinMorning() = verify("พลบค่ำ", "เช้าวันรุ่งขึ้น", "morning", false)
    @Test fun aiMorning() = verify("พลบค่ำ", "เช้าวันรุ่งขึ้น", "morning", true)
    @Test fun kotlinLegacy() = verify("", "looks down", SceneMoment.unknown, false)
    @Test fun aiLegacy() = verify("", "looks down", SceneMoment.unknown, true)
}
