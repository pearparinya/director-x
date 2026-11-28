package com.pearparinya.automovie

import org.json.JSONObject

internal object SceneRepairGuard {
    private fun bindings(scene: JSONObject): List<List<String>>? {
        val people = scene.optJSONArray("characters") ?: return null
        return (0 until people.length()).map { i ->
            val person = people.optJSONObject(i) ?: return null
            listOf("name","master","character_id","library_id","outfit").map { person.optString(it).trim() }
        }.sortedBy { it.joinToString("\u0000") }
    }
    fun preservesCast(original: JSONObject, replacement: JSONObject): Boolean {
        val after = bindings(replacement) ?: return false
        // Missing characters can be reconstructed by the existing registered-cast validator.
        // Never relax protection when an original array exists (including an empty array).
        if (!original.has("characters")) return true
        val before = bindings(original) ?: return false
        return before == after
    }
    fun preservesStory(original: JSONObject, replacement: JSONObject, repairMixedAudio: Boolean = false): Boolean {
        if (!preservesCast(original, replacement)) return false
        return listOf("scene", "action", "dialogue", "speaker", "narration", "dialogue_turns",
            "start_state", "end_state", "story_day", "story_time", "location",
            "activity", "continuity_group", "wardrobe_event", "mentioned_characters",
            "offscreen_characters").all { key ->
            !original.has(key) || (replacement.has(key) &&
                original.opt(key)?.toString() == replacement.opt(key)?.toString()) ||
                (key == "narration" && repairMixedAudio && original.optString("dialogue").isNotBlank() &&
                    original.optString("speaker").isNotBlank() && original.optString("narration").isNotBlank() &&
                    replacement.has("narration") && replacement.optString("narration").isEmpty())
        }
    }
}
