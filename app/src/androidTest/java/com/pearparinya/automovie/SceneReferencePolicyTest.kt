package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneReferencePolicyTest {
    @Test fun perCharacterWardrobeSourcesRemainIndependent() {
        val scene = JSONObject("""{"characters":[
            {"name":"A","wardrobe_source":"MASTER_OUTFIT","outfit":"ignored"},
            {"name":"B","wardrobe_source":"SCENE_OUTFIT","outfit":"lilac blouse"}
        ]}""")
        val result = SceneReferencePolicy.wardrobeInstructions(scene)
        assertTrue(result.contains("A: MASTER_OUTFIT"))
        assertTrue(result.contains("B: SCENE_OUTFIT"))
        assertTrue(result.contains("lilac blouse"))
    }

    @Test fun explicitPropMasterIsBoundById() {
        val scene = JSONObject("""{"prop_links":[{"item":"lantern","prop_master_id":"P017"}]}""")
        assertTrue(SceneReferencePolicy.propInstructions(scene).contains("PROP MASTER P017: lantern"))
    }


    @Test fun propMasterPromptDoesNotAssumePhysicalAttachment() {
        val scene = JSONObject("""{"prop_links":[{"item":"lantern","prop_master_id":"p017"}]}""")
        val prompt = SceneReferencePolicy.propInstructions(scene)
        assertTrue(prompt.contains("PROP MASTER P017: lantern"))
        assertTrue(prompt.contains("require a verified PROP_MASTER image attachment"))
    }

    @Test fun legacyScenesDoNotGainInventedBindings() {
        assertEquals("", SceneReferencePolicy.propInstructions(JSONObject()))
        assertEquals("", SceneReferencePolicy.wardrobeInstructions(JSONObject()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPropMasterIdIsRejected() {
        SceneReferencePolicy.propInstructions(JSONObject("""{"prop_links":[{"item":"lantern","prop_master_id":"../bad"}]}"""))
    }
}
