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
}
