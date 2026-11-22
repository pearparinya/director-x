package com.pearparinya.automovie

import org.json.JSONObject
import java.io.File

/** Simple checklist only: no file checks, hashes, generation rules or locking. */
internal object SceneChecklist {
    enum class Kind(val key: String) { IMAGE("image"), FLOW("flow") }

    fun isDone(state: JSONObject, sceneId: String, kind: Kind): Boolean =
        state.optBoolean("${sceneId}_${kind.key}", false)

    fun setDone(state: JSONObject, sceneId: String, kind: Kind, done: Boolean) {
        state.put("${sceneId}_${kind.key}", done)
    }

    fun count(state: JSONObject, episode: Int, kind: Kind, total: Int = 20): Int =
        (1..total).count { scene ->
            isDone(state, "EP${episode.toString().padStart(2, '0')}_SC${scene.toString().padStart(3, '0')}", kind)
        }

    fun load(workspace: File): JSONObject {
        val file = File(workspace, "scene_checklist.json")
        return if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
    }

    fun save(workspace: File, state: JSONObject) {
        check(workspace.isDirectory || workspace.mkdirs()) { "สร้างพื้นที่บันทึกไม่ได้" }
        val temp = File(workspace, "scene_checklist.json.tmp")
        temp.writeText(state.toString(), Charsets.UTF_8)
        java.nio.file.Files.move(
            temp.toPath(), File(workspace, "scene_checklist.json").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
    }
}
