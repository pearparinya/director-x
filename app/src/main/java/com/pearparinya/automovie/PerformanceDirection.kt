package com.pearparinya.automovie

import org.json.JSONObject

/** Per-character playable choices; never invent new dialogue, plot events or reference anatomy. */
internal object PerformanceDirection {
    val fields=listOf("emotion","objective","subtext","gaze","body_language","breath","voice","reaction","emotional_arc","thai_direction")
    const val guide="MATURE THAI DRAMA PERFORMANCE: Play a clear personal objective and obstacle beneath the line. Layer an outward emotion with a concealed conflicting feeling (e.g. dignity over hurt, reassurance over fear, charm over insecurity) only when supported by this scene. Give every visible character a distinct objective, gaze target, posture and response; listeners remain active and silent. Use precise, restrained micro-expressions, a held gaze, a brief eye withdrawal, jaw release, fingertip pressure or a controlled shift of weight, not identical exaggerated faces. Keep reference face/body anatomy stable. Preserve hands, props, positions and the reviewed start frame. Do not assign guilt, innocence or villainy from gender/appearance; each adult retains agency and complexity. Carry the previous scene's emotional end state forward. Avoid automatic tears, shouting, smirking or touching in every shot. Deep acting must support the written event, never add a new revelation, line, kiss, assault or plot event."
    const val planning="""PERFORMANCE DATA REQUIRED FOR EVERY ON-SCREEN CHARACTER: add a performance object with emotion, objective, subtext, gaze, body_language, breath, voice, reaction, emotional_arc and thai_direction. The first nine fields are concise English directing notes, each preferably <=8 words; refer to canonical cast names only. emotional_arc describes a small credible start -> turn -> finish inside this shot, not three different major actions. thai_direction is a concise natural Thai directing sentence summarizing the character's emotional contradiction, eye/body choice and vocal or silent response. Voice must be exactly silent for anyone other than the current dialogue speaker, including all people in VO/silent scenes. Only the named dialogue speaker gets a specific controlled Thai vocal delivery. Preserve each actor's established voice identity while varying emotional intensity and pace. Do not add audible gasps, muttering, ad-libs, extra dialogue or overlapping voices. Breathing for silent characters is a subtle visible performance cue only.
Write drama for psychologically mature adults: relationship history, trust, shame, jealousy, conflicting loyalties, guilt, restrained anger, vulnerability, pride and regained dignity can drive subtext when the plot supports them. The husband may conceal fear with composure, the wife may protect dignity while hurt, and the other woman may conceal insecurity with confidence; these are optional choices, never fixed moral stereotypes. Give supporting characters equally specific motives. Vary intensity and release across scenes, and preserve each character's emotional progression across EP cuts. Keep new spoken lines compact enough for roughly four seconds of natural Thai delivery, leaving room for meaningful silence. In an 8-second scene use 0-2s receive the cue, 2-6s one principal playable action with the assigned line, 6-8s a small response and held end-state. Do not cram in multiple confrontations or repeat substantial speech. A still image freezes one meaningful instant; it does not show a sequence or collage."""
    fun text(person: JSONObject): String = person.optJSONObject("performance")?.let { p ->
        fields.filter { it!="thai_direction" }.mapNotNull { field -> p.optString(field).takeIf { it.isNotBlank() }?.let { "$field: $it" } }.joinToString("; ")
    }.orEmpty()
    fun scene(scene: JSONObject): String = FlowReferencePrompt.people(scene).joinToString("\n") { p ->
        "${p.getString("name")} PERFORMANCE: "+text(p).ifBlank { "derive a restrained individual response from the existing action and start/end states; do not invent a new event or line" }
    }
    fun continuity(scene: JSONObject): String = FlowReferencePrompt.people(scene).mapNotNull { p ->
        p.optJSONObject("performance")?.let { acting -> "${p.optString("name")}: arc=${acting.optString("emotional_arc").take(160)}; goal=${acting.optString("objective").take(100)}" }
    }.joinToString(" | ")
    fun script(scene: JSONObject): String = FlowReferencePrompt.people(scene).mapNotNull { p ->
        p.optJSONObject("performance")?.optString("thai_direction")?.takeIf { it.isNotBlank() }?.let { "กำกับ ${p.getString("name")}: $it" }
    }.joinToString("\n")
    fun validate(scene: JSONObject) {
        val spoken=scene.optString("dialogue").trim().lowercase() !in setOf("","none","n/a","ไม่มี")
        FlowReferencePrompt.people(scene).forEach { person ->
            val p=requireNotNull(person.optJSONObject("performance")) { "ขาดข้อมูลการแสดงของ ${person.optString("name")}" }
            fields.forEach { require(p.opt(it) is String && p.optString(it).isNotBlank()) { "${person.optString("name")} ขาดการแสดง $it" } }
            require(Regex("[ก-๙]").containsMatchIn(p.getString("thai_direction"))) { "กำกับการแสดงภาษาไทยไม่ครบ" }
            val speaker=person.optString("name")==scene.optString("speaker") || StoryIdentity.keyFor(scene.optString("speaker"))?.let { FlowCast.id(person)==it }==true
            if(!spoken || !speaker) require(p.getString("voice")=="silent") { "${person.optString("name")} เป็นผู้ฟัง / ฉาก VO ต้องกำหนด voice=silent" }
            else require(p.getString("voice")!="silent") { "ผู้พูดต้องมีแนวทางน้ำเสียง" }
        }
    }
}
