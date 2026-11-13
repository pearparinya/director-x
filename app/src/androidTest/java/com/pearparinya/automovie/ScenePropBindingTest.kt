package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScenePropBindingTest {
    @Test fun reviewedEquipmentExpiresWhenSceneChangesButNotWhenReviewStatusChanges() {
        val scene=JSONObject().put("story_day","DAY01").put("location","ห้อง").put("action","หมอถือดาบ")
            .put("start_state","หมอถือดาบ").put("end_state","หมอวางดาบ")
            .put("characters",JSONArray().put(JSONObject().put("name","หมอ")))
        val approved=JSONObject().put("prop_id","PROP_01_MASTER").put("holder","หมอ")
            .put("source_scene_digest",ScenePropBinding.signature(scene))
        val rows=JSONArray().put(approved)
        assertEquals(1,ScenePropBinding.valid(scene,rows).length())
        scene.put("user_review","approved")
        assertEquals(1,ScenePropBinding.valid(scene,rows).length())
        scene.put("start_state","ดาบอยู่บนโต๊ะ")
        assertEquals(0,ScenePropBinding.valid(scene,rows).length())
    }
    @Test fun oldOrIncorrectHolderIsNeverSilentlyApproved() {
        val scene=JSONObject().put("characters",JSONArray().put(JSONObject().put("name","ผู้พิทักษ์")))
        val old=JSONObject().put("prop_id","PROP_01_MASTER").put("holder","ผู้พิทักษ์")
        val wrong=JSONObject().put("prop_id","PROP_02_MASTER").put("holder","คนอื่น")
            .put("source_scene_digest",ScenePropBinding.signature(scene))
        assertEquals(0,ScenePropBinding.valid(scene,JSONArray().put(old).put(wrong)).length())
        assertFalse(old.has("source_scene_digest"))
    }
}
