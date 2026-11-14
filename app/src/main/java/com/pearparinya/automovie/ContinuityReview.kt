package com.pearparinya.automovie

import org.json.JSONObject

/** Surface observable inconsistencies for human review, without claiming semantic verification. */
internal object ContinuityReview {
    fun review(story: JSONObject): List<String> {
        val findings = mutableListOf<String>()
        val groups = mutableMapOf<String, Pair<String, String>>()
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
                if (scene.optString("start_state").isBlank() || scene.optString("end_state").isBlank()) findings.add("$label: ตรวจสถานะเริ่มต้นและสถานะสิ้นสุด")
                if (group.isNotBlank()) {
                    val previous = groups[group]
                    if (previous != null && day.isNotBlank() && previous.first.isNotBlank() && day != previous.first)
                        findings.add("$label: กลุ่มต่อเนื่อง $group ใช้วันต่างกัน (${previous.first} / $day) โปรดตรวจ")
                    if (previous == null) groups[group] = day to location
                }
            }
        }
        findings.add("ตรวจด้วยตนเอง: ใครรู้หลักฐานเมื่อใด บาดแผล สิ่งของ อารมณ์ และเหตุการณ์ที่เชื่อมต่อระหว่าง EP • ระบบยังไม่ยืนยันความหมายหรือภาพจริง")
        return findings.distinct()
    }
}
