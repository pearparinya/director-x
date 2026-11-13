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
}
