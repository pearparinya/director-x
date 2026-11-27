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
    @org.junit.Test fun repeatedDialogueAcrossEpisodesIsAdvisoryAndDoesNotMutateStory() {
        val dialogue = "This is a deliberately long dialogue repeated across separate episodes."
        fun episode(text: String) = org.json.JSONObject().put("scenes", org.json.JSONArray().put(org.json.JSONObject().put("dialogue", text)))
        val story = org.json.JSONObject().put("episodes", org.json.JSONArray().put(episode(dialogue)).put(episode(dialogue)).put(episode("short")))
        val original = story.toString()
        val warnings = WholeStoryQualityGate.warnings(story)
        org.junit.Assert.assertEquals(1, warnings.size)
        org.junit.Assert.assertTrue(warnings.single().contains("EP 2"))
        org.junit.Assert.assertEquals(original, story.toString())
    }
}
