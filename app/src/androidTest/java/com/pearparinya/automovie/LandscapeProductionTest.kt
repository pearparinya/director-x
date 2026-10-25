package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LandscapeProductionTest {
    @Test fun portraitCopyReframesPromptsAndInvalidatesApprovalWithoutChangingSourceOrWardrobe() {
        val person=JSONObject().put("name","รินรดา").put("master","RINLADA_MASTER.png")
            .put("outfit","Top: white silk blouse with vertical stripes; Bottom: black skirt; Shoes: black heels; Accessories: one silver watch on left wrist")
        val scene=JSONObject().put("scene",1).put("characters",JSONArray().put(person)).put("story_day","DAY01")
            .put("dialogue","เรื่องนี้จริงหรือคะ").put("speaker","รินรดา").put("image_prompt","vertical 9:16 image, 1080 x 1920, living room")
            .put("flow_prompt","9:16 vertical video, locked camera").put("framing","portrait frame")
            .put("approved_frame_sha256","old-approval").put("flow_image_revision",77)
        val source=JSONObject().put("aspect_ratio","9:16").put("title","เรื่องเดิม")
            .put("episodes",JSONArray().put(JSONObject().put("ep",1).put("scenes",JSONArray().put(scene))))
        val before=source.toString()
        val copy=LandscapeProduction.copyPlan(source)
        assertEquals(before,source.toString()); assertEquals("9:16",source.getString("aspect_ratio"))
        assertEquals("16:9",copy.getString("aspect_ratio")); LandscapeProduction.requirePlan(copy)
        assertTrue(runCatching { LandscapeProduction.requirePlan(source) }.isFailure)
        val updated=copy.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        assertFalse(updated.has("approved_frame_sha256")); assertFalse(updated.has("flow_image_revision"))
        assertEquals("needs_revision",updated.getString("user_review"))
        assertEquals(person.toString(),updated.getJSONArray("characters").getJSONObject(0).toString())
        assertEquals("เรื่องนี้จริงหรือคะ",updated.getString("dialogue"))
        assertEquals("horizontal 16:9 image, 1920 x 1080, living room",updated.getString("image_prompt"))
        assertEquals("16:9 horizontal video, locked camera",updated.getString("flow_prompt"))
        assertEquals("horizontal frame",updated.getString("framing"))
        assertEquals(1920,EpisodeRules.WIDTH); assertEquals(1080,EpisodeRules.HEIGHT)
    }
}
