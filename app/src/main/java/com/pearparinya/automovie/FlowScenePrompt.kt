package com.pearparinya.automovie

internal data class FlowPerson(val name: String, val appearance: String, val position: String, val outfit: String,val performance: String="")
internal data class FlowSceneSpec(
    val cast: List<FlowPerson>, val speaker: String, val dialogue: String, val narration: String,
    val action: String, val location: String, val startState: String, val endState: String,
    val duration: Int, val aspect: String, val sound: String = "", val offscreen: List<String> = emptyList()
)

/** Build from current structured facts, never append stale dialogue from an imported prompt. */
internal object FlowScenePrompt {
    private fun identity(name: String) = StoryIdentity.keyFor(name.trim()) ?: name.trim()
    private fun speech(value: String) = value.trim().takeUnless { it in listOf("", "ไม่มี", "ไม่มีเสียงบรรยาย", "none", "None") }.orEmpty()
    fun issues(s: FlowSceneSpec): List<String> = buildList {
        if(s.aspect!=LandscapeProduction.ASPECT) add("คำสั่งวิดีโอต้องเป็นแนวตั้ง 9:16")
        if (s.cast.isEmpty() && speech(s.dialogue).isNotEmpty()) add("ระบุตัวละครที่ปรากฏในภาพฉากนี้")
        if (s.cast.any { it.name.isBlank() }) add("ตัวละครต้องมีชื่อ")
        if (s.cast.map { identity(it.name) }.distinct().size != s.cast.size) add("ชื่อตัวละครซ้ำหรือเป็นชื่อเรียกคนเดียวกัน")
        s.cast.forEach {
            if (it.appearance.isBlank() || it.position.isBlank()) add("ตรวจลักษณะและตำแหน่งในภาพของ ${it.name}")
        }
        if (s.cast.map { it.appearance.trim() to it.position.trim() }.distinct().size != s.cast.size) add("ลักษณะและตำแหน่งต้องแยกตัวละครออกจากกันได้")
        if (speech(s.dialogue).isNotEmpty() && (s.cast.count { identity(it.name) == identity(s.speaker) } + s.offscreen.count { identity(it)==identity(s.speaker) }) != 1) add("เลือกผู้พูดหนึ่งคนจากตัวละครในภาพหรือรายชื่อคนนอกภาพ")
        if (speech(s.dialogue).isEmpty() && speech(s.speaker).isNotEmpty()) add("ระบุบทพูดของผู้พูด หรือเว้นผู้พูดว่างสำหรับฉากเงียบ")
        if (speech(s.dialogue).isNotEmpty() && speech(s.narration).isNotEmpty()) add("แยกบทพูดและเสียงบรรยายเป็นคนละฉาก เพื่อไม่ให้เสียงทับกัน")
    }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    /** A prompt, not a Flow API schema. The image must still be attached in Flow. */
    fun build(s: FlowSceneSpec, id: String = "scene", camera: String = "Slow, gentle pan."): String {
        val problems = issues(s)
        if (problems.isNotEmpty()) return "ยังไม่พร้อมส่ง Flow • ตรวจภาพและกำหนดตัวละครก่อน:\n" + problems.joinToString("\n")
        val dialogue = speech(s.dialogue)
        val narration = speech(s.narration)
        val offscreen = dialogue.isNotEmpty() && s.offscreen.any { identity(it) == identity(s.speaker) }
        val line = dialogue.ifEmpty { narration }
        fun label(name: String): String = "[$name]"
        val visibleSpeaker = s.cast.firstOrNull { identity(it.name) == identity(s.speaker) }?.name ?: s.speaker
        val voice = when {
            dialogue.isNotEmpty() && !offscreen -> "${label(visibleSpeaker)} speaks naturally; the other visible characters remain silent."
            else -> "Visible characters remain silent with mouths closed."
        }
        val speaker = if (narration.isNotEmpty()) "Off-screen Male Narrator" else if (offscreen) "Off-screen ${s.speaker}" else label(visibleSpeaker)
        val tag = if (narration.isNotEmpty() || offscreen) "voiceover" else "dialogue"
        var action = s.action.trim().trimEnd('.', '!', '?').ifBlank { "The scene remains still" }
        // Only replace aliases with an established identity; never infer a new person's name from position or gender.
        val aliases = s.cast.flatMap { person ->
            val known = when (identity(person.name)) {
                "gawin" -> listOf("กวิน", "Gawin", "Kawin", "The Man")
                "rinlada" -> listOf("รินรดา", "รินลดา", "Rinlada", "Rinrada", "Woman 1")
                "mind" -> listOf("เมย์", "มายด์", "May", "Mind", "Woman 2")
                else -> emptyList()
            }
            (known + person.name).distinct().map { it to label(person.name) }
        }.sortedByDescending { it.first.length }
        aliases.forEach { (alias, replacement) ->
            val boundary = if (alias.any { it in 'ก'..'๙' }) "(?<![ก-๙])${Regex.escape(alias)}(?![ก-๙])" else "(?<![A-Za-z0-9])${Regex.escape(alias)}(?![A-Za-z0-9])"
            action = Regex(boundary, RegexOption.IGNORE_CASE).replace(action) { replacement }
        }
        val sound = s.sound.trim().ifEmpty { "Quiet natural ambience." }.replace(Regex("\\b(?:indoor )?murmurs\\b", RegexOption.IGNORE_CASE), "quiet room tone")
        return buildString {
            append("<scene id=\"${xml(id)}\">\n  <aspect_ratio>9:16</aspect_ratio>\n  <duration>8</duration>\n  <reference_image>attached_image</reference_image>\n")
            if (s.cast.isNotEmpty()) {
                append("  <characters>\n")
                s.cast.forEach { append("    <character>${xml(label(it.name))}</character>\n") }
                append("  </characters>\n")
            }
            val mapping = s.cast.joinToString(" ") { "${label(it.name)}: ${it.appearance}, ${it.position} in the first frame." }
            append("  <prompt>Masterpiece, 8k resolution, cinematic shot. One continuous 8-second vertical shot. ${xml(mapping)} ${xml(action)}. Preserve the characters, their positions, clothing and props from the first frame. Keep held objects together and in contact with the hands. ${xml(voice)} Camera: ${xml(camera)}</prompt>\n")
            append("  <lighting>5500K daylight white balance, pure bright studio lighting, no yellow light</lighting>\n")
            append("  <typography_safe_zone>SLIGHTLY BELOW CENTER</typography_safe_zone>\n")
            if (line.isNotEmpty()) {
                append("  <$tag>\n    <speaker>${xml(speaker)}</speaker>\n    <text>${xml(line)}</text>\n    <language>th-TH</language>\n    <subtitles>false</subtitles>\n  </$tag>\n")
            }
            append("  <sound_effects>\n    <ambient>${xml(sound)}${if (line.isNotEmpty()) " Quieter than the speech." else ""}</ambient>\n  </sound_effects>\n</scene>")
        }
    }
}
