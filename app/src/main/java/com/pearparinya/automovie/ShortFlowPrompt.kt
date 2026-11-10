package com.pearparinya.automovie

/** English scene direction with original speech. Never guess or delete untranslated instructions. */
internal object ShortFlowPrompt {
    private val thai=Regex("[\\u0E00-\\u0E7F]")
    private fun xml(v: String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
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
    private fun speakerLabel(s: FlowSceneSpec, name: String): String {
        val index=s.cast.indexOfFirst { it.name.trim()==name.trim() || StoryIdentity.keyFor(it.name)?.let { key -> key==StoryIdentity.keyFor(name) }==true }
        return if(index>=0) "Character ${index+1}" else "Off-screen speaker"
    }
    fun voiceOnly(s: FlowSceneSpec): String = when {
        s.turns.isNotEmpty() -> s.turns.joinToString("\n") { "${speakerLabel(s,it.speaker)}: ${ThaiSpeechPacing.format(it.text)}" }
        spoken(s.dialogue).isNotEmpty() -> "${speakerLabel(s,s.speaker)}: ${ThaiSpeechPacing.format(spoken(s.dialogue))}"
        spoken(s.narration).isNotEmpty() -> "Off-screen narrator: ${ThaiSpeechPacing.format(spoken(s.narration))}"
        else -> "No spoken dialogue."
    }
    fun build(s: FlowSceneSpec, camera: String, silent: Boolean=false): String {
        require(FlowScenePrompt.issues(s).isEmpty()) { "ตรวจข้อมูลซีนก่อนคัดลอกคำสั่ง" }
        val action=aliases(s,s.action.trim())
        val positions=s.cast.map { aliases(s,it.position.trim()) }
        val sound=aliases(s,s.sound.trim())
        require(!thai.containsMatchIn(action) && positions.none { thai.containsMatchIn(it) } && !thai.containsMatchIn(sound) && !thai.containsMatchIn(camera)) {
            "คำบรรยายการกระทำ ตำแหน่ง หรือเสียงยังมีภาษาไทย • กรุณาแก้เป็นภาษาอังกฤษก่อนคัดลอก โดยคงบทพูดภาษาไทย"
        }
        val cast=positions.mapIndexed { i,pos -> "Character ${i+1}: $pos" }.joinToString("; ")
        val visual=listOf("Use the attached first frame. One continuous 8-second vertical 9:16 shot",cast,action,
            "Keep identities, outfits, objects and location consistent",motion(s.action,s.location,s.startState,s.endState,s.sound),"Camera: ${camera.trim()}")
            .filter { it.isNotBlank() }.joinToString(". ")
        val lines=if(silent) "" else when {
            s.turns.isNotEmpty() -> s.turns.joinToString("") { "<dialogue><speaker>${xml(speakerLabel(s,it.speaker))}</speaker><text>${xml(ThaiSpeechPacing.format(it.text))}</text></dialogue>" }
            spoken(s.dialogue).isNotEmpty() -> "<dialogue><speaker>${xml(speakerLabel(s,s.speaker))}</speaker><text>${xml(ThaiSpeechPacing.format(spoken(s.dialogue)))}</text></dialogue>"
            spoken(s.narration).isNotEmpty() -> "<dialogue><speaker>Off-screen narrator</speaker><text>${xml(ThaiSpeechPacing.format(spoken(s.narration)))}</text></dialogue>"
            else -> ""
        }
        val audio=if(silent) "No dialogue or narration." else if(lines.isEmpty()) "No dialogue or narration." else "Speak the exact lines in order, naturally in Thai; no overlapping voices or subtitles."
        val ambience=if(silent) "" else sound.takeIf { it.isNotBlank() }?.let { " Sound: $it." }.orEmpty()
        return "<flow><scene><duration>8</duration><aspect_ratio>9:16</aspect_ratio><prompt>${xml(visual+". "+audio+ambience)}</prompt>$lines</scene></flow>"
    }
}
