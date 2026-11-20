package com.pearparinya.automovie

import org.json.JSONObject

/** Read the current scene each time; never infer missing story facts. */
internal object SceneOverview {
    private fun field(scene: JSONObject, name: String): String =
        scene.optString(name).trim().takeUnless { it.isBlank() || it.equals("null",true) }.orEmpty()
    private fun names(scene: JSONObject): String {
        val cast=scene.optJSONArray("characters") ?: return ""
        return (0 until cast.length()).mapNotNull { cast.optJSONObject(it)?.optString("name")?.trim()?.takeIf(String::isNotBlank) }.distinct().joinToString("、")
    }
    private fun thaiAction(scene: JSONObject): String {
        val explicit=listOf("action_th", "action_thai", "thai_description", "description_th", "scene_description_th")
            .firstNotNullOfOrNull { field(scene,it).takeIf(String::isNotBlank) }
        if(explicit!=null) return explicit
        val action=field(scene,"action")
        if(action.isBlank()) return "ยังไม่ได้ระบุเหตุการณ์ภาษาไทย"
        if(action.any { it in '\u0E00'..'\u0E7F' }) return action
        val lower=action.lowercase()
        val actor=names(scene).replace("、"," และ ").ifBlank { "ตัวละครในซีน" }
        return when {
            "wades into the strong current" in lower -> "$actor ลุยลงไปในกระแสน้ำที่ไหลแรง พร้อมสื่อสารกับผู้ร่วมทีม"
            "wades forward through the strong current" in lower -> "$actor ลุยฝ่ากระแสน้ำที่ไหลแรงไปข้างหน้าและพูดระหว่างปฏิบัติภารกิจ"
            "reaches out to" in lower && "carry him to safety" in lower -> "$actor ยื่นมือเข้าช่วยเด็กที่ติดอยู่บริเวณบ้าน และเตรียมพาเด็กไปยังที่ปลอดภัย"
            else -> "ยังไม่มีคำอธิบายภาษาไทยสำหรับเหตุการณ์นี้ โปรดตรวจบทภาษาอังกฤษด้านล่างก่อนสร้างซีนต่อ"
        }
    }
    private fun thaiLocation(scene: JSONObject): String {
        val explicit=field(scene,"location_th").ifBlank { field(scene,"thai_location") }
        if(explicit.isNotBlank()) return explicit
        val location=field(scene,"location")
        if(location.any { it in '\u0E00'..'\u0E7F' }) return location
        return when(location.lowercase()) {
            "flooded street near canal" -> "ถนนน้ำท่วมใกล้คลอง"
            "flooded porch of tonkla's house" -> "ชานบ้านต้นกล้าที่มีน้ำท่วม"
            else -> if(location.isBlank()) "ไม่ได้ระบุ" else "ยังไม่มีชื่อสถานที่ภาษาไทย"
        }
    }
    fun short(scene: JSONObject): String = listOf(
        names(scene).takeIf { it.isNotBlank() },
        thaiLocation(scene),
        thaiAction(scene).let { if(it.length>120) it.take(120)+"…" else it }
    ).filterNotNull().joinToString(" • ").ifBlank { "ยังไม่มีรายละเอียดซีน" }
    fun detail(scene: JSONObject, imageId: String): String {
        val dialogue=field(scene,"dialogue")
        val speaker=field(scene,"speaker")
        val narration=field(scene,"narration")
        val speech=when {
            ProductionCommandCheck.dialogueTurns(scene).isNotEmpty() -> ProductionCommandCheck.dialogueTurns(scene).joinToString(" / ") { "${it.speaker}: ${ThaiSpeechPacing.format(it.text)}" }
            dialogue.isNotBlank() -> (if(speaker.isNotBlank()) "$speaker: " else "")+ThaiSpeechPacing.format(dialogue)
            narration.isNotBlank() -> "บรรยาย: ${ThaiSpeechPacing.format(narration)}"
            else -> "ไม่มีบทพูดหรือคำบรรยายที่ระบุ"
        }
        val castSpec=ProductionCommandCheck.spec(scene)
        val labels=ShortFlowPrompt.labels(castSpec)
        val bindings=castSpec.cast.mapIndexed { i,p ->
            "${labels[i]} = ${p.name} • ${p.position.ifBlank { "ไม่ระบุตำแหน่ง" }} • ${p.outfit.ifBlank { "ไม่ระบุชุด" }}"
        }.joinToString("\n")
        val spokenText=if(ProductionCommandCheck.dialogueTurns(scene).isNotEmpty()) ProductionCommandCheck.dialogueTurns(scene).joinToString("") { it.text } else dialogue.ifBlank { narration }
        val warning=if(spokenText.count { !it.isWhitespace() }>80) "⚠ บทพูดอาจยาวสำหรับ 8 วินาที • ประเมินจากความยาวข้อความเท่านั้น กรุณาฟังเสียงจริงหรือแบ่งซีนก่อนใช้" else ""
        return listOf(
            "สรุปซีน",
            "สถานที่ (ไทย): "+thaiLocation(scene),
            "สถานที่ต้นฉบับ: "+field(scene,"location").ifBlank { "ไม่ได้ระบุ" },
            "ตัวละคร: "+names(scene).ifBlank { "ไม่ได้ระบุ" },
            if(bindings.isBlank()) "" else "ตรวจตัวละครและชุด:\n$bindings",
            "เหตุการณ์ (ไทย): "+thaiAction(scene),
            "เหตุการณ์ต้นฉบับ: "+field(scene,"action").ifBlank { "ไม่ได้ระบุ" },
            "ต้นฉากจากบท: "+field(scene,"start_state").ifBlank { "ไม่ได้ระบุ" },
            "สิ่งของและผู้ใช้จากบท: "+field(scene,"props").ifBlank { "ยังไม่ระบุ • ตรวจรายละเอียดฉาก" },
            "📷 ตรวจสิ่งของกับภาพเริ่มต้นจริงก่อนใช้ Flow",
            "เสียง: "+speech,
            warning,
            "ภาพซีนสำหรับ Flow: $imageId"
        ).filter { it.isNotBlank() }.joinToString("\n")
    }
}
