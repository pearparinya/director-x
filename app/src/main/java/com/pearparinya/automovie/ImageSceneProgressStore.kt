package com.pearparinya.automovie

import android.content.Context
import java.security.MessageDigest

/**
 * Persists completed image scenes per project. No API keys or image data are stored here.
 * The fingerprint prevents progress from a changed scene list being applied accidentally.
 */
internal class ImageSceneProgressStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "director_x_image_scene_progress", Context.MODE_PRIVATE
    )

    fun load(projectId: String, sceneIds: List<Int>): ImageSceneQueue.State {
        val key = storageKey(projectId, sceneIds)
        val saved = preferences.getString(key, "") ?: ""
        return try {
            ImageSceneQueue.restore(sceneIds, saved)
        } catch (_: IllegalArgumentException) {
            ImageSceneQueue.State(sceneIds)
        }
    }

    /** Commit synchronously so a completed scene survives process termination. */
    fun save(projectId: String, state: ImageSceneQueue.State): Boolean =
        preferences.edit().putString(
            storageKey(projectId, state.sceneIds), state.encodeProgress()
        ).commit()

    /**
     * Mark a scene complete only after its image has been durably saved by the caller.
     * If the preference write fails, return null so the caller does not advance the queue.
     */
    fun recordCompletedScene(
        projectId: String,
        current: ImageSceneQueue.State,
        sceneId: Int
    ): ImageSceneQueue.State? {
        val updated = current.markCompleted(sceneId)
        return if (save(projectId, updated)) updated else null
    }

    fun clear(projectId: String, sceneIds: List<Int>): Boolean =
        preferences.edit().remove(storageKey(projectId, sceneIds)).commit()

    private fun storageKey(projectId: String, sceneIds: List<Int>): String {
        require(projectId.isNotBlank()) { "Project ID is required" }
        val input = projectId + "\n" + sceneIds.joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
