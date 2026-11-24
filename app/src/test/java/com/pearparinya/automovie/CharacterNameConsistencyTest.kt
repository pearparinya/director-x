package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterNameConsistencyTest {
    @Test fun resolvesRegisteredNamesWithoutConsumingPartOfAnotherName() {
        val actor = JSONObject().put("name","ปริญญา").put("master","PIA_MASTER.png").put("english_name","Na-Pia")
        val aliases = JSONObject()
        CharacterAliasRegistry.add(aliases,"Pia","ปริญญา","PIA_MASTER.png")
        val scene = JSONObject().put("characters",JSONArray().put(actor))
            .put("flow_action","Na-Pia looks at Pia. Other-Pia turns.")
        CharacterAliasRegistry.applyRegistered(scene,listOf(actor),aliases)
        assertEquals("ปริญญา looks at ปริญญา. Other-Pia turns.",scene.getString("flow_action"))
    }
    @Test fun fullPiaNameAndPlaceAreDistinctFromBareKhud() {
        val pia = JSONObject().put("name","นายเปีย บ้านคลองขุด").put("master","pia.png")
        val scene = JSONObject().put("characters",JSONArray().put(pia))
            .put("flow_action","Nai Pia Ban Khlong Khud walks in Ban Khlong Khud. Khud waits.")
        CharacterAliasRegistry.applyRegistered(scene,listOf(pia),JSONObject())
        assertEquals("นายเปีย บ้านคลองขุด walks in บ้านคลองขุด. Khud waits.",scene.getString("flow_action"))
        scene.put("characters",JSONArray()).put("flow_action","Nai Pia Ban Khlong Khud walks.")
        CharacterAliasRegistry.applyRegistered(scene,listOf(pia),JSONObject())
        assertEquals("Nai Pia Ban Khlong Khud walks.",scene.getString("flow_action"))
    }
}
