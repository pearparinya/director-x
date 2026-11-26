package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

internal object StoryClock {
    const val instruction = "For each scene write story_time as a 24-hour HH.mm clock followed by a space and the scripted light period, e.g. 16.00 afternoon, 19.00 dusk, 20.00 night. These are examples, not a required sequence. Use local story context for light, including indoor sources. Consecutive shots seconds apart can share a minute. Advance only when the action or an explicit time cut warrants it. Keep the story_day identifier stable until midnight. At midnight advance the story day. Set time_transition to flashback only for a scripted flashback, otherwise normal. Changing location or camera angle alone does not advance the clock."

    private val clock = Regex("""^([01][0-9]|2[0-3])[.:]([0-5][0-9])(?:\s|$)""")

    fun validate(rows: JSONArray, prior: JSONObject?, ep: Int) {
        var previous = prior
        for (i in 0 until rows.length()) {
            val scene = rows.getJSONObject(i)
            val value = scene.optString("story_time").trim()
            val current = clock.find(value)
            val label = "EP $ep ซีน " + scene.optInt("scene")
            require(current != null && value.substring(current.range.last + 1).isNotBlank()) {
                "$label กรุณาระบุเวลาและช่วงแสง เช่น 19.00 พลบค่ำ"
            }
            val before = previous
            if (before != null && before.optString("story_day") == scene.optString("story_day") &&
                scene.optString("time_transition") != "flashback") {
                val earlier = clock.find(before.optString("story_time").trim())
                require(earlier == null || minutes(current) >= minutes(earlier)) {
                    "$label เวลาย้อนจาก " + before.optString("story_time") + " เป็น " + value
                }
            }
            scene.put("story_time", current.groupValues[1] + "." + current.groupValues[2] + " " +
                value.substring(current.range.last + 1).trim())
            previous = scene
        }
    }

    fun hasClock(value: String) = clock.containsMatchIn(value.trim())
    private fun minutes(match: MatchResult) = match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
}
