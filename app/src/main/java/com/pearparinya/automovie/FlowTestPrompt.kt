package com.pearparinya.automovie

import org.json.JSONObject

/** Reference labels stay independent of filenames and upload order. */
internal object FlowTestPrompt {
    val attachmentDefaults=FlowProductionPack.names.keys.associateWith { FlowReferencePrompt.alias(it) }
    fun build(selected: List<String>, activity: String, location: String, action: String, outfit: String): String {
        require(selected.isNotEmpty() && selected.distinct().size==selected.size && selected.all { it in FlowProductionPack.names }) { "เลือกตัวละครอย่างน้อยหนึ่งคน" }
        require(location.isNotBlank() && action.isNotBlank()) { "ระบุสถานที่และการกระทำ" }
        val people=selected.mapIndexed { index,id ->
            val clothes=outfit.ifBlank { when(activity) {
                "meeting" -> if(id=="gawin") "a fitted navy collared shirt and charcoal trousers" else "a tailored cream blouse and navy trousers"
                "sleep" -> "opaque cotton long-sleeve pajamas and matching long trousers"
                else -> if(id=="gawin") "a charcoal polo shirt and beige trousers" else "a fitted light-blue crew-neck top and cream trousers"
            } }
            JSONObject().put("name",FlowProductionPack.names.getValue(id)).put("outfit",clothes)
                .put("screen_position",if(selected.size==1) "center" else if(selected.size==2) listOf("left","right")[index] else listOf("left","center","right")[index])
        }
        return FlowReferencePrompt.header(people)+"""

FLOW TEST • VIDEO ONLY • 8 seconds • 16:9
VIDEO PROMPT
Create a photorealistic 8-second horizontal 16:9 video using the attached character reference images directly as identity references, not as the scene background or opening composition. Use the CHARACTER bindings and stated outfits above. Replace reference clothing from the start; no wardrobe transformation.
${FlowVisualDirection.identity}
${FlowVisualDirection.color}
Location: $location
Action: $action
Start already in the described location and outfits. Eye-level head-to-upper-thigh framing, natural perspective in a widescreen composition without lens distortion, showing faces and hands. Locked-off static camera, one continuous subtle action, no camera movement or cuts. Preserve identities, body proportions, outfits and props throughout. No dialogue or narration in this visual test; all mouths stay closed. No captions, added text or watermark. Hold the ending briefly.

${FlowVisualDirection.referencePriority}
${FlowVisualDirection.referenceReview}

แนบภาพเฉพาะคนในรายการด้านบน ใช้โหมดวิดีโอที่รองรับภาพอ้างอิงตัวละคร ไม่ต้องสร้างภาพฉากก่อน ตรวจว่าภาพแต่ละคนจับคู่กับรหัสถูกต้อง คำสั่งนี้ไม่เปลี่ยน MASTER และไม่รับประกันความเหมือนทุกจุด
        """.trimIndent()
    }
}
