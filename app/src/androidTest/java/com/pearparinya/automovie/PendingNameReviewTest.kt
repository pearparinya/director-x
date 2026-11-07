package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingNameReviewTest {
    private fun person(name: String, master: String) = JSONObject().put("name",name).put("master",master)
        .put("visual_description","adult").put("screen_position","center").put("outfit","shirt")
    private fun scene(n: Int,name: String,master: String,action: String) =
        JSONObject().put("scene",n).put("characters",JSONArray().put(person(name,master))).put("flow_action",action)
    @Test fun collectsEverySceneWithoutChangingPendingAndUsesConfirmedAliases() {
        val first=person("ภูวดล","SUPPORT_00000001_MASTER.png").put("auto_cast",true)
        val second=person("ปิ่นมณี","SUPPORT_00000002_MASTER.png").put("auto_cast",true)
        val spec=JSONObject().put("supporting_characters",JSONArray().put(first).put(second))
        val batch=JSONObject().put("scenes",JSONArray()
            .put(scene(9,"ภูวดล","SUPPORT_00000001_MASTER.png","Phuwadol walks"))
            .put(scene(10,"ปิ่นมณี","SUPPORT_00000002_MASTER.png","Pinmanee speaks")))
        val original=batch.toString()
        val registry=JSONObject()
        assertEquals(listOf("Phuwadol","Pinmanee"),PendingNameReview.collect(batch,spec,registry).map { it.alias })
        CharacterAliasRegistry.add(registry,"Phuwadol","ภูวดล","SUPPORT_00000001_MASTER.png")
        assertEquals(listOf("Pinmanee"),PendingNameReview.collect(batch,spec,registry).map { it.alias })
        assertEquals(original,batch.toString())
    }
    @Test fun registeredAliasRequiresUniqueCastAndMatchingMaster() {
        val registry=JSONObject()
        val actor=person("ปิ่นมณี","SUPPORT_00000002_MASTER.png").put("english_name","Pinmanee")
        val spec=JSONObject().put("supporting_characters",JSONArray().put(actor.put("auto_cast",true)))
        val row=scene(9,"ปิ่นมณี","SUPPORT_00000002_MASTER.png","Pinmanee walks")
        val batch=JSONObject().put("scenes",JSONArray().put(row))
        assertTrue(PendingNameReview.collect(batch,spec,registry).isEmpty())
        assertEquals("Pinmanee walks",row.getString("flow_action"))
        val copy=JSONObject(row.toString())
        CharacterAliasRegistry.applyRegistered(copy,FlowCast.definitions(spec),registry)
        assertEquals("ปิ่นมณี walks",copy.getString("flow_action"))
        val duplicate=person("คนอื่น","SUPPORT_00000003_MASTER.png").put("english_name","PINMANEE").put("auto_cast",true)
        spec.getJSONArray("supporting_characters").put(duplicate)
        val ambiguous=JSONObject(row.toString())
        CharacterAliasRegistry.applyRegistered(ambiguous,FlowCast.definitions(spec),registry)
        assertEquals("Pinmanee walks",ambiguous.getString("flow_action"))
        spec.getJSONArray("supporting_characters").remove(1)
        val wrong=scene(9,"ปิ่นมณี","SUPPORT_00000003_MASTER.png","Pinmanee walks")
        CharacterAliasRegistry.applyRegistered(wrong,FlowCast.definitions(spec),registry)
        assertEquals("Pinmanee walks",wrong.getString("flow_action"))
    }
}
