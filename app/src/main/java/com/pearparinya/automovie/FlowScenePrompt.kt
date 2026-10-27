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
    fun build(s: FlowSceneSpec): String {
        val problems = issues(s)
        if (problems.isNotEmpty()) return "ยังไม่พร้อมส่ง Flow • ตรวจภาพและกำหนดตัวละครก่อน:\n" + problems.joinToString("\n")
        val dialogue = speech(s.dialogue)
        val narrator = speech(s.narration)
        val talking = s.cast.firstOrNull { identity(it.name) == identity(s.speaker) }
        fun label(p: FlowPerson) = "${p.name} [${p.appearance}; screen position: ${p.position}; wardrobe: ${p.outfit.ifBlank { "exactly as in the supplied image" }}]"
        return buildString {
            append("Flow / Veo 3.1 Lite: ${s.duration} seconds, ${s.aspect} vertical portrait, 1080 x 1920, locked-off static camera. Realistic Thai drama. One continuous shot.\n")
            append("Use the supplied scene image as the first frame. Match the following confirmed people to that image; preserve their faces, hair, outfits, screen positions, room and props.\nCHARACTER MAP:\n")
            s.cast.forEach { append("- ${label(it)}\n") }
            if (s.startState.isNotBlank()) append("START STATE: ${s.startState}\n")
            append("LOCATION: ${s.location}\nONE ACTION: ${s.action}\n")
            append("Realistic acting: follow each person's stated intent, gaze and genre; preserve anatomy and the reviewed frame. PERFORMANCE BEATS: ${if(s.duration==8) "0-2s receive the cue; 2-6s perform the single stated action with assigned speech; 6-8s small silent response and held end-state" else "receive the cue, perform the single stated action, then hold a small response"}. Fit all beats naturally inside ${s.duration} seconds. Beat boundaries are approximate: finishing the exact assigned line naturally takes priority; never speed up, truncate or add words to force a beat. Performance notes never authorize extra voices; the audio rules below control all speech.\n")
            s.cast.forEach { person -> append("INDIVIDUAL PERFORMANCE — ${person.name}: ${person.performance.ifBlank { "use the stated action and start/end emotional context; restrained, distinct reactions only" }}\n") }
            if (dialogue.isNotEmpty() && talking != null) {
                append("ONLY SPEAKER: ${label(talking)}. Only this person's lips move for the following Thai dialogue, said once: “$dialogue”.\n")
                val listeners = s.cast.filter { identity(it.name) != identity(talking.name) }
                listeners.forEach { append("SILENT LISTENER: ${label(it)}. Mouth stays closed; react only through eyes, expression and small gestures. No speech or lip-sync.\n") }
                append("One voice only, no shared or overlapping delivery. Finish naturally within ${s.duration} seconds. No narrator.\n")
            } else if(dialogue.isNotEmpty() && s.speaker in s.offscreen) {
                append("OFF-SCREEN CHARACTER SPEAKER ONLY: ${s.speaker}. Exact Thai dialogue once: “$dialogue”. Keep this character outside the frame. All visible mouths stay closed; no lip-sync. No narrator or additional voices.\n")
            } else if (narrator.isNotEmpty()) {
                append("OFF-SCREEN MALE NARRATOR ONLY, in Thai: “$narrator”. All on-screen mouths stay closed; no character lip-syncs the narration.\n")
            } else append("Silent scene: no dialogue or narration. All on-screen mouths stay closed.\n")
            append("${SoundDesign.prompt(s.sound)}\n")
            if (s.endState.isNotBlank()) append("END STATE: ${s.endState}. Hold this state briefly for the next scene.\n")
            append("No cuts, camera movement, captions or added text. Keep document text indistinct; convey its meaning through speech.")
        }
    }
}
