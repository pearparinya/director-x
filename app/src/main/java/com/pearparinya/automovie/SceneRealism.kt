package com.pearparinya.automovie

import org.json.JSONObject

internal object SceneRealism {
    const val direction = "Match the story time, available light and actual visibility. Carry the same wearer's shoes, belt, jewellery and accessory side across the same story day, including off-camera items. Show a change only when the story establishes it."
    const val planning = "A character carrying a flashlight is not automatically using it. Establish holder, on/off state and target when relevant; motivate switching on or stowing it through visible action. A daylight search may need a beam inside a dark recess. Keep sounds, reactions and discoveries in causal order; reveal only what the character can perceive."
    private val flashlight = Regex("ไฟฉาย|\\b(flashlights?|torches|torch)\\b", RegexOption.IGNORE_CASE)

    fun frame(scene: JSONObject): String {
        val time = scene.optString("story_time").trim()
        val start = scene.optString("start_state").trim()
        val evidence = listOf(start, scene.optString("action"), scene.optString("props"), scene.optString("image_prompt")).joinToString(" ")
        return buildString {
            append(direction)
            if (time.isNotEmpty()) append(" Story time: ").append(time).append(".")
            if (start.isNotEmpty()) append(" First frame: ").append(start).append(".")
            if (flashlight.containsMatchIn(evidence)) append(" Follow the flashlight state at the start of this shot. Carrying alone implies no visible beam. If the script calls for light, aim at the stated target; do not switch on early or invent a second light.")
        }
    }
}
