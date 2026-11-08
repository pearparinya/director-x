package com.pearparinya.automovie

internal data class FlowPerson(val name: String, val appearance: String, val position: String, val outfit: String,val performance: String="", val englishName: String="")
internal data class FlowDialogueTurn(val speaker: String, val text: String)
internal data class FlowSceneSpec(
    val cast: List<FlowPerson>, val speaker: String, val dialogue: String, val narration: String,
    val action: String, val location: String, val startState: String, val endState: String,
    val duration: Int, val aspect: String, val sound: String = "", val offscreen: List<String> = emptyList(),
    val turns: List<FlowDialogueTurn> = emptyList()
) {
    // Preserve approvals made by older versions when no ordered turns exist.
    override fun toString(): String {
        val original="FlowSceneSpec(cast=$cast, speaker=$speaker, dialogue=$dialogue, narration=$narration, action=$action, location=$location, startState=$startState, endState=$endState, duration=$duration, aspect=$aspect, sound=$sound, offscreen=$offscreen)"
        return if(turns.isEmpty()) original else original.dropLast(1)+", turns=$turns)"
    }
}

internal object ReadingPropPolicy {
    private val paper = Regex("เอกสาร|จดหมาย|กระดาษ|หนังสือ|สัญญา|document|letter|paper|contract|book", RegexOption.IGNORE_CASE)
    const val instruction = "When reading, the written side faces the reader, top edge up; when viewed from the front, show the blank reverse to camera. Keep the sheet and hands physically consistent; no readable or mirrored text."
    fun forScene(vararg parts: String) = if (parts.any { paper.containsMatchIn(it) }) instruction else ""
}

/** Build from current structured facts, never append stale dialogue from an imported prompt. */
internal object FlowScenePrompt {
    private fun identity(name: String) = StoryIdentity.keyFor(name.trim()) ?: name.trim()
    private fun refName(name: String): String = when (identity(name)) {
        "gawin" -> "Gawin"
        "rinlada" -> "Rinrada"
        "mind" -> "May"
        "พัชรินทร์" -> "Patcharin"
        "นที" -> "Natee"
        "ปิยวรรณ" -> "Piyawan"
        else -> "Character_" + java.lang.Integer.toUnsignedString(name.trim().hashCode(), 16).uppercase()
    }
    private fun speech(value: String) = value.trim().takeUnless { it in listOf("", "ไม่มี", "ไม่มีเสียงบรรยาย", "none", "None") }.orEmpty()
    fun issues(s: FlowSceneSpec): List<String> = buildList {
        if(s.aspect!=LandscapeProduction.ASPECT) add("คำสั่งวิดีโอต้องเป็นแนวตั้ง 9:16")
        unknownActionNames(s).forEach { add("พบชื่อภาษาอังกฤษ '$it' ที่ยังจับคู่ตัวละครไม่ได้ ใน flow_action ให้แก้เป็นชื่อตัวละครภาษาไทยตามรายชื่อในซีน แล้วเปิดคำสั่งใหม่") }
        if (s.cast.isEmpty() && speech(s.dialogue).isNotEmpty()) add("ระบุตัวละครที่ปรากฏในภาพฉากนี้")
        if (s.cast.map { it.englishName.trim().lowercase() }.filter { it.isNotEmpty() }.let { it.distinct().size!=it.size }) add("ชื่ออังกฤษของตัวละครซ้ำ กรุณาตรวจรายชื่อ")
        if (s.cast.any { it.name.isBlank() }) add("ตัวละครต้องมีชื่อ")
        if (s.cast.map { identity(it.name) }.distinct().size != s.cast.size || s.cast.map { refName(it.name) }.distinct().size != s.cast.size) add("ชื่อตัวละครซ้ำหรือรหัสอ้างอิงชนกัน")
        s.cast.forEach {
            if (it.appearance.isBlank() || it.position.isBlank()) add("ตรวจลักษณะและตำแหน่งในภาพของ ${it.name}")
        }
        if (s.cast.map { it.appearance.trim() to it.position.trim() }.distinct().size != s.cast.size) add("ลักษณะและตำแหน่งต้องแยกตัวละครออกจากกันได้")
        if(s.turns.isNotEmpty()) {
            if(speech(s.dialogue).isNotEmpty() || speech(s.speaker).isNotEmpty() || speech(s.narration).isNotEmpty()) add("บทสนทนาหลายคนต้องไม่ใช้ร่วมกับบทเดี่ยวหรือบรรยาย")
            s.turns.forEachIndexed { index, turn ->
                if(speech(turn.text).isEmpty() || speech(turn.speaker).isEmpty()) add("บทสนทนาลำดับ ${index+1} ต้องระบุผู้พูดและข้อความ")
                else if(s.cast.count { identity(it.name)==identity(turn.speaker) } + s.offscreen.count { identity(it)==identity(turn.speaker) } != 1) add("ผู้พูดลำดับ ${index+1} ต้องอยู่ในรายชื่อคนในภาพหรือคนนอกภาพ")
            }
        }
        if (speech(s.dialogue).isNotEmpty() && (s.cast.count { identity(it.name) == identity(s.speaker) } + s.offscreen.count { identity(it)==identity(s.speaker) }) != 1) add("เลือกผู้พูดหนึ่งคนจากตัวละครในภาพหรือรายชื่อคนนอกภาพ")
        if (speech(s.dialogue).isEmpty() && speech(s.speaker).isNotEmpty()) add("ระบุบทพูดของผู้พูด หรือเว้นผู้พูดว่างสำหรับฉากเงียบ")
        if (speech(s.dialogue).isNotEmpty() && speech(s.narration).isNotEmpty()) add("แยกบทพูดและเสียงบรรยายเป็นคนละฉาก เพื่อไม่ให้เสียงทับกัน")
    }
    // Unknown subject names cannot safely be guessed from character order or appearance.
    fun unknownActionNames(s: FlowSceneSpec): List<String> {
        if(s.cast.isEmpty()) return emptyList()
        val known=s.cast.flatMap { p ->
            val aliases=when(identity(p.name)) {
                "gawin" -> listOf("gawin", "kawin")
                "rinlada" -> listOf("rinlada", "rinrada")
                "mind" -> listOf("mind", "may")
                "พัชรินทร์" -> listOf("patcharin")
                "นที" -> listOf("natee")
                "ปิยวรรณ" -> listOf("piyawan")
                else -> emptyList()
            }
            aliases + listOf(p.name,refName(p.name),p.englishName)
        }.map { it.lowercase() }.toSet()
        val generic=setOf("i","me","my","mine","myself","you","your","yours","yourself","yourselves","he","him","his","himself","she","her","hers","herself","it","its","itself","we","us","our","ours","ourselves","they","them","their","theirs","themselves","who","whom","whose","which","that","what","whoever","whatever","it","this","there","man","woman","person","character","people","someone","everyone","camera","phone","door","screen","light","wind","paper","music","sound","document","folder","manager","boss","coworker","colleague","worker","narrator", "and", "or", "then", "also", "but", "with")
        val pattern=Regex("(?<![\\w\\]])\\b([A-Za-z][A-Za-z0-9_]*)\\s+(?:holds|looks|watches|stares|speaks|says|asks|replies|walks|stands|sits|leans|smiles|reacts|turns|opens|closes|reads|inspects|observes|hands|places|shows|checks|nods|waves|slides|reaches|takes|points)\\b",RegexOption.IGNORE_CASE)
        return pattern.findAll(s.action).filterNot { match ->
            // "open hands" is an adjective plus noun, not a person performing an action.
            match.value.equals("open hands", ignoreCase=true)
        }.map { it.groupValues[1] }.filter { it.lowercase() !in known && it.lowercase() !in generic }.distinct().toList()
    }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    /** A prompt, not a Flow API schema. The image must still be attached in Flow. */
    fun build(s: FlowSceneSpec, id: String = "scene", camera: String = "Slow, gentle pan."): String {
        val problems = issues(s)
        if (problems.isNotEmpty()) return "ยังไม่พร้อมส่ง Flow • ตรวจภาพและกำหนดตัวละครก่อน:\n" + problems.joinToString("\n")
        if(s.turns.isNotEmpty()) return FlowVoiceXml.build(s,camera)
        val dialogue = speech(s.dialogue)
        val narration = speech(s.narration)
        val offscreen = dialogue.isNotEmpty() && s.offscreen.any { identity(it) == identity(s.speaker) }
        val line = dialogue.ifEmpty { narration }
        fun label(name: String): String = "[${name.trim()}]"
        val visiblePerson = s.cast.firstOrNull { identity(it.name) == identity(s.speaker) }
        val visibleSpeaker = visiblePerson?.name ?: s.speaker
        fun descriptor(p: FlowPerson): String {
            val position=p.position.trim().replace(Regex("\\s+")," ")
            val appearance=p.appearance.trim().replace(Regex("\\s+")," ")
            // Profiles sometimes contain long MASTER instructions, rather than a visual description.
            val cue=appearance.takeIf { it.length<=160 && !it.contains("MASTER",ignoreCase=true) && !it.contains("Fictional character",ignoreCase=true) }
            return listOfNotNull(position.takeIf { it.isNotBlank() },cue).joinToString(", ")
        }
        fun bound(p: FlowPerson) = "${label(p.name)} — ${descriptor(p)}"
        val listeners = s.cast.filter { visiblePerson == null || identity(it.name) != identity(visiblePerson.name) }
        val voice = when {
            dialogue.isNotEmpty() && !offscreen && visiblePerson != null ->
                "ONLY ${bound(visiblePerson)} speaks the Thai dialogue with natural mouth movement. " +
                listeners.joinToString(" ") { "${bound(it)} must NOT speak. Keep their lips closed and still throughout. They may blink, move their head, hands and body, and react naturally without making any vocal sound. Do not animate their mouth or add speech, laughter, gasps, humming or ad-libs." }
            else -> "Visible characters remain silent with mouths closed. They may blink, gesture and move naturally without making vocal sounds. No visible character speaks, laughs, gasps, hums or adds ad-libs."
        }
        val speaker = if (narration.isNotEmpty()) "Off-screen Male Narrator" else if (offscreen) "Off-screen ${label(s.speaker)}" else label(visibleSpeaker)
        val tag = if (narration.isNotEmpty() || offscreen) "voiceover" else "dialogue"
        var action = s.action.trim().trimEnd('.', '!', '?').ifBlank { "The scene remains still" }
        // Only replace aliases with an established identity; never infer a new person's name from position or gender.
        val aliases = s.cast.flatMap { person ->
            val known = when (identity(person.name)) {
                "gawin" -> listOf("กวิน", "Gawin", "Kawin", "The Man")
                "rinlada" -> listOf("รินรดา", "รินลดา", "Rinlada", "Rinrada", "Woman 1")
                "mind" -> listOf("เมย์", "มายด์", "May", "Mind", "Woman 2")
                "พัชรินทร์" -> listOf("พัชรินทร์", "Patcharin")
                "นที" -> listOf("นที", "Natee")
                "ปิยวรรณ" -> listOf("ปิยวรรณ", "Piyawan")
                else -> emptyList()
            }
            (known + person.name + person.englishName).filter { it.isNotBlank() }.distinct().map { it to label(person.name) }
        }.sortedByDescending { it.first.length }
        aliases.forEach { (alias, replacement) ->
            val boundary = if (alias.any { it in 'ก'..'๙' }) "(?<![ก-๙])${Regex.escape(alias)}(?![ก-๙])" else "(?<![A-Za-z0-9])${Regex.escape(alias)}(?![A-Za-z0-9])"
            val options = if (alias == "May") emptySet() else setOf(RegexOption.IGNORE_CASE)
            action = Regex("\\[" + Regex.escape(alias) + "\\]", options).replace(action) { replacement }
            action = Regex("(?<!\\[)$boundary(?!\\])", options).replace(action) { replacement }
        }
        // Do not invite human background speech when the same scene requires no extra voices.
        val accentDirection = if (line.isNotEmpty()) " " + FlowVoiceXml.accent else ""
        val sound = s.sound.trim().ifEmpty { "Quiet natural ambience." }
            .replace(Regex("\\b(?:(?:soft|quiet|low|faint|gentle|indoor)\\s+)*murmurs?\\s+of\\s+(?:(?:art|gallery|room|office)\\s+)*(?:patrons|people|crowds?|visitors|guests)\\b", RegexOption.IGNORE_CASE), "quiet room tone")
            .replace(Regex("\\b(?:indoor )?murmurs\\b", RegexOption.IGNORE_CASE), "quiet room tone")
        return buildString {
            append("<scene id=\"${xml(id)}\">\n  <aspect_ratio>9:16</aspect_ratio>\n  <duration>8</duration>\n  <reference_image>attached_image</reference_image>\n  <lighting>Preserve the lighting and colors of the attached start frame.</lighting>\n  <typography_safe_zone>SLIGHTLY BELOW CENTER</typography_safe_zone>\n")
            if (s.cast.isNotEmpty()) {
                append("  <characters>\n")
                s.cast.forEach { append("    <character>${xml(label(it.name))}</character>\n") }
                append("  </characters>\n")
            }
            val outfits = s.cast.filter { it.outfit.isNotBlank() }.joinToString(" ") { "${label(it.name)} outfit in scene: ${it.outfit.trim()}." }
            append("  <prompt>(9:16 aspect ratio). Photorealistic. Full vertical continuous 8-second shot with no cuts. Use the attached image as the first frame. ${xml(action)}. ${xml(outfits)} ${xml(voice)}${xml(accentDirection)} Preserve the characters, their faces, hair, body proportions, positions, clothing and props from the first frame. Clothing remains securely in place. Use natural eye-level framing focused on expressions and action. Keep held objects together and in contact with the hands. ${xml(ReadingPropPolicy.forScene(s.action, s.startState, s.endState))} Camera: ${xml(camera)}</prompt>\n")

            if (line.isNotEmpty()) {
                append("  <$tag>\n    <speaker>${xml(speaker)}</speaker>\n    <text>${xml(line)}</text>\n    <language>th-TH</language>\n    <subtitles>false</subtitles>\n  </$tag>\n")
            }
            append("  <sound_effects>\n    <ambient>Begin quietly. ${xml(sound)}${if (line.isNotEmpty()) " Quieter than the speech. No additional voices or vocal sounds." else " No voices or vocal sounds."}</ambient>\n  </sound_effects>\n</scene>")
        }
    }
}
