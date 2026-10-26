package com.pearparinya.automovie

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Immutable source references. Assignments copy bytes; no project or legacy master is migrated. */
internal class MasterLibrary(private val root: File, private val load: (String) -> ByteArray) {
    data class Entry(val id: String, val filename: String, val original: String, val sha256: String)
    fun entries(): List<Entry> {
        val rows = JSONObject(String(load("master_library/catalog.json"), Charsets.UTF_8)).getJSONArray("records")
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            Entry(row.getString("id"), row.getString("file"), row.getString("original_filename"), row.getString("sha256"))
        }.also { entries ->
            require(entries.size == 35 && entries.map { it.id }.distinct().size == 35)
            require(entries.all { it.id.matches(Regex("CHAR_[0-9]{3}")) && File(it.filename).name == it.filename })
        }
    }
    fun source(entry: Entry): File {
        val target = File(root, entry.filename)
        if (target.isFile && digest(target.readBytes()) == entry.sha256) return target
        val bytes = load("master_library/" + entry.filename)
        require(digest(bytes) == entry.sha256) { "ภาพ MASTER ไม่ตรงกับทะเบียน" }
        copyBytes(bytes, target)
        return target
    }
    fun assign(entry: Entry, target: File) {
        val bytes = source(entry).readBytes()
        if (target.exists()) target.copyTo(File(target.parentFile, target.name + ".previous"), overwrite = true)
        copyBytes(bytes, target)
    }
    private fun copyBytes(bytes: ByteArray, target: File) {
        target.parentFile!!.mkdirs()
        val pending = File(target.parentFile, target.name + ".pending")
        try {
            pending.writeBytes(bytes)
            check(pending.renameTo(target)) { "บันทึก MASTER ไม่สำเร็จ" }
        } finally { pending.delete() }
    }
    companion object {
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun description(id: String) = "Fictional character from reference $id. Match this character's own attached three-view MASTER: face, hair, skin, body silhouette and proportions. One sheet shows one character, not three people. Do not infer exact age, measurements or borrow another character's features."
    }
}
