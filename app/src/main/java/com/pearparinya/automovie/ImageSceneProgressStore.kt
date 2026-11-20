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

    /**
     * Merge with saved progress before a synchronous commit. Synchronize on the
     * SharedPreferences instance so multiple store objects in this process cannot
     * overwrite completed scene IDs with stale worker snapshots.
     */
    fun save(projectId: String, state: ImageSceneQueue.State): Boolean =
        synchronized(preferences) {
            val merged = ImageSceneProgressMerge.merge(
                load(projectId, state.sceneIds), state
            )
            preferences.edit().putString(
                storageKey(projectId, state.sceneIds), merged.encodeProgress()
            ).commit()
        }

    /** Refresh a worker snapshot before selecting its next pending scene. */
    private fun refreshed(projectId: String, current: ImageSceneQueue.State) =
        ImageSceneProgressMerge.merge(load(projectId, current.sceneIds), current)

    /**
     * Mark a scene complete only after its image has been durably saved by the caller.
     * If the preference write fails, return null so the caller does not advance the queue.
     */
    fun recordCompletedScene(
        projectId: String,
        current: ImageSceneQueue.State,
        sceneId: Int
    ): ImageSceneQueue.State? = synchronized(preferences) {
        val latest = refreshed(projectId, current)
        // This method only records already-durable images; do not start image work here.
        val updated = latest.markCompleted(sceneId)
        if (save(projectId, updated)) load(projectId, current.sceneIds) else null
    }

    /**
     * Serialize work across store instances in this process. The lock is held
     * through image persistence, so callers must run off the main thread.
     * Run the next unfinished scene's durable image save before persisting completion.
     * A null result means image or progress persistence failed; do not advance the UI.
     */
    fun saveNextSceneAndProgress(
        projectId: String,
        current: ImageSceneQueue.State,
        saveImage: (sceneId: Int) -> Boolean
    ): ImageSceneQueue.State? = synchronized(preferences) {
        ImageSceneCompletionCoordinator.completeNext(
            refreshed(projectId, current),
            saveImage = saveImage,
            saveProgress = { updated -> save(projectId, updated) }
        )?.let { load(projectId, current.sceneIds) }
    }

    /**
     * Batch processing entry point for a worker/background executor.
     * The caller must save each generated image durably before returning true.
     */
    fun savePendingScenesAndProgress(
        projectId: String,
        current: ImageSceneQueue.State,
        saveImage: (sceneId: Int) -> Boolean,
        shouldContinue: () -> Boolean = { true },
        maxScenes: Int = Int.MAX_VALUE
    ): ImageSceneQueue.State = synchronized(preferences) {
        ImageSceneCompletionCoordinator.completePending(
            refreshed(projectId, current),
            saveImage = saveImage,
            saveProgress = { updated -> save(projectId, updated) },
            shouldContinue = shouldContinue,
            maxScenes = maxScenes
        )
        load(projectId, current.sceneIds)
    }

    /**
     * Run at most five pending scenes in queue order. A scene is marked completed
     * only after the caller durably saves its image and progress is committed.
     * Safe to call again after interruption: already-completed IDs are skipped.
     * Must be called from a background worker, never the main thread.
     */
    fun saveNextFiveScenesAndProgress(
        projectId: String,
        current: ImageSceneQueue.State,
        saveImage: (sceneId: Int) -> Boolean,
        shouldContinue: () -> Boolean = { true }
    ): ImageSceneQueue.State = savePendingScenesAndProgress(
        projectId = projectId,
        current = current,
        saveImage = saveImage,
        shouldContinue = shouldContinue,
        maxScenes = 5
    )

    /**
     * Executes one planned free-tier batch and commits each completed scene.
     * Provider credentials and HTTP calls remain the responsibility of the caller.
     */
    fun runFreeTierBatch(
        projectId: String,
        current: ImageSceneQueue.State,
        providers: List<ImageProviderRotation.Provider>,
        nowEpochMs: Long,
        requiresReferenceImage: Boolean,
        requestedBatchSize: Int,
        saveImage: (providerId: String, sceneId: Int) -> Boolean,
        shouldContinue: () -> Boolean = { true },
        previousProviderId: String? = null
    ): ImageFreeTierSceneRunner.Result = synchronized(preferences) {
        val result = ImageFreeTierSceneRunner.run(
            current = refreshed(projectId, current),
            providers = providers,
            nowEpochMs = nowEpochMs,
            requiresReferenceImage = requiresReferenceImage,
            requestedBatchSize = requestedBatchSize,
            saveImage = saveImage,
            saveProgress = { updated -> save(projectId, updated) },
            shouldContinue = shouldContinue,
            previousProviderId = previousProviderId
        )
        result.copy(state = load(projectId, current.sceneIds))
    }

    fun clear(projectId: String, sceneIds: List<Int>): Boolean =
        synchronized(preferences) {
            preferences.edit().remove(storageKey(projectId, sceneIds)).commit()
        }

    private fun storageKey(projectId: String, sceneIds: List<Int>): String {
        require(projectId.isNotBlank()) { "Project ID is required" }
        val input = projectId + "\n" + sceneIds.joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
