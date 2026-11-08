package com.pearparinya.automovie

/** Human-readable Flow prompts based only on the validated scene, not a Flow API schema. */
internal object CompactFlowPrompt {
    private fun line(v: String): String = v.trim().takeUnless { it.isBlank() || it.equals("none",true) || it=="ไม่มี" || it=="ไม่มีเสียงบรรยาย" }.orEmpty()
    fun build(s: FlowSceneSpec, camera: String, silent: Boolean): String {
        require(FlowScenePrompt.issues(s).isEmpty()) { "กรุณาตรวจแก้ข้อมูลซีนก่อนคัดลอก" }
        val action=s.action.trim().ifBlank { "The scene remains still." }
        val dialogue=line(s.dialogue)
        val narration=line(s.narration)
        val offscreen=s.offscreen.any { it.trim()==s.speaker.trim() }
        val voices=when {
            silent -> "No speech, narration, music or sound effects. Keep visible mouths closed."
            narration.isNotEmpty() -> "Visible characters remain silent with mouths closed. An adult male narrator speaks off-screen in Thai: \"$narration\""
            dialogue.isNotEmpty() && offscreen -> "Visible characters remain silent with mouths closed. Off-screen character ${s.speaker} speaks in Thai: \"$dialogue\""
            dialogue.isNotEmpty() -> "Only ${s.speaker} speaks in Thai with natural lip movement: \"$dialogue\" Other visible characters remain silent."
            else -> "Visible characters remain silent with mouths closed. No speech or narration."
        }
        return listOf(
            "Use the attached image as the first frame. One continuous 8-second vertical 9:16 shot.",
            action,
            "Preserve the people, faces, hair, body proportions, positions, clothes, shoes, props and location from the first frame. Keep objects and hands in contact naturally.",
            ReadingPropPolicy.forScene(s.action,s.startState,s.endState),
            "Camera: ${camera.trim()}",
            voices,
            "No subtitles or on-screen text."
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }
    fun voiceText(s: FlowSceneSpec): String {
        val dialogue=line(s.dialogue); val narration=line(s.narration)
        if(s.turns.isNotEmpty()) return s.turns.joinToString("\n") { "${it.speaker}: ${it.text}" }
        return when {
            dialogue.isNotEmpty() -> "${s.speaker}: $dialogue"
            narration.isNotEmpty() -> "ผู้บรรยาย: $narration"
            else -> "ซีนนี้ไม่มีบทพูดหรือคำบรรยายที่ระบุ"
        }
    }
}
