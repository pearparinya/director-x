package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScenePropMasterAssignmentTest {
    @Test fun reviewedAssignmentPersistsInScene() {
        val scene = JSONObject("""{"prop_links":[{"item":"ตะเกียงส่องวิญญาณ","holder":"กวิน"}]}""")
        ScenePropBinding.assignMaster(scene, 0, "p017")
        assertEquals("P017", scene.getJSONArray("prop_links").getJSONObject(0).getString("prop_master_id"))
        assertTrue(SceneReferencePolicy.propInstructions(scene).contains("PROP MASTER P017"))
        ScenePropBinding.clearMaster(scene, 0)
        assertFalse(scene.getJSONArray("prop_links").getJSONObject(0).has("prop_master_id"))
        assertEquals("ตะเกียงส่องวิญญาณ", scene.getJSONArray("prop_links").getJSONObject(0).getString("item"))
    }

    @Test fun invalidAssignmentCannotMutateScene() {
        val scene = JSONObject("""{"prop_links":[{"item":"lantern"}]}""")
        try {
            ScenePropBinding.assignMaster(scene, 0, "../unsafe")
            fail("Expected invalid MASTER ID to be rejected")
        } catch (_: IllegalArgumentException) {
            assertFalse(scene.getJSONArray("prop_links").getJSONObject(0).has("prop_master_id"))
        }
    }
}
