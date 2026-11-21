package com.pearparinya.automovie

/** Text prompt in the concise XML style tested by the user; not a Flow API schema. */
internal object ThaiSpeechPacing {
    const val direction = "Speak the exact written Thai words naturally, with clear phrase-level pauses guided by existing spaces and punctuation. Do not read punctuation aloud, add words or rush; fit the delivery within the shot."
    fun format(text: String): String = text.replace(Regex("(\\.{3}|…)(?=[ก-๙A-Za-z])"), "$1 ")
}

internal object FlowVoiceXml {
    internal const val accent = "The speaker speaks Thai with a natural Central Thai accent."
    private fun xml(v: String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun spoken(v: String)=v.trim().takeUnless { it.isBlank() || it.equals("none",true) || it=="ไม่มี" || it=="ไม่มีเสียงบรรยาย" }.orEmpty()
    fun build(s: FlowSceneSpec, camera: String): String {
        require(FlowScenePrompt.issues(s).isEmpty()) { "ตรวจข้อมูลซีนก่อนสร้างคำสั่ง" }
        val dialogue=spoken(s.dialogue)
        val narration=spoken(s.narration)
        val offscreen=dialogue.isNotEmpty() && s.offscreen.any { it.trim()==s.speaker.trim() }
        // These labels are local to the current frame, never inferred from gender or image filenames.
        fun label(index: Int)="Character ${index+1}"
        val speakerIndex=s.cast.indexOfFirst { it.name.trim()==s.speaker.trim() || StoryIdentity.keyFor(it.name)?.let { key -> key==StoryIdentity.keyFor(s.speaker) }==true }
        val speakerLabel=if(speakerIndex>=0) label(speakerIndex) else "Off-screen character"
        var action=s.action.trim().ifBlank { "The scene remains still" }
        val aliases=s.cast.flatMapIndexed { index,person ->
            val known=when(StoryIdentity.keyFor(person.name) ?: person.name.trim()) {
                "gawin" -> listOf("กวิน", "Gawin", "Kawin")
                "rinlada" -> listOf("รินรดา", "รินลดา", "Rinlada", "Rinrada")
                "mind" -> listOf("เมย์", "มายด์", "May", "Mind")
                "พัชรินทร์" -> listOf("พัชรินทร์", "Patcharin")
                "นที" -> listOf("นที", "Natee")
                "ปิยวรรณ" -> listOf("ปิยวรรณ", "Piyawan")
                else -> emptyList()
            }
            (known+person.name+person.englishName).filter { it.isNotBlank() }.distinct().map { it to label(index) }
        }.sortedByDescending { it.first.length }
        aliases.forEach { (name,replacement) ->
            val boundary=if(name.any { it in 'ก'..'๙' }) "(?<![ก-๙])${Regex.escape(name)}(?![ก-๙])" else "(?<![A-Za-z0-9_])${Regex.escape(name)}(?![A-Za-z0-9_])"
            action=Regex(boundary, if(name=="May") emptySet() else setOf(RegexOption.IGNORE_CASE)).replace(action) { replacement }
        }
        val mapping=s.cast.mapIndexed { index,person -> "${label(index)}: ${person.position.trim()}." }.joinToString(" ")
        fun role(name: String): String {
            val index=s.cast.indexOfFirst { it.name.trim()==name.trim() || StoryIdentity.keyFor(it.name)?.let { key -> key==StoryIdentity.keyFor(name) }==true }
            if(index>=0) return label(index)
            val outside=s.offscreen.indexOfFirst { it.trim()==name.trim() || StoryIdentity.keyFor(it)?.let { key -> key==StoryIdentity.keyFor(name) }==true }
            return "Off-screen character ${outside+1}"
        }
        val visible=when {
            s.turns.isNotEmpty() -> {
                val identities=s.cast.mapIndexed { index, person ->
                    "${label(index)} = ${person.name.trim()} (position: ${person.position.trim()}, outfit: ${person.outfit.trim()})."
                }.joinToString(" ")
                val instructions=s.turns.mapIndexed { index, turn ->
                    val active=role(turn.speaker)
                    val off=s.offscreen.any { it.trim()==turn.speaker.trim() || StoryIdentity.keyFor(it)?.let { key -> key==StoryIdentity.keyFor(turn.speaker) }==true }
                    val others=s.cast.indices.filter { label(it)!=active }.joinToString(", ") { label(it) }
                    "TURN ${index+1}: ONLY $active (${turn.speaker.trim()}) speaks the assigned Thai text. " +
                        (if(off) "Speaker is off-screen; no visible character lip-syncs. " else "Only $active lip-syncs. ") +
                        (if(others.isBlank()) "" else "$others must remain silent with mouths closed and lips still; they may react using eyes, head, hands and posture only. ")
                }.joinToString(" ")
                "STRICT SPEAKER AND LIP-SYNC LOCK. $identities $instructions " +
                    "Never swap dialogue, speakers, voices, screen positions or lip movements. " +
                    "Only one speaker at a time; no overlapping speech, listener lip-sync, ad-libs, extra voices or subtitles. " +
                    "Do not force dialogue to fit by rushing; if the written lines exceed eight seconds, revise or split the scene before generating."
            }
            dialogue.isNotEmpty() && !offscreen -> "Only [$speakerLabel] speaks with natural lip movement; other visible characters stay silent."
            else -> "Visible characters remain silent with mouths closed and lips still. No visible character lip-syncs or speaks."
        }
        val accentDirection=if(dialogue.isNotEmpty() || narration.isNotEmpty() || s.turns.isNotEmpty()) " $accent ${ThaiSpeechPacing.direction}" else ""
        val prompt="$mapping $action. $visible$accentDirection Preserve the attached first frame, faces, hair, body proportions, positions, clothes, shoes, props, lighting and location. ${ScenePropContinuity.video} ${CinematicFlowGuard.direction()}${CinematicScenePreflight.promptDirection(s.cinematicProps).takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()} ${camera.trim()}".trim()
        val audio=when {
            s.turns.isNotEmpty() -> s.turns.joinToString("") { turn ->
                val off=s.offscreen.any { it.trim()==turn.speaker.trim() || StoryIdentity.keyFor(it)?.let { key -> key==StoryIdentity.keyFor(turn.speaker) }==true }
                "    <dialogue${if(off) " off_screen=\"true\"" else ""}>\n      <speaker>${xml(if(off) role(turn.speaker) else "[${role(turn.speaker)}]")}</speaker>\n      <text>${xml(ThaiSpeechPacing.format(turn.text))}</text>\n      <language>th-TH</language>\n    </dialogue>\n"
            }
            narration.isNotEmpty() -> "    <dialogue off_screen=\"true\">\n      <voice name=\"male_narrator_thai\">${xml(ThaiSpeechPacing.format(narration))}</voice>\n    </dialogue>\n"
            dialogue.isNotEmpty() && offscreen -> "    <dialogue off_screen=\"true\">\n      <speaker>${xml(speakerLabel)}</speaker>\n      <text>${xml(ThaiSpeechPacing.format(dialogue))}</text>\n      <language>th-TH</language>\n    </dialogue>\n"
            dialogue.isNotEmpty() -> "    <dialogue>\n      <speaker>[${xml(speakerLabel)}]</speaker>\n      <text>${xml(ThaiSpeechPacing.format(dialogue))}</text>\n      <language>th-TH</language>\n    </dialogue>\n"
            else -> ""
        }
        return "<flow>\n  <scene>\n    <aspect_ratio>9:16</aspect_ratio>\n    <duration>8</duration>\n    <lighting>Preserve the lighting and colors of the attached start frame.</lighting>\n    <typography_safe_zone>SLIGHTLY BELOW CENTER</typography_safe_zone>\n    <prompt>${xml(prompt)}</prompt>\n"+audio+"  </scene>\n</flow>"
    }
}
