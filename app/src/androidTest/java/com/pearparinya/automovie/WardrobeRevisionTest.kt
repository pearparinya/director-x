package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WardrobeRevisionTest {
    private val old="Top: black jacket; Bottom: black trousers; Shoes: black boots; Accessories: none"
    private val fresh="Top: blue work shirt; Bottom: gray work trousers; Shoes: rubber boots; Accessories: none"
    private fun person(name: String,id: String)=JSONObject().put("name",name).put("master",id).put("outfit",old)
    private fun story(): JSONObject {
        val scenes=JSONArray()
        for(n in 1..2) scenes.put(JSONObject().put("scene",n).put("story_day","DAY01").put("user_review","approved").put("flow_image_revision",5).put("characters",JSONArray().put(person("A","SUPPORT_00000082_MASTER.png")).put(person("B","SUPPORT_00000094_MASTER.png"))))
        return JSONObject().put("episodes",JSONArray().put(JSONObject().put("ep",1).put("scenes",scenes)))
    }
    @Test fun appliesOnlySelectedPersonAcrossDayWithoutChangingOriginal() {
        val original=story(); val before=original.toString()
        val updated=WardrobeRevision.apply(original,listOf(WardrobeRevision.Choice("SUPPORT_00000082_MASTER","DAY01",fresh)))
        assertEquals(before,original.toString())
        val scenes=updated.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        for(i in 0 until scenes.length()) {
            val scene=scenes.getJSONObject(i); assertEquals(fresh,scene.getJSONArray("characters").getJSONObject(0).getString("outfit"))
            assertEquals(old,scene.getJSONArray("characters").getJSONObject(1).getString("outfit"))
            assertEquals("needs_revision",scene.getString("user_review")); assertFalse(scene.has("flow_image_revision"))
        }
    }
    @Test fun rejectsUnknownAndIncompleteProposals() {
        val original=story(); val before=original.toString()
        assertTrue(runCatching { WardrobeRevision.apply(original,listOf(WardrobeRevision.Choice("UNKNOWN","DAY01",fresh))) }.isFailure)
        assertTrue(runCatching { WardrobeRevision.apply(original,listOf(WardrobeRevision.Choice("SUPPORT_00000082_MASTER","DAY01","same as before"))) }.isFailure)
        assertEquals(before,original.toString())
    }
    @Test fun changingOneDayPreservesOtherDaysAndUnrelatedScenes() {
        val original=story()
        val scenes=original.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        scenes.put(JSONObject().put("scene",3).put("story_day","DAY02").put("user_review","approved").put("flow_image_revision",7).put("characters",JSONArray().put(person("A","SUPPORT_00000082_MASTER.png"))))
        scenes.put(JSONObject().put("scene",4).put("story_day","DAY01").put("user_review","approved").put("flow_image_revision",8).put("characters",JSONArray().put(person("B","SUPPORT_00000094_MASTER.png"))))
        val before=original.toString()
        val updated=WardrobeRevision.apply(original,listOf(WardrobeRevision.Choice("SUPPORT_00000082_MASTER","DAY01",fresh)))
        val result=updated.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        assertEquals(before,original.toString())
        assertEquals(old,result.getJSONObject(2).getJSONArray("characters").getJSONObject(0).getString("outfit"))
        assertEquals("approved",result.getJSONObject(2).getString("user_review"))
        assertEquals(7,result.getJSONObject(2).getInt("flow_image_revision"))
        assertEquals(old,result.getJSONObject(3).getJSONArray("characters").getJSONObject(0).getString("outfit"))
        assertEquals(8,result.getJSONObject(3).getInt("flow_image_revision"))
    }

}
