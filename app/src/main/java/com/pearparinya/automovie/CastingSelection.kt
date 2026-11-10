package com.pearparinya.automovie

import org.json.JSONArray

/** Empty means unrestricted; an explicit pool never silently expands to other actors. */
internal object CastingSelection {
    fun requireIncluded(cast: JSONArray, selected: Set<String>) {
        val castIds=(0 until cast.length()).map { cast.getJSONObject(it).optString("library_id") }.toSet()
        require(castIds.containsAll(selected)) { "AI ยังไม่ได้ใช้ตัวละครที่เลือกครบ กรุณาลองใหม่" }
    }
    fun filter(catalog: JSONArray, selected: Set<String>): JSONArray {
        if(selected.isEmpty()) return catalog
        val out=JSONArray()
        val found=mutableSetOf<String>()
        for(i in 0 until catalog.length()) {
            val actor=catalog.getJSONObject(i); val id=actor.getString("id")
            if(id in selected) { out.put(actor); found.add(id) }
        }
        require(found==selected) { "ตัวละครที่เลือกบางคนไม่อยู่ในคลัง กรุณาเลือกใหม่" }
        return out
    }
}
