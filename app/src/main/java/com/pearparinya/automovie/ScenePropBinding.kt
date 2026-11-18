package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Bind reviewed equipment to the scene content that was actually reviewed. */
internal object ScenePropBinding {
    fun signature(scene: JSONObject): String {
        val snapshot=JSONObject()
        listOf("story_day","location","action","start_state","end_state","dialogue","props").forEach { snapshot.put(it,scene.optString(it)) }
        snapshot.put("characters",scene.optJSONArray("characters") ?: JSONArray())
        return MasterLibrary.digest(snapshot.toString().toByteArray(Charsets.UTF_8))
    }
    fun valid(scene: JSONObject, assignments: JSONArray?): JSONArray {
        val result=JSONArray(); val digest=signature(scene)
        val names=FlowReferencePrompt.people(scene).map { it.optString("name") }.toSet()
        for(i in 0 until (assignments?.length() ?: 0)) {
            val row=assignments?.optJSONObject(i) ?: continue
            val holder=row.optString("holder")
            if(row.optString("source_scene_digest")==digest && (holder.isBlank() || holder in names)) result.put(row)
        }
        return result
    }
    /**
     * Persist a user-reviewed prop MASTER selection on an existing scene link.
     * Do not accept an AI suggestion as a MASTER selection.
     */
    fun assignMaster(scene: JSONObject, linkIndex: Int, masterId: String): JSONObject {
        val links = scene.optJSONArray("prop_links") ?: error("No reviewed prop links")
        require(linkIndex in 0 until links.length()) { "Invalid prop link index" }
        val link = links.optJSONObject(linkIndex) ?: error("Invalid prop link")
        require(link.optString("item").trim().isNotEmpty()) { "Missing reviewed prop item" }
        val id = masterId.trim().uppercase()
        require(id.matches(Regex("^[A-Z][A-Z0-9_-]{1,63}$"))) { "Invalid prop MASTER ID" }
        link.put("prop_master_id", id)
        return scene
    }

    /** Remove only the MASTER assignment, leaving the approved story prop intact. */
    fun clearMaster(scene: JSONObject, linkIndex: Int): JSONObject {
        val links = scene.optJSONArray("prop_links") ?: error("No reviewed prop links")
        require(linkIndex in 0 until links.length()) { "Invalid prop link index" }
        val link = links.optJSONObject(linkIndex) ?: error("Invalid prop link")
        link.remove("prop_master_id")
        return scene
    }

}
