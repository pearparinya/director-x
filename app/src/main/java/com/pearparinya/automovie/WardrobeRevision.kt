package com.pearparinya.automovie

import org.json.JSONObject

/** Apply reviewed proposals to a copy, preserving all other story data and old image files. */
internal object WardrobeRevision {
    data class Choice(val master: String,val day: String,val outfit: String)
    fun apply(story: JSONObject, choices: List<Choice>): JSONObject {
        require(choices.isNotEmpty()) { "เลือกชุดอย่างน้อยหนึ่งรายการ" }
        require(choices.all { it.master.isNotBlank() && it.day.isNotBlank() }) { "ชุดต้องระบุตัวละครและวัน" }
        val keys=choices.map { referenceId(it.master).lowercase() to it.day.trim().lowercase() }
        require(keys.distinct().size==keys.size) { "พบชุดซ้ำสำหรับตัวละครและวันเดียวกัน" }
        choices.forEach { AutoDirectorWardrobe.parts(it.outfit) }
        val next=JSONObject(story.toString())
        val seen=mutableSetOf<Pair<String,String>>()
        for(shot in AutoDirectorWardrobe.shots(next)) {
            val day=shot.scene.optString("story_day").trim().lowercase()
            var changed=false
            FlowReferencePrompt.people(shot.scene).forEach { p ->
                val id=referenceId(p.optString("master")).lowercase() to day
                val index=keys.indexOf(id)
                if(index>=0) {
                    if(next.optInt("autodirector_version")>=AutoDirectorContent.version) AutoDirectorWardrobe.v45Details(choices[index].outfit,p.optString("character_type") in listOf("animal","creature"))
                    p.put("outfit",choices[index].outfit); seen.add(id); changed=true
                }
            }
            if(changed) { shot.scene.put("user_review","needs_revision"); shot.scene.remove("flow_image_revision") }
        }
        require(seen==keys.toSet()) { "ตัวละครหรือวันไม่ตรงกับบท กรุณาตรวจใหม่" }
        val before=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(story)).toSet()
        val after=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(next)).filter { it !in before }
        require(after.isEmpty()) { "ชุดใหม่ขัดกับความต่อเนื่อง: "+after.take(3).joinToString("; ") }
        return next
    }
}
