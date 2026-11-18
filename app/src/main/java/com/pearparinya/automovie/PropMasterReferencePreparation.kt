package com.pearparinya.automovie

import java.io.File
import org.json.JSONObject

/**
 * Validate and prepare physical prop MASTER image attachments.
 * Callers must supply resolved local files from the prop library, never infer a path from a prompt.
 */
internal object PropMasterReferencePreparation {
    data class Source(val id: String, val item: String, val file: File)
    data class Attachment(val id: String, val item: String, val role: String, val file: File)
    data class Result(val attachments: List<Attachment>, val issues: List<String>) {
        val ready: Boolean get() = issues.isEmpty()
    }

    fun prepare(scene: JSONObject, sources: List<Source>): Result {
        val links = scene.optJSONArray("prop_links") ?: return Result(emptyList(), emptyList())
        val byId = sources.groupBy { it.id.trim().uppercase() }
        val attachments = mutableListOf<Attachment>()
        val issues = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (index in 0 until links.length()) {
            val link = links.optJSONObject(index) ?: continue
            val id = link.optString("prop_master_id").trim().uppercase()
            if (id.isEmpty()) continue // Unbound legacy props have no physical MASTER.
            if (!id.matches(Regex("^[A-Z][A-Z0-9_-]{1,63}$"))) {
                issues.add("รหัส MASTER อุปกรณ์ไม่ถูกต้อง: $id")
                continue
            }
            if (!seen.add(id)) continue // One image per MASTER, even when held by multiple people.
            val matches = byId[id].orEmpty()
            if (matches.size != 1) {
                issues.add("$id: ต้องพบไฟล์ MASTER อุปกรณ์ที่ตรงกันเพียงหนึ่งรายการ")
                continue
            }
            val source = matches.single()
            if (!source.file.isFile || source.file.length() <= 0L) {
                issues.add("$id: ไม่พบไฟล์ภาพ MASTER อุปกรณ์")
                continue
            }
            if (!isSupportedImage(source.file)) {
                issues.add("$id: ไฟล์ MASTER ไม่ใช่ภาพ JPEG, PNG หรือ WebP ที่รองรับ")
                continue
            }
            attachments.add(Attachment(id, source.item, "PROP_MASTER", source.file))
        }
        return Result(attachments, issues)
    }

    /** Inspect file bytes, not the filename extension, before forwarding a reference. */
    private fun isSupportedImage(file: File): Boolean {
        val header = ByteArray(12)
        val count = file.inputStream().use { it.read(header) }
        if (count < 12) return false
        val png = header.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() }
        val jpeg = header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        val webp = String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
        return png || jpeg || webp
    }

    fun bindingText(result: Result, startIndex: Int): String {
        require(startIndex >= 1)
        return result.attachments.mapIndexed { index, attachment ->
            "Reference image ${startIndex + index}: PROP MASTER ${attachment.id} | ${attachment.item} | ${attachment.role}; preserve object design, never treat as a human identity."
        }.joinToString("\n")
    }
}
