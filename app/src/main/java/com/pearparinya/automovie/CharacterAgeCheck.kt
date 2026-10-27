package com.pearparinya.automovie
import org.json.JSONObject
internal object CharacterAgeCheck {
    fun issues(scene: JSONObject, definitions: List<JSONObject>): List<String> = buildList {
        val rows=scene.optJSONArray("characters") ?: return@buildList
        for(i in 0 until rows.length()) {
            val p=rows.getJSONObject(i)
            val master=definitions.firstOrNull { it.optString("name")==p.optString("name") } ?: continue
            val age=master.optInt("character_age")
            if(age<=0) continue
            if(!p.has("character_age")) p.put("character_age",age) // Upgrade legacy scenes from the frozen registry, never guess.
            if(p.optInt("character_age")!=age) add("${p.optString("name")} ต้องอายุ $age ปีตาม MASTER")
            val description=p.optString("visual_description")
            val explicit=Regex("(?i)([0-9]{2})[ -]*(?:years? old|year-old|ปี)").findAll(description).map { it.groupValues[1].toInt() }
            if(explicit.any { it!=age }) add("อายุในคำบรรยาย ${p.optString("name")} ไม่ตรง MASTER $age ปี")
        }
    }
}
