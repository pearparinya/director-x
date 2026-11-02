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
        val voice = when {
            dialogue.isNotEmpty() && !offscreen -> "${s.speaker} speaks naturally; the other visible characters remain silent."
            else -> "Visible characters remain silent with mouths closed."
        }
        val speaker = if (narration.isNotEmpty()) "Off-screen Male Narrator" else if (offscreen) "Off-screen ${s.speaker}" else s.speaker
        val tag = if (narration.isNotEmpty() || offscreen) "voiceover" else "dialogue"
        val sound = s.sound.trim().ifEmpty { "Quiet natural ambience." }
        return buildString {
            append("<scene id=\"${xml(id)}\">\n  <aspect_ratio>9:16</aspect_ratio>\n  <duration>8</duration>\n  <reference_image>attached_image</reference_image>\n")
            append("  <prompt>Use the attached image as the first frame. One continuous 8-second vertical shot. ${xml(s.action.trim())} Preserve the characters, their positions, clothing and props from the first frame. ${xml(voice)} Camera: ${xml(camera)}</prompt>\n")
            append("  <lighting>Preserve the lighting of the attached image.</lighting>\n")
            append("  <typography_safe_zone>SLIGHTLY BELOW CENTER</typography_safe_zone>\n")
            if (line.isNotEmpty()) {
                append("  <$tag>\n    <speaker>${xml(speaker)}</speaker>\n    <text>${xml(line)}</text>\n    <language>th-TH</language>\n    <subtitles>false</subtitles>\n  </$tag>\n")
            }
            append("  <sound_effects>\n    <ambient>${xml(sound)}${if (line.isNotEmpty()) " Quieter than the speech." else ""}</ambient>\n  </sound_effects>\n</scene>")
        }
    }
}
