package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Three fixed leads plus explicitly registered, separately referenced adult supporting characters. */
internal object FlowCast {
    private val supportingMaster=Regex("^SUPPORT_[A-Fa-f0-9]{8}_MASTER[.](png|jpg|jpeg|webp)$",RegexOption.IGNORE_CASE)
    fun id(person: JSONObject): String? = person.optString("master").takeIf { supportingMaster.matches(it) }?.substringBeforeLast('.')?.replace(Regex("_MASTER$",RegexOption.IGNORE_CASE),"")?.lowercase() ?: StoryIdentity.keyFor(person.optString("name"))
    fun supporting(plan: JSONObject?): List<JSONObject> = plan?.optJSONArray("supporting_characters")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    fun definitions(plan: JSONObject?): List<JSONObject> = (if(supporting(plan).isNotEmpty() && supporting(plan).all { it.optBoolean("auto_cast") }) emptyList() else FlowProductionPack.names.map { (id,name) -> JSONObject().put("name",name).put("master","${id.uppercase()}_MASTER.png").put("visual_description",plan?.optJSONObject("lead_references")?.optJSONObject(id)?.optString("visual_description")?.takeIf { it.isNotBlank() } ?: FlowProductionPack.dna.getValue(id)) })+supporting(plan)
    fun issues(plan: JSONObject): List<String> = buildList {
        val rows=supporting(plan)
        if(rows.size>8) add("รองรับตัวละครเสริมไม่เกิน 8 คนต่อเรื่อง")
        rows.forEach { p ->
            if(p.optString("name").isBlank() || (StoryIdentity.keyFor(p.optString("name"))!=null && !p.optBoolean("auto_cast")) || !supportingMaster.matches(p.optString("master")) || p.optString("visual_description").isBlank()) add("ข้อมูลตัวละครเสริมต้องมีชื่อ ภาพอ้างอิงแยกรายคน และคำบรรยาย")
        }
        if(rows.map { it.optString("name") }.distinct().size!=rows.size || rows.map { id(it) }.distinct().size!=rows.size) add("ตัวละครเสริมมีชื่อหรือภาพอ้างอิงซ้ำ")
    }
    fun matches(plan: JSONObject, person: JSONObject): Boolean {
        val key=id(person) ?: return false
        if(supporting(plan).any { it.optBoolean("auto_cast") && it.optString("name")==person.optString("name") && id(it)==key }) return true
        if(StoryIdentity.keyFor(person.optString("name"))!=null) return StoryIdentity.matchesMaster(person.optString("name"),person.optString("master"))
        return supporting(plan).any { it.optString("name")==person.optString("name") && id(it)==key }
    }
}
