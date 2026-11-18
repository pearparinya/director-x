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
        if (!saveImage(next)) return null
        val updated = current.markCompleted(next)
        return if (saveProgress(updated)) updated else null
    }
}
