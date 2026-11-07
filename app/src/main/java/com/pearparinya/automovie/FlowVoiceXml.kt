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
        val action=s.action.trim().ifBlank { "The scene remains still" }
        val visible=when {
            dialogue.isNotEmpty() && !offscreen -> "Only [${s.speaker}] speaks with natural lip movement; other visible characters stay silent."
            else -> "Visible characters remain silent with mouths closed."
        }
        val accentDirection=if(dialogue.isNotEmpty() || narration.isNotEmpty()) " $accent" else ""
        val prompt="$action. $visible$accentDirection Preserve the attached first frame, faces, hair, body proportions, positions, clothes, shoes, props, lighting and location. ${camera.trim()}"
        val audio=when {
            narration.isNotEmpty() -> "    <dialogue off_screen=\"true\">\n      <voice name=\"male_narrator_thai\">${xml(narration)}</voice>\n    </dialogue>\n"
            dialogue.isNotEmpty() && offscreen -> "    <dialogue off_screen=\"true\">\n      <speaker>${xml(s.speaker)}</speaker>\n      <voice language=\"th-TH\">${xml(dialogue)}</voice>\n    </dialogue>\n"
            dialogue.isNotEmpty() -> "    <dialogue>\n      <speaker>[${xml(s.speaker)}]</speaker>\n      <text>${xml(dialogue)}</text>\n      <language>th-TH</language>\n    </dialogue>\n"
            else -> ""
        }
        return "<flow>\n  <scene>\n    <aspect_ratio>9:16</aspect_ratio>\n    <duration>8</duration>\n    <prompt>${xml(prompt)}</prompt>\n"+audio+"  </scene>\n</flow>"
    }
}
