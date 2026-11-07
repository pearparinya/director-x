package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterAliasRegistryTest {
    private fun scene(name: String="ปิ่นมณี",master: String="P_MASTER.png") =
        JSONObject().put("characters",JSONArray().put(JSONObject().put("name",name).put("master",master)))
            .put("flow_action","Pinmanee holds a letter. Pinmanee2 watches.")
            .put("dialogue","Pinmanee ไม่เปลี่ยนบทพูด")
    @Test fun confirmedAliasAppliesOnlyToMatchingIdentityAndWholeToken() {
        val registry=JSONObject()
        CharacterAliasRegistry.add(registry,"Pinmanee","ปิ่นมณี","P_MASTER.png")
        val good=scene(); CharacterAliasRegistry.apply(good,registry)
        assertEquals("ปิ่นมณี holds a letter. Pinmanee2 watches.",good.getString("flow_action"))
        assertEquals("Pinmanee ไม่เปลี่ยนบทพูด",good.getString("dialogue"))
        val other=scene(master="OTHER_MASTER.png"); CharacterAliasRegistry.apply(other,registry)
        assertTrue(other.getString("flow_action").startsWith("Pinmanee"))
    }
    @Test fun conflictingAliasDoesNotOverwriteFirstIdentity() {
        val registry=JSONObject()
        CharacterAliasRegistry.add(registry,"Pinmanee","ปิ่นมณี","P_MASTER.png")
        assertTrue(runCatching { CharacterAliasRegistry.add(registry,"pinmanee","อื่น","OTHER_MASTER.png") }.isFailure)
        assertEquals(1,registry.getJSONArray("entries").length())
    }
}
