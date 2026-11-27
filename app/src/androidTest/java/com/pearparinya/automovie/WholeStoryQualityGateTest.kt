package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WholeStoryQualityGateTest {
    @Test fun validatesStorySizesAndDetectsDuplicateOrMissingScenes() {
        for (size in listOf(8,20,50,100)) {
            val rows = JSONArray()
            for (i in 1..size) rows.put(JSONObject().put("scene",i))
            val story = JSONObject().put("episode_count",1).put("scene_count",size)
                .put("episodes",JSONArray().put(JSONObject().put("scenes",rows)))
            assertTrue(WholeStoryQualityGate.issues(story,1,size).isEmpty())
            rows.getJSONObject(size-1).put("scene",1)
            assertTrue(WholeStoryQualityGate.issues(story,1,size).any { it.contains("ซีน $size") })
            rows.getJSONObject(size-1).put("scene",size)
            rows.remove(size-1)
            assertFalse(WholeStoryQualityGate.issues(story,1,size).isEmpty())
            assertFalse(WholeStoryQualityGate.issues(story,2,size).isEmpty())
        }
    }
}
