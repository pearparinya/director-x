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
    fun short(scene: JSONObject): String = listOf(
        names(scene).takeIf { it.isNotBlank() },
        field(scene,"location").takeIf { it.isNotBlank() },
        field(scene,"action").takeIf { it.isNotBlank() }?.let { if(it.length>100) it.take(100)+"…" else it }
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
        return listOf(
            "สรุปซีน",
            "สถานที่: "+field(scene,"location").ifBlank { "ไม่ได้ระบุ" },
            "ตัวละคร: "+names(scene).ifBlank { "ไม่ได้ระบุ" },
            "เหตุการณ์: "+field(scene,"action").ifBlank { "ไม่ได้ระบุ" },
            "เสียง: "+speech,
            "ภาพซีนสำหรับ Flow: $imageId"
        ).joinToString("\n")
    }
}
