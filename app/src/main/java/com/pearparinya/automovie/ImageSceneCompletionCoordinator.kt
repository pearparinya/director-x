package com.pearparinya.automovie

/**
 * Coordinates one pending scene. The image must be durably saved before progress advances.
 * No image provider is called here; the caller supplies the image persistence operation.
 */
internal object ImageSceneCompletionCoordinator {
    fun completeNext(
        current: ImageSceneQueue.State,
        saveImage: (sceneId: Int) -> Boolean,
        saveProgress: (ImageSceneQueue.State) -> Boolean
    ): ImageSceneQueue.State? {
        val next = current.nextPendingScene ?: return current
        // Treat exceptions like failed writes: callers must not advance the queue.
        if (runCatching { saveImage(next) }.getOrDefault(false).not()) return null
        val updated = current.markCompleted(next)
        return if (runCatching { saveProgress(updated) }.getOrDefault(false)) updated else null
    }
    /**
     * Save all pending scenes in queue order. Stop at the first failed image or
     * progress write; only successfully persisted completions are returned.
     * Callers must not invoke this on the main thread.
     */
    fun completePending(
        current: ImageSceneQueue.State,
        saveImage: (sceneId: Int) -> Boolean,
        saveProgress: (ImageSceneQueue.State) -> Boolean,
        shouldContinue: () -> Boolean = { true }
    ): ImageSceneQueue.State {
        var state = current
        while (state.nextPendingScene != null) {
            // Check between scenes so a cancellation never marks an unfinished scene complete.
            if (!runCatching { shouldContinue() }.getOrDefault(false)) break
            val updated = completeNext(state, saveImage, saveProgress) ?: break
            state = updated
        }
        return state
    }

}
