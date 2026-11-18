package com.pearparinya.automovie

import org.json.JSONObject

/**
 * Deterministic anatomical-side contract. An approved cover must be annotated
 * with left/right by a human or trusted image inspection before it can be locked.
 * Never infer an anatomical wrist from screen coordinates.
 */
internal object AccessoryContinuityContract {
    private val allowed = setOf("left", "right")
    fun lockedSide(character: JSONObject): String {
        val side = character.optString("watch_side").trim().lowercase()
        require(side.isEmpty() || side in allowed) { "watch_side must be left or right" }
        return side
    }
    fun sceneInstructions(characters: List<JSONObject>): String = characters.mapNotNull { actor ->
        val side = lockedSide(actor)
        if (side.isEmpty()) null else {
            val name = actor.optString("name").trim()
            require(name.isNotEmpty()) { "Locked watch side requires character name" }
            val wrist = if (side=="right") "RIGHT" else "LEFT"
            "LOCKED WATCH: $name wears the watch on THEIR OWN $wrist wrist, not the viewer's $wrist. This anatomical wrist must not change with camera angle, body turn, mirror or pose. Do not generate a watch on the other wrist."
        }
    }.joinToString("\n")
    fun conflicts(characters: List<JSONObject>): List<String> = characters.mapNotNull { actor ->
        val side=lockedSide(actor)
        val outfit=actor.optString("outfit")
        if(side.isEmpty() || !Regex("(?i)watch|wristwatch|นาฬิกา").containsMatchIn(outfit)) null
        else {
            val opposite=if(side=="right") Regex("(?i)(left\\s*wrist|ข้อมือซ้าย|ข้างซ้าย)") else Regex("(?i)(right\\s*wrist|ข้อมือขวา|ข้างขวา)")
            if(opposite.containsMatchIn(outfit)) "${actor.optString("name")}: watch_side=$side conflicts with outfit; review before generation" else null
        }
    }
}
