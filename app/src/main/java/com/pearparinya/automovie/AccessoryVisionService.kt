package com.pearparinya.automovie

import org.json.JSONObject
import java.io.File

/**
 * Uses the existing optional OpenAI Vision client to inspect an actual saved image.
 * No automatic acceptance when the provider fails or returns an uncertain observation.
 * Call from a background thread only; user approval remains necessary for REVIEW.
 */
internal class AccessoryVisionService(private val client: OpenAiVisionClient) {
    fun inspect(characterName: String, approvedSide: String, sceneImage: File): AccessoryVisionReview.Result {
        require(characterName.isNotBlank())
        require(approvedSide in setOf("left","right"))
        val schema=JSONObject()
            .put("character_name","exact character name or empty if not identifiable")
            .put("observed_side","left|right|none|uncertain")
            .put("watch_visible",false)
            .put("mirrored_or_ambiguous",true)
            .put("reason","brief visual evidence")
        return try {
            val result=client.generate(
                AccessoryVisionReview.prompt(characterName,approvedSide),
                schema,
                listOf(characterName to sceneImage)
            )
            AccessoryVisionReview.evaluate(characterName,approvedSide,result)
        } catch(e: Exception) {
            AccessoryVisionReview.Result(AccessoryVisionReview.Decision.REVIEW,
                "Vision unavailable or invalid result: "+(e.message ?: "unknown error"))
        }
    }
}
