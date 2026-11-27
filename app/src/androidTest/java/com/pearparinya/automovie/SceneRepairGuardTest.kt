package com.pearparinya.automovie
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SceneRepairGuardTest {
    private fun scene(master: String = "A.png", outfit: String = "blue") =
        JSONObject().put("characters", JSONArray().put(JSONObject().put("name","A").put("master",master).put("outfit",outfit)))
    @Test fun missingCastCanBeRecoveredButExistingBindingsCannotChange() {
        assertTrue(SceneRepairGuard.preservesCast(JSONObject(),scene()))
        assertTrue(SceneRepairGuard.preservesCast(scene(),scene()))
        assertFalse(SceneRepairGuard.preservesCast(scene(),scene("B.png")))
        assertFalse(SceneRepairGuard.preservesCast(scene(),scene(outfit="red")))
        assertFalse(SceneRepairGuard.preservesCast(scene(),JSONObject()))
        assertFalse(SceneRepairGuard.preservesCast(JSONObject().put("characters",JSONArray()),scene()))
        assertFalse(SceneRepairGuard.preservesCast(JSONObject().put("characters","invalid"),scene()))
    }
}
