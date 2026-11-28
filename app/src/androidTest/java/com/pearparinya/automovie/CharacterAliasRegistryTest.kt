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
    @Test fun allSixteenStandLibraryIdsResolveWithoutChangingSceneData() {
        val defs=(170..185).map { id ->
            JSONObject().put("library_id","CHAR_$id").put("name","สแตนด์$id").put("master","ST_${id}_MASTER.png")
        }
        for (definition in defs) {
            val actor=JSONObject(definition.toString())
            val row=JSONObject().put("scene",3).put("characters",JSONArray().put(actor))
                .put("dialogue","บทเดิม").put("flow_action",definition.getString("library_id")+" stands.")
            val before=row.getJSONArray("characters").toString()
            repeat(2) { CharacterAliasRegistry.applyRegistered(row,defs,JSONObject()) }
            assertEquals(definition.getString("name")+" stands.",row.getString("flow_action"))
            assertEquals(before,row.getJSONArray("characters").toString())
            assertEquals("บทเดิม",row.getString("dialogue"))
            assertEquals(3,row.getInt("scene"))
        }
    }
    @Test fun duplicateLibraryIdAndWrongMasterAreNotGuessed() {
        val a=JSONObject().put("library_id","CHAR_170").put("name","หนึ่ง").put("master","A_MASTER.png")
        val b=JSONObject().put("library_id","CHAR_170").put("name","สอง").put("master","B_MASTER.png")
        val row=JSONObject().put("characters",JSONArray().put(JSONObject(a.toString())))
            .put("flow_action","CHAR_170 stands.")
        CharacterAliasRegistry.applyRegistered(row,listOf(a,b),JSONObject())
        assertEquals("CHAR_170 stands.",row.getString("flow_action"))
        row.getJSONArray("characters").getJSONObject(0).put("master","WRONG_MASTER.png")
        CharacterAliasRegistry.applyRegistered(row,listOf(a),JSONObject())
        assertEquals("CHAR_170 stands.",row.getString("flow_action"))
    }
    @Test fun presenterModeRejectsOppositeLegacyAliasAndPreservesIdentity() {
        val name = "นายเปีย บ้านคลองขุด"
        val master = "SUPPORT_12345678_MASTER.png"
        for (alias in listOf("Pia", "Piaros")) {
            val opposite = if (alias == "Pia") "Piaros" else "Pia"
            val definition = JSONObject().put("name", name).put("master", master)
                .put("english_name", alias).put("library_id", "CHAR_169")
                .put("character_id", CharacterAliasRegistry.identityId(name, master))
            val confirmed = JSONObject()
            CharacterAliasRegistry.add(confirmed, opposite, name, master)
            val row = JSONObject().put("scene", 7)
                .put("characters", JSONArray().put(JSONObject(definition.toString())))
                .put("dialogue", "บทเดิม").put("flow_action", "$alias stands. $opposite watches. Khud waits.")
            val before = row.getJSONArray("characters").toString()
            repeat(2) { CharacterAliasRegistry.applyRegistered(row, listOf(definition), confirmed) }
            assertEquals("$name stands. $opposite watches. Khud waits.", row.getString("flow_action"))
            assertEquals(before, row.getJSONArray("characters").toString())
            assertEquals("บทเดิม", row.getString("dialogue"))
            assertEquals(7, row.getInt("scene"))
        }
    }
}
