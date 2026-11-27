package com.pearparinya.automovie

import org.json.JSONObject
import java.util.Locale

internal object CharacterContinuity {
    data class Beat(val scene: String, val actor: String, val master: String, val day: String, val time: String,
        val location: String, val outfit: String, val state: String, val endState: String, val group: String,
        val change: Boolean, val flashback: Boolean, val lighting: String = "", val lightingChange: Boolean = false)
    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT)
    private fun clock(s: String): Int? {
        val m = Regex("^([01]?\\d|2[0-3])[:.]([0-5]\\d)(?:\\s*น\\.)?$").matchEntire(s.trim()) ?: return null
        return m.groupValues[1].toInt()*60+m.groupValues[2].toInt()
    }
    fun beats(story: JSONObject): List<Beat> = buildList {
        val eps = story.optJSONArray("episodes")
        for (e in 0 until (eps?.length() ?: 0)) {
            val ep = eps?.optJSONObject(e) ?: continue
            val scenes = ep.optJSONArray("scenes")
            for (i in 0 until (scenes?.length() ?: 0)) {
                val s = scenes?.optJSONObject(i) ?: continue
                val people = s.optJSONArray("characters")
                for (p in 0 until (people?.length() ?: 0)) {
                    val a = people?.optJSONObject(p) ?: continue
                    add(Beat("EP${ep.optInt("ep")} SC${s.optInt("scene")}",a.optString("name"),a.optString("master"),
                        s.optString("story_day"),s.optString("story_time"),s.optString("location"),a.optString("outfit"),
                        a.optString("character_state"),a.optString("character_end_state"),s.optString("continuity_group"),
                        a.optBoolean("wardrobe_change"),s.optBoolean("flashback"),s.optString("lighting"),s.optBoolean("lighting_change")))
                }
            }
        }
    }
    fun issues(beats: List<Beat>): List<String> = buildList {
        val previous = mutableMapOf<String,Beat>()
        for (b in beats) {
            if (b.master.isBlank()) continue
            val id = norm(b.master)
            val p = previous[id]
            if (b.flashback) continue
            if (clock(b.time)==null) add("${b.scene} ${b.actor}: ตรวจเวลาให้เป็น HH:mm")
            if (p != null && b.day.isNotBlank() && norm(b.day)==norm(p.day)) {
                val now = clock(b.time); val before = clock(p.time)
                if (now!=null && before!=null && now<before) add("${b.scene} ${b.actor}: เวลาย้อนจาก ${p.scene}")
                if (b.outfit.isNotBlank() && p.outfit.isNotBlank() && norm(b.outfit)!=norm(p.outfit) && !b.change)
                    add("${b.scene} ${b.actor}: ชุดเปลี่ยนโดยไม่ระบุ wardrobe_change")
                if (b.group.isNotBlank() && b.group==p.group && norm(b.location)==norm(p.location) &&
                    b.lighting.isNotBlank() && p.lighting.isNotBlank() && norm(b.lighting)!=norm(p.lighting) && !b.lightingChange)
                    add("${b.scene} ${b.actor}: ตรวจแสงต่อเนื่องจาก ${p.scene} หรือระบุ lighting_change")
                if (b.group.isNotBlank() && b.group==p.group && p.endState.isNotBlank() && b.state.isNotBlank() && norm(p.endState)!=norm(b.state))
                    add("${b.scene} ${b.actor}: ตรวจสถานะต้นฉากเทียบกับท้าย ${p.scene}")
            }
            previous[id]=b
        }
    }
    fun imageLighting(previous: JSONObject?, current: JSONObject): String {
        val own = current.optString("lighting")
        if (previous == null || current.optBoolean("flashback") || current.optBoolean("lighting_change")) return own
        val group = current.optString("continuity_group")
        val day = current.optString("story_day")
        val location = current.optString("location")
        if (group.isBlank() || day.isBlank() || location.isBlank() ||
            group != previous.optString("continuity_group") ||
            norm(day) != norm(previous.optString("story_day")) ||
            norm(location) != norm(previous.optString("location"))) return own
        val before = clock(previous.optString("story_time"))
        val now = clock(current.optString("story_time"))
        if (before == null || now == null || now - before !in 0..2) return own
        return previous.optString("lighting").ifBlank { own }
    }
    fun guide(people: List<JSONObject>): String = people.mapNotNull { p ->
        val state = p.optString("character_state").trim()
        if (state.isBlank()) null else p.optString("name")+": "+state
    }.takeIf { it.isNotEmpty() }?.joinToString("\n",prefix="\n\nCHARACTER START STATE (preserve until story action changes it):\n").orEmpty()
}
