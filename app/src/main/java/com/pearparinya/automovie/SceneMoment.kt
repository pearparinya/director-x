package com.pearparinya.automovie

import org.json.JSONObject

internal data class SceneMoment(val time: String, val previousEnd: String = "") {
    companion object {
        const val unknown = "ยังไม่กำหนด"

        fun resolve(story: JSONObject?, ep: Int, target: JSONObject): SceneMoment {
            val episodes = story?.optJSONArray("episodes")
            var previous: JSONObject? = null
            var lastTime = unknown
            for (e in 0 until (episodes?.length() ?: 0)) {
                val episode = episodes?.optJSONObject(e) ?: continue
                if (episode.optInt("ep") != ep) continue
                val rows = episode.optJSONArray("scenes") ?: break
                for (i in 0 until rows.length()) {
                    val scene = rows.optJSONObject(i) ?: continue
                    val explicit = timeIn(scene)
                    val group = scene.optString("continuity_group").trim()
                    val continuous = previous != null && group.isNotEmpty() &&
                        group == previous.optString("continuity_group").trim() &&
                        scene.optString("story_day") == previous.optString("story_day") &&
                        (explicit.isEmpty() || explicit == lastTime)
                    val time = explicit.ifBlank { if (continuous) lastTime else unknown }
                    if (scene.optInt("scene") == target.optInt("scene")) {
                        return SceneMoment(time, if (continuous) previous?.optString("end_state").orEmpty() else "")
                    }
                    previous = scene
                    lastTime = time
                }
            }
            return SceneMoment(timeIn(target).ifBlank { unknown })
        }

        private fun timeIn(scene: JSONObject): String {
            val recorded = scene.optString("story_time").trim()
            if (StoryClock.hasClock(recorded)) return recorded
            val start = scene.optString("start_state").trim()
            if (start.startsWith("เช้าวันรุ่งขึ้น") || start.startsWith("the next morning", true)) return "morning"
            return scene.optString("story_time").trim().takeUnless { it == unknown }.orEmpty()
        }
    }
}
