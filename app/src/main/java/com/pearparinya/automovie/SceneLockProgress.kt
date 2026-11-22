package com.pearparinya.automovie

import org.json.JSONObject
import java.io.File

/** Episode-level counters and resume selection for independently approved media. */
internal object SceneLockProgress {
    data class Summary(val total: Int, val approved: Int, val nextSceneId: String?) {
        val label: String get() = "🔒 $approved/$total"
    }

    /**
     * sceneIds must be ordered in story sequence. Missing files are not considered approved.
     * The caller resolves artifacts from its own story/workspace model.
     */
    fun summarize(
        state: JSONObject,
        sceneIds: List<String>,
        medium: SceneLocks.Medium,
        artifactFor: (String) -> File?
    ): Summary {
        val ordered = sceneIds.distinct()
        var approved = 0
        var next: String? = null
        ordered.forEach { id ->
            if (SceneLocks.isLocked(state, id, medium, artifactFor(id))) {
                approved++
            } else if (next == null) {
                next = id
            }
        }
        return Summary(ordered.size, approved, next)
    }
}
