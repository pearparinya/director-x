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
    @Test fun registeredAliasesWorkForManyCharactersAndPreserveDialogue() {
        val defs=listOf(
            JSONObject().put("name","ภูวดล").put("master","A_MASTER.png").put("english_name","Phuwadol"),
            JSONObject().put("name","ปิ่นมณี").put("master","B_MASTER.png").put("english_name","Pinmanee"),
            JSONObject().put("name","นที").put("master","C_MASTER.png").put("english_name","Natee"))
        val people=JSONArray().put(JSONObject().put("name","ภูวดล").put("master","A_MASTER.png"))
            .put(JSONObject().put("name","ปิ่นมณี").put("master","B_MASTER.png"))
            .put(JSONObject().put("name","นที").put("master","C_MASTER.png"))
        val scene=JSONObject().put("characters",people)
            .put("flow_action","Phuwadol walks. Pinmanee looks. Natee stands. Pinmanee2 watches.")
            .put("dialogue","Phuwadol พูด")
        CharacterAliasRegistry.applyRegistered(scene,defs,JSONObject())
        assertEquals("ภูวดล walks. ปิ่นมณี looks. นที stands. Pinmanee2 watches.",scene.getString("flow_action"))
        assertEquals("Phuwadol พูด",scene.getString("dialogue"))
    }
    @Test fun registeredAliasCollisionIsNotSilentlyAssigned() {
        val defs=listOf(
            JSONObject().put("name","นที").put("master","A_MASTER.png").put("english_name","Nati"),
            JSONObject().put("name","ภาคิน").put("master","B_MASTER.png").put("english_name","NATI"))
        val scene=JSONObject().put("characters",JSONArray()
            .put(JSONObject().put("name","นที").put("master","A_MASTER.png"))
            .put(JSONObject().put("name","ภาคิน").put("master","B_MASTER.png")))
            .put("flow_action","Nati stands")
        assertTrue(runCatching { CharacterAliasRegistry.applyRegistered(scene,defs,JSONObject()) }.isFailure)
        assertEquals("Nati stands",scene.getString("flow_action"))
    }
    @Test fun confirmedAliasCannotOverrideDifferentRegisteredActor() {
        val defs=listOf(
            JSONObject().put("name","นที").put("master","A_MASTER.png").put("english_name","Nati"),
            JSONObject().put("name","ภาคิน").put("master","B_MASTER.png").put("english_name","Pakin"))
        val confirmed=JSONObject()
        CharacterAliasRegistry.add(confirmed,"Nati","ภาคิน","B_MASTER.png")
        val scene=JSONObject().put("characters",JSONArray()
            .put(JSONObject().put("name","นที").put("master","A_MASTER.png"))
            .put(JSONObject().put("name","ภาคิน").put("master","B_MASTER.png")))
            .put("flow_action","Nati stands. Pakin looks.")
        assertTrue(runCatching { CharacterAliasRegistry.applyRegistered(scene,defs,confirmed) }.isFailure)
        assertEquals("Nati stands. Pakin looks.",scene.getString("flow_action"))
    }
    @Test fun aliasWithWrongMasterIsNotRewritten() {
        val defs=listOf(JSONObject().put("name","นที").put("master","A_MASTER.png").put("english_name","Natee"))
        val scene=JSONObject().put("characters",JSONArray()
            .put(JSONObject().put("name","นที").put("master","B_MASTER.png")))
            .put("flow_action","Natee stands")
        CharacterAliasRegistry.applyRegistered(scene,defs,JSONObject())
        assertEquals("Natee stands",scene.getString("flow_action"))
    }
}
