package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Human approval bound to exact scene/reference bytes, never an automated likeness score. */
internal object SceneFrameReview {
    private val fields=listOf("scene","ep","characters","action","flow_action","location","start_state","end_state","speaker","dialogue","narration","image_prompt","flow_prompt","framing","lighting","story_time","story_day","activity","wardrobe_event","props","continuity_group")
    private fun canonical(value: Any?): String = when(value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix="{",postfix="}") { JSONObject.quote(it)+":"+canonical(value.opt(it)) }
        is JSONArray -> (0 until value.length()).joinToString(prefix="[",postfix="]") { canonical(value.opt(it)) }
        else -> JSONObject.valueToString(value)
    }
    fun fingerprint(scene: JSONObject, image: File, masters: List<File>): String? {
        if(!image.isFile || image.length()==0L || masters.isEmpty() || masters.any { !it.isFile || it.length()==0L }) return null
        return runCatching {
            val facts=JSONObject(); fields.forEach { facts.put(it,scene.opt(it) ?: JSONObject.NULL) }
            val input=canonical(facts)+"|"+EpisodeMedia.digest(image)+"|"+masters.joinToString("|") { EpisodeMedia.digest(it) }
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
    fun current(scene: JSONObject, image: File, masters: List<File>): Boolean {
        val actual=fingerprint(scene,image,masters) ?: return false
        return scene.optString("approved_frame_sha256")==actual
    }
    fun approve(scene: JSONObject,image: File,masters: List<File>) {
        scene.put("approved_frame_sha256",requireNotNull(fingerprint(scene,image,masters)) { "ขาดภาพฉากหรือ MASTER" })
    }
}
