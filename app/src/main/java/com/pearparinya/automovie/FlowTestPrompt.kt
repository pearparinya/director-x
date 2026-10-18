package com.pearparinya.automovie

/** Offline test commands, not generated media or executable source code. */
internal object FlowTestPrompt {
    fun build(selected: List<String>, filenames: Map<String,String>, activity: String, location: String, action: String, outfit: String): String {
        require(selected.isNotEmpty() && selected.distinct().size==selected.size && selected.all { it in FlowProductionPack.names }) { "เลือกตัวละครอย่างน้อยหนึ่งคน" }
        require(selected.all { !filenames[it].isNullOrBlank() }) { "ขาดชื่อไฟล์ MASTER" }
        require(location.isNotBlank() && action.isNotBlank()) { "ระบุสถานที่และการกระทำ" }
        val people=selected.mapIndexed { index,id ->
            val clothes=outfit.ifBlank { when(activity) {
                "meeting" -> if(id=="gawin") "a fitted navy collared shirt and charcoal trousers" else "a tailored cream blouse and navy trousers"
                "sleep" -> "opaque cotton long-sleeve pajamas and matching long trousers"
                else -> if(id=="gawin") "a charcoal polo shirt and beige trousers" else "a fitted light-blue crew-neck top and cream trousers"
            } }
            val position=if(selected.size==1) "center" else listOf("left","center","right")[index]
            "${FlowProductionPack.names[id]} -> ${filenames.getValue(id)}\n${FlowProductionPack.dna.getValue(id)}\nScene outfit: $clothes. Screen position: $position."
        }.joinToString("\n\n")
        return """
FLOW TEST • IMAGE + VIDEO • 9:16
แนบ MASTER เฉพาะรายชื่อต่อไปนี้ ต้องแนบภาพจริง ไม่ใช่แค่ชื่อไฟล์
$people

IMAGE PROMPT
Photorealistic adult Thai drama, vertical 9:16. Only the following matched reference characters appear:
$people
Preserve each reference face, hair, skin tone, overall proportions and adult charm without increasing or reducing proportions. Replace reference clothes with the scene outfits stated above. Keep identities separate, no face blending or duplicated people. Natural clothing fit and fabric drape.
Location: $location
First-frame setup and action: $action
Eye-level medium shot showing faces and hands, realistic scene lighting. No captions, added text or watermark.

VIDEO PROMPT
8 seconds, vertical 9:16, locked-off static camera, one continuous subtle action. Use the generated and reviewed scene image as the first frame. Confirm the planned character map against that actual frame before running:
$people
Location: $location
Action: $action
Preserve the first-frame identities, outfits, positions and props. No dialogue or narration in this visual test; all mouths stay closed. No camera movement, cuts, captions or watermark. Hold the ending briefly.

วิธีทดสอบ: แนบ MASTER ที่ระบุใน Flow -> ใช้ IMAGE PROMPT -> ตรวจภาพจริง -> แนบภาพฉากนั้นเป็นเฟรมแรก -> ใช้ VIDEO PROMPT
นี่เป็นไฟล์คำสั่ง ยังไม่ได้สร้างภาพหรือทดสอบผลจาก Flow
        """.trimIndent()
    }
}
