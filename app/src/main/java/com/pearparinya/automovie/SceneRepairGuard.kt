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
        val before = bindings(original) ?: return false
        return before == bindings(replacement)
    }
}
