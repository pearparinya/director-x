package com.pearparinya.automovie

/** Offline test commands, not generated media or executable source code. */
internal object FlowTestPrompt {
    // User's attachment names in Flow; aliases only, never replace the app's MASTER bytes.
    val attachmentDefaults = mapOf("gawin" to "1000026557.png", "rinlada" to "1000026584.png", "mind" to "1000026586.png")
    fun build(selected: List<String>, filenames: Map<String,String>, activity: String, location: String, action: String, outfit: String): String {
        require(selected.isNotEmpty() && selected.distinct().size==selected.size && selected.all { it in FlowProductionPack.names }) { "เลือกตัวละครอย่างน้อยหนึ่งคน" }
        require(selected.all { !filenames[it].isNullOrBlank() }) { "ขาดชื่อไฟล์ MASTER" }
        require(selected.all { name -> val value=filenames.getValue(name); value.none { it == '/' || it == '\\' || it == '\n' || it == '\r' } && value.substringAfterLast('.', "").lowercase() in listOf("png","jpg","jpeg","webp") }) { "ชื่อไฟล์ต้องเป็นชื่อภาพ PNG / JPG / WEBP ไม่ใส่โฟลเดอร์หรือขึ้นบรรทัดใหม่" }
        require(selected.map { filenames.getValue(it).lowercase() }.distinct().size == selected.size) { "แต่ละตัวละครต้องใช้ชื่อไฟล์อ้างอิงต่างกัน" }
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
แนบภาพ MASTER: ${selected.joinToString(" | ") { "${FlowProductionPack.names.getValue(it)} = ${filenames.getValue(it)}" }}
FLOW TEST • VIDEO ONLY • 8 seconds • 9:16

VIDEO PROMPT
Create a photorealistic 8-second vertical 9:16 video using the attached character reference images directly as identity references, not as the scene background or the opening composition. Match each filename exclusively to its named person below. Do not borrow identity or body shape from other attachments or previously generated scenes. Only these adult characters appear:
$people
Preserve each person's reference facial geometry, eyes, nose, lips, jawline, hairstyle and skin tone. Maintain the reference shoulder width, chest volume, waist width, hip width, upper-arm thickness, chest-to-waist ratio and waist-to-hip ratio without slimming, enlargement, face blending or duplicated people. Fit the new clothes to the original body, with realistic fabric drape. Replace reference clothing with the specified scene outfits from the start of the video; no on-screen wardrobe transformation.
Location: $location
Action: $action
Start already in the described location and outfits. Eye-level head-to-upper-thigh framing, natural portrait perspective without wide-angle distortion, showing faces and hands. Locked-off static camera, one continuous subtle action, no camera movement or cuts. Preserve identities, body proportions, outfits and props throughout. No dialogue or narration in this visual test; all mouths stay closed. No captions, added text or watermark. Hold the ending briefly.

ตรวจชื่อไฟล์ให้ตรงกับภาพที่แนบจริง ชื่อไฟล์นี้เป็นชื่ออ้างอิงใน Flow ไม่ใช่การเปลี่ยน MASTER ภายในแอป ห้ามใช้ภาพผลทดสอบที่รูปร่างเพี้ยนเป็นต้นแบบร่วม
วิธีทดสอบ: แนบภาพ MASTER เฉพาะคนที่เลือกในโหมดวิดีโอที่รองรับภาพอ้างอิงตัวละคร แล้ววางคำสั่งนี้ ไม่ต้องสร้างภาพฉากก่อน หากโหมดที่เลือกไม่รองรับภาพอ้างอิงตัวละครโดยตรง ให้เปลี่ยนเป็นโหมดที่รองรับก่อนทดสอบ
นี่เป็นไฟล์คำสั่ง ยังไม่ได้สร้างหรือทดสอบผลวิดีโอจาก Flow และไม่รับประกันความเหมือนทุกจุด
        """.trimIndent()
    }
}
