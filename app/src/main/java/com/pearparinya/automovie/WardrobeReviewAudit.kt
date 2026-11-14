package com.pearparinya.automovie

import org.json.JSONObject

/** Warn without changing the story; only compare the same character and story day. */
internal object WardrobeReviewAudit {
    fun warnings(scenes: List<JSONObject>): List<String> {
        val seen=linkedMapOf<Pair<String,String>,Pair<String,String>>()
        val issues=linkedSetOf<String>()
        scenes.forEach { scene ->
            val day=scene.optString("story_day").trim()
            val cast=scene.optJSONArray("characters") ?: return@forEach
            for(i in 0 until cast.length()) {
                val person=cast.optJSONObject(i) ?: continue
                val name=person.optString("name").ifBlank { "ตัวละคร" }
                val id=FlowCast.id(person) ?: name.trim().lowercase()
                val outfit=person.optString("outfit").trim()
                val where="${name} • ${day.ifBlank { "ไม่ระบุวัน" }}"
                val parts=runCatching { AutoDirectorWardrobe.parts(outfit) }.getOrNull()
                if(parts==null) { issues.add("$where: ชุดยังไม่ครบ 4 ส่วน กรุณาตรวจ") ; continue }
                if(parts["Shoes"].orEmpty().trim().equals("none",true)) issues.add("$where: ระบุว่าเท้าเปล่า • กรุณายืนยันว่าตั้งใจ")
                val top=parts["Top"].orEmpty()
                if(top.length<35 && !top.equals("none",true)) issues.add("$where: ตรวจรายละเอียดสี ทรง คอ แขน และเนื้อผ้าของชุด")
                if(day.isNotBlank()) {
                    val key=id to day.lowercase()
                    val old=seen.putIfAbsent(key,outfit to where)
                    if(old!=null && old.first!=outfit) issues.add("$where: รายละเอียดชุดต่างกันในวันเดียวกัน • ตรวจแต่ละฉากก่อนยืนยัน")
                }
            }
        }
        return issues.toList()
    }
}
