package com.pearparinya.automovie

import org.json.JSONObject

internal object SoundDesign {
    const val planning = "SOUND DESIGN: Every scene must contain sound_design with three short English strings: ambience (realistic location room tone), sfx (specific visible action, material, timing and volume, or none), music (a subtle non-vocal cue only when the scene benefits, or none). For comedy, add a brief non-vocal pop/pluck/boing only on a written comic payoff when appropriate; no repeated gag sounds. Keep real synchronized foley primary. No new events, human voices, vocal laughs, laugh track, ad-libs or dialogue in sound fields. Never cover the assigned Thai line; duck ambience, foley and music beneath speech. A silent scene means no dialogue/narration, not necessarily no environmental audio."
    fun validate(scene: JSONObject) {
        val sound=requireNotNull(scene.optJSONObject("sound_design")) { "ขาดแผนเสียงของซีน" }
        listOf("ambience","sfx","music").forEach { require(sound.opt(it) is String && sound.getString(it).isNotBlank()) { "แผนเสียงขาด $it" } }
    }
    fun notes(scene: JSONObject): String = scene.optJSONObject("sound_design")?.let {
        "Ambience: ${it.optString("ambience","none")}; SFX: ${it.optString("sfx","none")}; Music: ${it.optString("music","none")}."
    }.orEmpty()
    fun prompt(notes: String): String = "SOUND: "+notes.ifBlank { "Quiet realistic ambience for this location; synchronized soft foley only for the stated visible action; no music or cartoon effects unless explicitly planned." }+
        " Natural distance, room acoustics and material texture; keep audio synchronized. Effects/music remain below speech and duck during the Thai line. No added voices, vocal laughs, laugh track or ad-libs. Non-speaking characters make no vocal sounds."
}
