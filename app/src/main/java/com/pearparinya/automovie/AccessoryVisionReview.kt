package com.pearparinya.automovie

import org.json.JSONObject

/**
 * Conservative interpretation of an AI Vision observation. The model's confidence
 * is not a calibrated probability. Occluded, mirrored, ambiguous or mismatched
 * images require human approval and must never be marked as passing.
 */
internal object AccessoryVisionReview {
    enum class Decision { PASS, FAIL, REVIEW }
    data class Result(val decision: Decision, val reason: String)
    fun prompt(characterName: String, expectedSide: String): String {
        require(expectedSide in setOf("left","right"))
        return """
Inspect the supplied ACTUAL scene image, not the text prompt. Find only the named character: $characterName.
Determine which ANATOMICAL wrist wears the watch (character's own left/right), never screen left/right.
Consider body orientation, visible hands and arms, reflection and camera mirroring.
If the watch is hidden, image is ambiguous, character cannot be identified, or both wrists appear to wear watches, mark UNCERTAIN. Never guess.
Independently report the observed anatomical wrist without assuming which side is correct.
Return ONLY JSON with fields character_name, observed_side (left/right/none/uncertain), watch_visible (boolean), mirrored_or_ambiguous (boolean), reason (string). Do not claim 100% certainty.
""".trimIndent()
    }
    fun evaluate(expectedName: String, expectedSide: String, observation: JSONObject): Result {
        require(expectedSide in setOf("left","right"))
        val observed=observation.optString("observed_side").trim().lowercase()
        val name=observation.optString("character_name").trim()
        if(name != expectedName) return Result(Decision.REVIEW,"Vision character identity does not match")
        if(!observation.has("watch_visible") || !observation.has("mirrored_or_ambiguous")) return Result(Decision.REVIEW,"Vision observation is incomplete")
        if(observation.opt("watch_visible") !is Boolean || observation.opt("mirrored_or_ambiguous") !is Boolean) return Result(Decision.REVIEW,"Vision observation has invalid boolean fields")
        if(observation.optBoolean("mirrored_or_ambiguous",true)) return Result(Decision.REVIEW,"Mirrored or ambiguous image")
        if(!observation.optBoolean("watch_visible",false)) return Result(Decision.REVIEW,"Watch is not visible")
        if(observed !in setOf("left","right")) return Result(Decision.REVIEW,"Anatomical wrist cannot be determined")
        return if(observed==expectedSide) Result(Decision.PASS,"Vision reports expected anatomical wrist")
        else Result(Decision.FAIL,"Watch appears on the opposite anatomical wrist")
    }
}
