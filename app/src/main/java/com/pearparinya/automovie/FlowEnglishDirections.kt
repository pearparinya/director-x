package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Translate only production directions; never send or replace the written dialogue. */
internal object FlowEnglishDirections {
    private val thai=Regex("[\\u0E00-\\u0E7F]")
    fun schema(): JSONObject {
        val string=JSONObject().put("type","string")
        return JSONObject().put("type","object").put("properties",JSONObject()
            .put("action",string).put("positions",JSONObject().put("type","array").put("items",JSONObject().put("type","string")))
            .put("sound",JSONObject().put("type","string")))
            .put("required",JSONArray().put("action").put("positions").put("sound"))
    }
    fun prompt(s: FlowSceneSpec): String {
        val mapping=JSONArray(s.cast.mapIndexed { i,p -> JSONObject().put("label","Character ${i+1}").put("name",p.name).put("english_name",p.englishName) })
        val data=JSONObject().put("action",s.action).put("positions",JSONArray(s.cast.map { it.position })).put("sound",s.sound).put("characters",mapping)
        return """Translate the supplied action, each ordered position and sound direction to concise English for an 8-second video. Replace character names with their exact Character N labels using the supplied mapping. Preserve every event, position and sound; do not invent, omit, reinterpret or add props, people, dialogue or effects. Preserve the number and order of positions. If sound is empty, return an empty string. Return exactly the requested JSON. Do not translate or add spoken dialogue. Input: $data"""
    }
    fun apply(s: FlowSceneSpec,result: JSONObject): FlowSceneSpec {
        require(result.opt("action") is String && result.opt("sound") is String) { "คำแปลไม่ครบ" }
        val action=result.getString("action").trim(); val sound=result.getString("sound").trim()
        val positions=result.optJSONArray("positions") ?: error("คำแปลตำแหน่งไม่ครบ")
        require(action.isNotEmpty() && positions.length()==s.cast.size) { "คำแปลการกระทำหรือตำแหน่งไม่ครบ" }
        require(s.sound.isBlank()==sound.isBlank()) { "คำแปลเสียงไม่ตรงกับบทต้นฉบับ" }
        val mentioned=Regex("(?i)\\bCharacter\\s+([0-9]+)\\b").findAll(action+" "+sound).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        require(mentioned.all { it in 1..s.cast.size }) { "คำแปลระบุตัวละครไม่ตรงกับซีน" }
        require(!thai.containsMatchIn(action) && !thai.containsMatchIn(sound)) { "คำแปลยังมีภาษาไทยนอกบทพูด" }
        val cast=s.cast.mapIndexed { i,p ->
            require(positions.opt(i) is String) { "คำแปลตำแหน่งไม่ถูกต้อง" }
            val position=positions.getString(i).trim()
            require(position.isNotEmpty() && !thai.containsMatchIn(position)) { "คำแปลตำแหน่งไม่ครบหรือยังมีภาษาไทย" }
            p.copy(position=position)
        }
        return s.copy(action=action,cast=cast,sound=sound)
    }
}
