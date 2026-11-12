package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Fingerprints only fields that are relevant to each review. */
internal object ReviewFingerprints {
    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun people(scene: JSONObject, wardrobe: Boolean): String {
        val a=scene.optJSONArray("characters") ?: JSONArray()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.joinToString("|") {
            listOf(it.optString("name"),it.optString("master")) .plus(if(wardrobe) listOf(it.optString("outfit")) else emptyList()).joinToString("\u001f")
        }
    }
    fun wardrobe(scenes: List<JSONObject>): String = digest(scenes.joinToString("|") {
        it.optString("ep")+":"+it.optString("scene")+":"+it.optString("story_day")+":"+people(it,true)
    })
    fun legacyWardrobe(scenes: List<JSONObject>): String = digest(scenes.joinToString("|") {
        it.optString("story_day")+":"+it.optString("characters")
    })
    fun props(scene: JSONObject): String = digest(listOf("start_state","action","props","location","story_day")
        .joinToString("|") { scene.optString(it) }+"|"+people(scene,false))
    fun legacyProps(scene: JSONObject): String = digest(listOf("start_state","action","props","location","story_day","characters")
        .joinToString("|") { scene.optString(it) })
}
