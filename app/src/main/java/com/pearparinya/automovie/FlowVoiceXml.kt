package com.pearparinya.automovie

/** Text prompt in the concise XML style tested by the user; not a Flow API schema. */
internal object FlowVoiceXml {
    internal const val accent = "The speaker speaks Thai with a natural Central Thai accent."
    private fun xml(v: String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
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
            s.turns.isNotEmpty() -> "Speak the written dialogue turns in order, one speaker at a time without overlap. Only the current visible speaker moves their lips; all other visible characters stay silent."
            dialogue.isNotEmpty() && !offscreen -> "Only [$speakerLabel] speaks with natural lip movement; other visible characters stay silent."
            else -> "Visible characters remain silent with mouths closed."
        }
        val accentDirection=if(dialogue.isNotEmpty() || narration.isNotEmpty() || s.turns.isNotEmpty()) " $accent" else ""
        val prompt="$mapping $action. $visible$accentDirection Preserve the attached first frame, faces, hair, body proportions, positions, clothes, shoes, props, lighting and location. ${camera.trim()}".trim()
        val audio=when {
            s.turns.isNotEmpty() -> s.turns.joinToString("") { turn ->
                val off=s.offscreen.any { it.trim()==turn.speaker.trim() || StoryIdentity.keyFor(it)?.let { key -> key==StoryIdentity.keyFor(turn.speaker) }==true }
                "    <dialogue${if(off) " off_screen=\"true\"" else ""}>\n      <speaker>${xml(if(off) role(turn.speaker) else "[${role(turn.speaker)}]")}</speaker>\n      <text>${xml(turn.text)}</text>\n      <language>th-TH</language>\n    </dialogue>\n"
            }
            narration.isNotEmpty() -> "    <dialogue off_screen=\"true\">\n      <voice name=\"male_narrator_thai\">${xml(narration)}</voice>\n    </dialogue>\n"
            dialogue.isNotEmpty() && offscreen -> "    <dialogue off_screen=\"true\">\n      <speaker>${xml(speakerLabel)}</speaker>\n      <text>${xml(dialogue)}</text>\n      <language>th-TH</language>\n    </dialogue>\n"
            dialogue.isNotEmpty() -> "    <dialogue>\n      <speaker>[${xml(speakerLabel)}]</speaker>\n      <text>${xml(dialogue)}</text>\n      <language>th-TH</language>\n    </dialogue>\n"
            else -> ""
        }
        return "<flow>\n  <scene>\n    <aspect_ratio>9:16</aspect_ratio>\n    <duration>8</duration>\n    <lighting>Preserve the lighting and colors of the attached start frame.</lighting>\n    <typography_safe_zone>SLIGHTLY BELOW CENTER</typography_safe_zone>\n    <prompt>${xml(prompt)}</prompt>\n"+audio+"  </scene>\n</flow>"
    }
}
