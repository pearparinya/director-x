package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** AI suggestions are untrusted and remain drafts until explicitly selected. */
internal object PropSuggestionReview {
    fun valid(answer: JSONObject, scenes: Map<String, JSONObject>): List<JSONObject> {
        val rows=answer.optJSONArray("items") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.filter { row ->
            val scene=scenes[row.optString("scene_id")]
            val name=row.optString("item").trim()
            val evidence=row.optString("evidence").trim()
            val holder=row.optString("holder").trim()
            val sources=if(scene==null) "" else listOf("action","start_state","end_state","props").joinToString(" ") { scene.optString(it) }
            val people=scene?.optJSONArray("characters") ?: JSONArray()
            val known=(0 until people.length()).mapNotNull { people.optJSONObject(it)?.optString("name") }
            scene!=null && name.isNotBlank() && name.length<=100 && evidence.length>=2 &&
                sources.contains(evidence,ignoreCase=true) && (holder.isBlank() || holder in known)
        }.distinctBy { listOf(it.optString("scene_id"),it.optString("item"),it.optString("holder")).joinToString("|").lowercase() }
    }
    fun apply(scene: JSONObject, approved: List<JSONObject>) {
        if(approved.isEmpty()) return
        val old=scene.optString("props").trim()
        val lines=old.split("\n").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val links=scene.optJSONArray("prop_links") ?: JSONArray()
        approved.forEach { row ->
            val item=row.optString("item").trim()
            val holder=row.optString("holder").trim().ifBlank { "ยังไม่ระบุผู้ใช้" }
            val details=row.optString("details").trim()
            val group=row.optString("continuity_id").trim()
            val line="$holder: $item"+(if(details.isBlank()) "" else " ($details)")+(if(group.isBlank()) "" else " [กลุ่ม $group • ตรวจความต่อเนื่อง]")
            if(line !in lines) lines.add(line)
            val existing=(0 until links.length()).any { i -> links.optJSONObject(i)?.let { it.optString("item")==item && it.optString("holder")==row.optString("holder").trim() && it.optString("continuity_id")==group } == true }
            if(!existing) links.put(JSONObject().put("item",item).put("holder",row.optString("holder").trim()).put("details",details).put("continuity_id",group).put("evidence",row.optString("evidence").trim()))
        }
        scene.put("props",lines.joinToString("\n")).put("prop_links",links)
    }
}
