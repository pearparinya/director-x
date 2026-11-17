package com.pearparinya.automovie

import java.io.File

/**
 * Build deterministic, source-backed image bindings for one scene.
 * A three-view MASTER is kept intact; only a verified crop may supplement it.
 * No inferred views, synthetic faces or cross-character fallback are permitted.
 */
internal object MasterReferencePreparation {
    data class CharacterSource(val name: String, val master: File)
    data class Attachment(val character: String, val role: String, val file: File)
    data class Result(val attachments: List<Attachment>, val warnings: List<String>) {
        val ready: Boolean get() = warnings.isEmpty()
    }

    fun prepare(characters: List<CharacterSource>, store: FaceReferenceStore): Result {
        val attachments = mutableListOf<Attachment>()
        val warnings = mutableListOf<String>()
        val seenNames = mutableSetOf<String>()
        val usedSources = mutableSetOf<String>()
        characters.forEach { character ->
            val name = character.name.trim()
            val normalizedName = name.replace(Regex("\\s+"), " ").lowercase()
            if (name.isBlank() || !seenNames.add(normalizedName)) {
                warnings.add("ชื่อตัวละครว่างหรือซ้ำ: กรุณาตรวจการจับคู่ MASTER")
                return@forEach
            }
            val source = character.master
            if (!source.isFile || source.length() == 0L) {
                warnings.add("$name: ไม่พบไฟล์ MASTER ต้นฉบับ")
                return@forEach
            }
            val digest = MasterLibrary.digest(source.readBytes())
            if (!usedSources.add(digest)) {
                warnings.add("$name: ใช้ไฟล์ MASTER เดียวกับตัวละครอื่น โปรดตรวจสอบ")
                return@forEach
            }
            // Never infer a 3-view image from an arbitrary single-view source.
            attachments.add(Attachment(name, "ORIGINAL_MASTER", source))
            val crop = store.latest(source)
            if (crop != null && crop.isFile && crop.length() > 0L)
                attachments.add(Attachment(name, "FACE_CROP", crop))
            else warnings.add("$name: ยังไม่มีภาพใบหน้าที่ตรวจสอบแล้ว แนบ MASTER ต้นฉบับเท่านั้น")
        }
        return Result(attachments, warnings)
    }

    fun bindingText(result: Result): String =
        result.attachments.mapIndexed { index, attachment ->
            "Reference image ${index + 1}: ${attachment.character} | ${attachment.role} | identity only; never blend with another character."
        }.joinToString("\n")
}
