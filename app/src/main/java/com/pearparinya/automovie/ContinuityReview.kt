package com.pearparinya.automovie

import org.json.JSONObject

/** Surface observable inconsistencies for human review, without claiming semantic verification. */
internal object ContinuityReview {
    internal fun continuityGroupLocationIssue(
        group: String, previousLocation: String, currentLocation: String
    ): String? {
        if(group.isBlank() || previousLocation.isBlank() || currentLocation.isBlank()) return null
        if(previousLocation.trim().equals(currentLocation.trim(), ignoreCase = true)) return null
        return "กลุ่มต่อเนื่อง $group ใช้สถานที่ต่างกัน ($previousLocation / $currentLocation) โปรดตรวจว่ามีการย้ายสถานที่ในเนื้อเรื่อง"
    }
    fun review(story: JSONObject): List<String> {
        val findings = mutableListOf<String>()
        val groups = mutableMapOf<String, Pair<String, String>>()
        val dailyWardrobes = mutableMapOf<Pair<String, String>, Pair<String, String>>()
        val characterMasters = mutableMapOf<String, Pair<String, String>>()
        val episodes = story.optJSONArray("episodes") ?: return listOf("ไม่มีข้อมูล EP ให้ตรวจ")
        for (e in 0 until episodes.length()) {
            val ep = episodes.optJSONObject(e) ?: continue
            val scenes = ep.optJSONArray("scenes") ?: continue
            for (i in 0 until scenes.length()) {
                val scene = scenes.optJSONObject(i) ?: continue
                val label = "EP${e + 1} ซีน ${scene.optInt("scene", i + 1)}"
                val group = scene.optString("continuity_group").trim()
                val day = scene.optString("story_day").trim()
                val location = scene.optString("location").trim()
                val characters = scene.optJSONArray("characters")
                if (characters != null) for (c in 0 until characters.length()) {
                    val character = characters.optJSONObject(c) ?: continue
                    val name = character.optString("name").trim()
                    if (name.isBlank()) continue
                    val master = character.optString("master").trim()
                    val outfit = character.optString("outfit").trim()
                    if (master.isBlank()) findings.add("$label: $name ไม่มีภาพ MASTER ที่ระบุไว้")
                    else {
                        val previousMaster = characterMasters[name]
                        if (previousMaster != null && previousMaster.first != master)
                            findings.add("$label: $name ใช้ MASTER ต่างจาก ${previousMaster.second} โปรดตรวจการจับคู่ภาพ")
                        else characterMasters.putIfAbsent(name, master to label)
                    }
                    if (day.isBlank()) {
                        findings.add("$label: $name ไม่ระบุ story_day จึงตรวจชุดประจำวันไม่ได้")
                    } else if (outfit.isBlank()) {
                        findings.add("$label: $name ไม่มีรายละเอียดชุดสำหรับ $day")
                    } else {
                        val key = name to day
                        val previousOutfit = dailyWardrobes[key]
                        if (previousOutfit != null && previousOutfit.first != outfit)
                            findings.add("$label: $name ชุดหรือรองเท้าใน $day ต่างจาก ${previousOutfit.second} โปรดตรวจ")
                        else dailyWardrobes.putIfAbsent(key, outfit to label)
                    }
                }
                if (scene.optString("start_state").isBlank() || scene.optString("end_state").isBlank()) findings.add("$label: ตรวจสถานะเริ่มต้นและสถานะสิ้นสุด")
                if (group.isNotBlank()) {
                    val previous = groups[group]
                    if (previous != null && day.isNotBlank() && previous.first.isNotBlank() && day != previous.first)
                        findings.add("$label: กลุ่มต่อเนื่อง $group ใช้วันต่างกัน (${previous.first} / $day) โปรดตรวจ")
                    if(previous != null) continuityGroupLocationIssue(group, previous.second, location)?.let {
                        findings.add("$label: $it")
                    }
                    if (previous == null) groups[group] = day to location
                }
            }
        }
        findings.add("ตรวจด้วยตนเอง: ใครรู้หลักฐานเมื่อใด บาดแผล สิ่งของ อารมณ์ และเหตุการณ์ที่เชื่อมต่อระหว่าง EP • ระบบยังไม่ยืนยันความหมายหรือภาพจริง")
        return findings.distinct()
    }
}
