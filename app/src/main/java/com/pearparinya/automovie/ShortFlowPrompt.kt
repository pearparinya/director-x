package com.pearparinya.automovie

/** English scene direction with original speech. Never guess or delete untranslated instructions. */
internal object ShortFlowPrompt {
    private val thai=Regex("[\\u0E00-\\u0E7F]")
    private fun xml(v: String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun spoken(v: String)=v.trim().takeUnless { it.isBlank() || it.equals("none",true) || it=="ไม่มี" || it=="ไม่มีเสียงบรรยาย" }.orEmpty()
    private fun aliases(s: FlowSceneSpec, source: String): String {
        var result=source
        s.cast.forEachIndexed { i,p ->
            val names=(listOf(p.name,p.englishName)+when(StoryIdentity.keyFor(p.name)) {
                "gawin" -> listOf("กวิน","Gawin","Kawin")
                "rinlada" -> listOf("รินรดา","รินลดา","Rinlada","Rinrada")
                "mind" -> listOf("เมย์","มายด์","May","Mind")
                else -> emptyList()
            }).filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
            names.forEach { name ->
                val boundary=if(thai.containsMatchIn(name)) "(?<![ก-๙])${Regex.escape(name)}(?![ก-๙])" else "(?<![A-Za-z0-9_])${Regex.escape(name)}(?![A-Za-z0-9_])"
                result=Regex("\\["+Regex.escape(name)+"\\]",RegexOption.IGNORE_CASE).replace(result,"Character ${i+1}")
                result=Regex(boundary,RegexOption.IGNORE_CASE).replace(result,"Character ${i+1}")
            }
        }
        return result
    }
    internal fun labels(s: FlowSceneSpec): List<String> {
        val counts=mutableMapOf<String,Int>()
        return s.cast.map { p ->
            val kind=p.characterType.trim().lowercase()
            val desc=p.appearance.lowercase()
            fun has(pattern: String)=Regex(pattern,RegexOption.IGNORE_CASE).containsMatchIn(desc)
            val category=when {
                kind=="animal" -> "Animal"
                kind in setOf("god", "goddess", "deity") || has("\\b(god|goddess|deity)\\b|เทพ") -> "Deity"
                kind=="demon" || has("\\b(demon|devil)\\b|ปีศาจ") -> "Demon"
                kind=="ghost" || has("\\bghost\\b|ผี") -> "Ghost"
                kind=="fairy" || has("\\bfairy\\b|ภูต") -> "Fairy"
                kind=="spirit" || has("\\bspirit\\b|วิญญาณ") -> "Spirit"
                kind=="creature" -> "Creature"
                p.characterGender.equals("man",true) || (p.characterGender.isBlank() && has("\\b(man|male)\\b")) -> "The Man"
                p.characterGender.equals("woman",true) || (p.characterGender.isBlank() && has("\\b(woman|female)\\b")) -> "Woman"
                else -> "Character"
            }
            val number=(counts[category] ?: 0)+1; counts[category]=number
            "$category $number"
        }
    }
    private fun typed(s: FlowSceneSpec,source: String): String {
        val labels=labels(s)
        val mapped=Regex("(?i)\\[?\\bCharacter\\s+([0-9]+)\\b\\]?").replace(source) { m ->
            val i=(m.groupValues[1].toIntOrNull() ?: 0)-1
            labels.getOrNull(i) ?: m.value
        }
        return Regex("(?i)\\[((?:The Man|Woman|Animal|Creature|Demon|Ghost|Fairy|Spirit|Deity)\\s+[0-9]+)\\]").replace(mapped) { it.groupValues[1] }
    }
    internal fun clothing(p: FlowPerson): String {
        if (p.flowClothing.isNotBlank()) return p.flowClothing.trim()
        val outfit=p.outfit.trim()
        val top=Regex("(?:^|;)\\s*Top\\s*:\\s*([^;]+)",RegexOption.IGNORE_CASE).find(outfit)?.groupValues?.get(1)
        val cue=(top ?: outfit.takeUnless { Regex("(?:^|;)\\s*(Bottom|Shoes|Accessories)\\s*:",RegexOption.IGNORE_CASE).containsMatchIn(it) }.orEmpty()).trim()
        if (!thai.containsMatchIn(cue)) return cue
        // A short identifier, not a translation of the complete wardrobe. Never infer a color.
        val upper=cue.substringBefore("กางเกง").substringBefore("กระโปรง").substringBefore("รองเท้า").substringBefore(";")
        val colors=listOf("กรมท่า" to "navy", "ฟ้า" to "light blue", "น้ำเงิน" to "blue", "ครีม" to "cream", "ขาว" to "white", "ดำ" to "black", "เทา" to "gray", "เขียว" to "green", "แดง" to "red", "น้ำตาล" to "brown", "เบจ" to "beige", "เหลือง" to "yellow", "ม่วง" to "purple", "ส้ม" to "orange", "ชมพู" to "pink")
        val color=colors.firstOrNull { upper.contains(it.first) }?.second
        val type=when { upper.contains("แจ็กเก็ต") || upper.contains("แจ็คเก็ต") -> "jacket"; upper.contains("เสื้อคลุม") -> "coat"; upper.contains("เสื้อเชิ้ต") -> "shirt"; upper.contains("เสื้อยืด") -> "T-shirt"; upper.contains("เสื้อ") -> "top"; upper.contains("ชุดทำงาน") -> "work outfit"; else -> null }
        return if (color!=null && type!=null) "$color $type" else cue
    }
    fun untranslatedFields(s: FlowSceneSpec,camera: String): List<String> = buildList {
        if(thai.containsMatchIn(aliases(s,s.action))) add("การกระทำสำหรับวิดีโอ")
        if(s.cast.any { thai.containsMatchIn(aliases(s,it.position)) }) add("ตำแหน่งตัวละคร")
        if(s.cast.any { thai.containsMatchIn(clothing(it)) }) add("เสื้อผ้าตัวละคร")
        if(thai.containsMatchIn(aliases(s,s.sound))) add("เสียงฉาก")
        if(thai.containsMatchIn(camera)) add("กล้อง")
    }
    internal fun motion(vararg parts: String): String {
        val text=parts.joinToString(" ").lowercase()
        val rules=mutableListOf<String>()
        if(Regex("wind|breeze|ลมพัด|แรงลม").containsMatchIn(text)) rules.add("Visible wind moves hair and fabric naturally")
        if(Regex("storm|พายุ").containsMatchIn(text)) rules.add("Storm movement follows the scene's weather")
        if(Regex("rain|ฝน").containsMatchIn(text)) rules.add("Rain falls and reacts naturally")
        if(Regex("ghost|spirit|วิญญาณ|ผี").containsMatchIn(text)) rules.add("A visible spirit floats naturally if present")
        if(Regex("smoke|ควัน").containsMatchIn(text)) rules.add("Visible smoke drifts naturally")
        if(Regex("fog|mist|หมอก").containsMatchIn(text)) rules.add("Visible fog moves gently")
        return rules.joinToString(". ")
    }
    private fun speechText(text: String): String = text.trim()
    private fun speakerLabel(s: FlowSceneSpec, name: String): String {
        val index=s.cast.indexOfFirst { it.name.trim()==name.trim() || StoryIdentity.keyFor(it.name)?.let { key -> key==StoryIdentity.keyFor(name) }==true }
        return if(index>=0) labels(s)[index] else "Off-screen speaker"
    }
    /** Non-destructive checks for dense or mixed-script multi-speaker scenes. */
    internal fun dialogueWarnings(s: FlowSceneSpec): List<String> = buildList {
        val turns=if(s.turns.isNotEmpty()) s.turns.map { it.speaker to it.text }
            else if(spoken(s.dialogue).isNotEmpty()) listOf(s.speaker to spoken(s.dialogue))
            else emptyList()
        val timing = EightSecondDialoguePlanner.assess(turns.map { EightSecondDialoguePlanner.Turn(it.first, it.second) })
        timing.warning?.let { add(it) }
        if(turns.any { Regex("[\\u0600-\\u06FF]").containsMatchIn(it.second) && thai.containsMatchIn(it.second) })
            add("บทพูดมีอักษรอาหรับปนกับภาษาไทย • ตรวจสอบการออกเสียง โดยไม่เปลี่ยนข้อความต้นฉบับ")
        val names=s.cast.map { it.name.trim() }
        if(turns.any { (name,_) -> name.isNotBlank() && name !in names && speakerLabel(s,name)=="Off-screen speaker" })
            add("พบผู้พูดที่จับคู่กับตัวละครในฉากไม่ได้ • ตรวจสอบชื่อและภาพ MASTER")
    }
    fun voiceOnly(s: FlowSceneSpec): String = when {
        s.turns.isNotEmpty() -> s.turns.joinToString("\n") { "${speakerLabel(s,it.speaker)}: ${speechText(it.text)}" }
        spoken(s.dialogue).isNotEmpty() -> "${speakerLabel(s,s.speaker)}: ${speechText(spoken(s.dialogue))}"
        spoken(s.narration).isNotEmpty() -> "Off-screen narrator: ${speechText(spoken(s.narration))}"
        else -> "No spoken dialogue."
    }
    fun plain(s: FlowSceneSpec, camera: String): String {
        val structured=build(s,camera)
        return android.text.Html.fromHtml(structured.replace(Regex("<[^>]+>"), "\n"), android.text.Html.FROM_HTML_MODE_LEGACY).toString().lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
    }
    fun build(s: FlowSceneSpec, camera: String, silent: Boolean=false): String {
        require(FlowScenePrompt.issues(s).isEmpty()) { "ตรวจข้อมูลซีนก่อนคัดลอกคำสั่ง" }
        val action=typed(s,aliases(s,s.action.trim()))
        val positions=s.cast.map { typed(s,aliases(s,it.position.trim())) }
        val clothes=s.cast.map { clothing(it).takeUnless { value -> value.equals("none",true) } .orEmpty() }
        val sound=typed(s,aliases(s,s.sound.trim()))
            .replace(Regex("\\b(?:(?:soft|quiet|low|faint|gentle|indoor)\\s+)*murmurs?\\s+of\\s+(?:(?:art|gallery|room|office)\\s+)*(?:patrons|people|crowds?|visitors|guests)\\b",RegexOption.IGNORE_CASE),"quiet room tone")
            .replace(Regex("\\b(?:indoor )?murmurs\\b",RegexOption.IGNORE_CASE),"quiet room tone")
        require(!thai.containsMatchIn(action) && positions.none { thai.containsMatchIn(it) } && !thai.containsMatchIn(sound) && !thai.containsMatchIn(camera) && clothes.none { thai.containsMatchIn(it) }) {
            "คำบรรยายการกระทำ ตำแหน่ง หรือเสียงยังมีภาษาไทย • กรุณาแก้เป็นภาษาอังกฤษก่อนคัดลอก โดยคงบทพูดภาษาไทย"
        }
        val cast=positions.mapIndexed { i,pos -> "${labels(s)[i]}: $pos" + clothes[i].takeIf { it.isNotBlank() }?.let { ", wearing $it" }.orEmpty() + s.cast[i].appearance.trim().takeIf { it.isNotBlank() && it.length<=160 && !thai.containsMatchIn(it) && !it.contains("MASTER",true) }?.let { ", appearance: $it" }.orEmpty() }.joinToString("; ")
        val performance=s.cast.mapIndexedNotNull { i,person ->
            val cues=person.performance.split(';').map { it.trim() }.filter { it.isNotBlank() && !thai.containsMatchIn(it) && it.substringBefore(':').trim() in setOf("emotion", "objective", "subtext", "gaze", "body_language", "breath", "reaction", "voice", "emotional_arc") && !(silent && it.startsWith("voice:")) }
                .joinToString("; ")
            cues.takeIf { it.isNotBlank() }?.let { "${labels(s)[i]} performance: ${typed(s,aliases(s,it))}" }
        }.joinToString(". ")
        val direction="Perform each character's recorded emotion and reaction naturally in this scene, respecting the prior scene and existing action. Silent listeners may react silently."
        val spokenTurns=when {
            silent -> emptyList()
            s.turns.isNotEmpty() -> s.turns.map { it.speaker to it.text }
            spoken(s.dialogue).isNotEmpty() -> listOf(s.speaker to spoken(s.dialogue))
            spoken(s.narration).isNotEmpty() -> listOf("" to spoken(s.narration))
            else -> emptyList()
        }
        val allSpokenTurns=if(!silent && spoken(s.narration).isNotEmpty() && spokenTurns.none { it.first.isBlank() }) spokenTurns + listOf("" to spoken(s.narration)) else spokenTurns
        val instructions=allSpokenTurns.mapIndexed { order,turn ->
            val label=if(turn.first.isBlank()) "Off-screen narrator" else speakerLabel(s,turn.first)
            val index=labels(s).indexOf(label)
            if(index>=0) {
                val cue=listOf(positions[index],clothes[index].takeIf { it.isNotBlank() }?.let { "wearing $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
                val others=positions.indices.filter { it!=index }.joinToString(", ") { labels(s)[it] }
                "Turn ${order+1}: ONLY $label ($cue) speaks; animate the mouth only if visible and anatomically appropriate. " + if(others.isNotBlank()) "$others remain silent; do not invent mouths" else "No other voices"
            } else "Turn ${order+1}: Only $label speaks; visible characters stay silent; do not invent mouths"
        }.joinToString(". ")
        // Ordered dialogue entries are authoritative, never repeat speech from action/performance.
        val speakerSequence=if(!silent && s.turns.isNotEmpty()) {
            val identities=s.cast.mapIndexed { i,p ->
                "${labels(s)[i]} = ${p.name.trim()}, ${positions[i]}, wearing ${clothes[i]}"
            }.joinToString("; ")
            val ordered=s.turns.mapIndexed { i,turn ->
                val who=speakerLabel(s,turn.speaker)
                val listeners=labels(s).filter { it!=who }.joinToString(", ")
                "SPEECH TURN ${i+1}: ONLY $who speaks dialogue entry ${i+1} exactly once. " +
                    "$listeners listen silently without lip movement. Do not repeat any earlier line."
            }.joinToString(" ")
            "SPEAKER SEQUENCE IS AUTHORITATIVE. Identity: $identities. " +
                "FIRST AUDIBLE VOICE MUST BE ${speakerLabel(s,s.turns.first().speaker)}. " +
                "Begin with dialogue entry 1, never a later speaker. $ordered " +
                "Speak each XML dialogue entry once in exact order; no repeated questions, " +
                "repeated answers, speaker swaps, overlapping voices or improvised dialogue. " +
                "Performance voice cues and action descriptions are NOT additional dialogue."
        } else ""
        val multiCharacterLock=if(s.cast.size>=3 && allSpokenTurns.isNotEmpty()) {
            val anchors=s.cast.mapIndexed { i,p ->
                "${labels(s)[i]} (${p.name.trim()}): ${positions[i]}" +
                    clothes[i].takeIf { it.isNotBlank() }?.let { ", ${it}" }.orEmpty()
            }.joinToString("; ")
            "MULTI-CHARACTER FIRST-FRAME SPATIAL LOCK: $anchors. " +
                "The attached first frame is the authoritative source for each person's face, outfit, left-to-right placement and visible props. " +
                "Never exchange faces, bodies, clothing, positions, dialogue voices, or mouth movements between people. " +
                "Keep each actor anchored to their own first-frame position throughout this continuous shot; no teleporting or identity blending. " +
                "Only the speaker named in the current XML dialogue entry moves their lips; all other visible actors keep their mouths closed and react silently. " +
                "Do not infer celebrity identity or impersonate any real public figure from the scene description. " +
                "If a reference image depicts a real person, use it only with the person's permission; do not alter the supplied face to evade provider review."
        } else ""
        // Megaphone dialogue requires continuous physical mouthpiece contact and processed audio.
        // Apply only when the scene explicitly establishes a megaphone and a visible speaker.
        val megaphonePresent=Regex("\\bmegaphone\\b|\\bbullhorn\\b|\\bloudhailer\\b",RegexOption.IGNORE_CASE)
            .containsMatchIn(listOf(s.action,s.location,s.sound,s.startState,s.endState).joinToString(" ")+" "+s.cast.joinToString(" ") { it.appearance+" "+it.performance })
        val megaphoneSpeakers=if(megaphonePresent) allSpokenTurns.mapNotNull { (name,_) ->
            val label=speakerLabel(s,name)
            label.takeIf { it!="Off-screen speaker" && it!="Off-screen narrator" }
        }.distinct() else emptyList()
        val megaphoneDirection=if(megaphoneSpeakers.isNotEmpty())
            "MEGAPHONE FIRST-FRAME CONTACT LOCK: "+megaphoneSpeakers.joinToString(", ")+" must ALREADY hold the megaphone mouthpiece directly at the lips in the ATTACHED FIRST FRAME, at timestamp 00:00. "+
            "Start with the megaphone in its final speaking position; NO lifting, reaching, repositioning, or preparatory movement is allowed at the beginning. "+
            "The first spoken syllable may begin only while the mouthpiece is fully aligned with the lips; NEVER start dialogue while raising the megaphone. "+
            "Maintain mouthpiece-to-lips alignment, a firm hand grip, and a fixed arm position for the ENTIRE 8-SECOND SHOT, before, during, and after the dialogue. "+
            "NEVER lower, set down, turn away, remove, or reposition the megaphone at any time in this shot, including after the final syllable. "+
            "The voice must come exclusively from the megaphone loudspeaker, with consistent narrow-band electronic amplification, compression and slight horn distortion; NEVER use dry natural speech. "+
            "Keep lip synchronization behind the mouthpiece. No off-megaphone speech, voice-over, or extra words. "+
            "If the attached first frame shows the megaphone away from the lips, that frame is NOT READY: correct or replace the first-frame image before generating video; do not animate a raise-to-mouth transition. "+
            "Do not introduce a megaphone absent from the first frame."
        else ""
        val location=typed(s,aliases(s,s.location.trim())).takeIf { it.isNotBlank() && !thai.containsMatchIn(it) }.orEmpty()
        val visual=listOf("Use the attached first frame as the starting point. One continuous 8-second vertical 9:16 shot",cast,location.takeIf { it.isNotBlank() }?.let { "Environment: $it" }.orEmpty(),action,
            "Preserve each character's identity, face or species-specific features, hair or fur, body proportions, position, clothing and props from the first frame. Keep the location and lighting consistent throughout",ScenePropContinuity.video,performance,direction,
            motion(s.action,s.location,s.startState,s.endState,s.sound),"Camera: ${camera.trim()}",multiCharacterLock,megaphoneDirection,speakerSequence,if(speakerSequence.isBlank()) instructions else "")
            .filter { it.isNotBlank() }.joinToString(". ")
        val lines=allSpokenTurns.joinToString("") { (name,text) ->
            val label=if(name.isBlank()) "Off-screen narrator" else speakerLabel(s,name)
            val offscreen=name.isBlank() || s.offscreen.any { it.trim()==name.trim() }
            "<dialogue${if(offscreen) " off_screen=\"true\"" else ""}><speaker>${xml(label)}</speaker><text>${xml(speechText(text))}</text><language>th-TH</language></dialogue>"
        }
        val audio=if(silent) "No speech, narration, music or sound effects; visible characters remain silent." else if(lines.isEmpty()) "No dialogue or narration; visible characters remain silent." else "Speak the exact written Thai words in the XML dialogue order, once per assigned speaker. No earlier line replay, extra voices, overlap or subtitles. Other visible characters remain silent while the current speaker speaks; their lips remain still. Preserve anatomy and natural delivery; do not invent words to fill time."
        val soundParts=Regex("(?is)^Ambience: (.*?); SFX: (.*?); Music: (.*?)[.]?$").matchEntire(sound)
        val ambient=soundParts?.groupValues?.get(1) ?: sound.ifBlank { "none specified" }
        val sfx=soundParts?.groupValues?.get(2) ?: "none specified"
        val music=soundParts?.groupValues?.get(3) ?: "none specified"
        val megaphoneMix=if(megaphoneSpeakers.isNotEmpty()) " Route the assigned speaker voice through a distinctly amplified megaphone/bullhorn filter for the ENTIRE utterance, never switch to dry speech. Preserve intelligible Thai articulation; no additional spoken words." else ""
        val effects=if(silent) "" else "<sound_effects><ambient>${xml(ambient)}</ambient><sfx>${xml(sfx)}</sfx><music>${xml(music)}</music><mix>Keep sound below dialogue; no additional voices.${xml(megaphoneMix)}</mix></sound_effects>"
        return "<flow><scene><aspect_ratio>9:16</aspect_ratio><duration>8</duration><lighting>Story time: ${xml(s.storyTime)}. Preserve the attached first frame's ambient light and direction throughout.</lighting><typography_safe_zone>SLIGHTLY BELOW CENTER</typography_safe_zone><prompt>${xml(visual+". "+audio)}</prompt>$lines$effects</scene></flow>"
    }
}
