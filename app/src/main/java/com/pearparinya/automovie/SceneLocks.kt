package com.pearparinya.automovie

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Per-scene, per-medium approval state. A saved image never implicitly approves Flow.
 * Approval is tied to the exact content digest so edited media is not silently trusted.
 */
internal object SceneLocks {
    enum class Medium(val key: String) { IMAGE("image"), FLOW("flow") }
    private fun digest(file: File): String {
        require(file.isFile && file.length() > 0L) { "ไม่มีไฟล์ผลงานสำหรับบันทึก" }
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                md.update(buffer, 0, count)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    private fun key(sceneId: String, medium: Medium): String {
        require(Regex("EP[0-9]{2,}_SC[0-9]{3,}").matches(sceneId)) { "รหัสซีนไม่ถูกต้อง" }
        return "${sceneId}_${medium.key}"
    }
    fun approve(state: JSONObject, sceneId: String, medium: Medium, artifact: File) {
        state.put(key(sceneId, medium), JSONObject()
            .put("sha256", digest(artifact))
            .put("approved", true))
    }
    fun isLocked(state: JSONObject, sceneId: String, medium: Medium, artifact: File?): Boolean {
        if (artifact == null || !artifact.isFile || artifact.length() == 0L) return false
        val record = state.optJSONObject(key(sceneId, medium)) ?: return false
        return record.optBoolean("approved") &&
            record.optString("sha256") == runCatching { digest(artifact) }.getOrNull()
    }
    fun unlock(state: JSONObject, sceneId: String, medium: Medium) {
        state.remove(key(sceneId, medium))
    }
    fun status(state: JSONObject, sceneId: String, medium: Medium, artifact: File?): String =
        if (isLocked(state, sceneId, medium, artifact)) "🔒 อนุมัติแล้ว" else "⏳ รอตรวจสอบ"

    /** Persist separately from story.json so pending story regeneration cannot erase approvals. */
    fun load(workspace: File): JSONObject = runCatching {
        JSONObject(File(workspace, "scene_locks.json").readText(Charsets.UTF_8))
    }.getOrDefault(JSONObject())

    fun save(workspace: File, state: JSONObject) {
        require(workspace.isDirectory) { "ไม่พบพื้นที่งาน" }
        val target = File(workspace, "scene_locks.json")
        val temp = File(workspace, "scene_locks.json.tmp")
        temp.writeText(state.toString(), Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
