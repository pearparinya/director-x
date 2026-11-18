package com.pearparinya.automovie

/**
 * Immutable queue progress for scene image generation. Persist completed scene IDs
 * and restore them after process death; never regenerate already completed scenes.
 */
internal object ImageSceneQueue {
    /** Restore only validated saved scene IDs for the current project. */
    fun restore(sceneIds: List<Int>, savedProgress: String): State {
        val completed = if (savedProgress.isBlank()) emptySet() else
            savedProgress.split(",").map { token ->
                token.trim().toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("Invalid saved scene ID")
            }.toSet()
        return State(sceneIds, completed)
    }

    data class State(
        val sceneIds: List<Int>,
        val completedSceneIds: Set<Int> = emptySet()
    ) {
        init {
            require(sceneIds.all { it > 0 }) { "Scene IDs must be positive" }
            require(sceneIds.distinct().size == sceneIds.size) { "Duplicate scene IDs" }
            require(completedSceneIds.all { it in sceneIds }) { "Unknown completed scene" }
        }

        val completedCount: Int get() = completedSceneIds.size
        val totalCount: Int get() = sceneIds.size
        val progressPercent: Int get() =
            if (totalCount == 0) 100 else completedCount * 100 / totalCount
        val nextPendingScene: Int? get() = sceneIds.firstOrNull { it !in completedSceneIds }

        fun encodeProgress(): String = completedSceneIds.sorted().joinToString(",")

        fun markCompleted(sceneId: Int): State {
            require(sceneId in sceneIds) { "Unknown scene ID" }
            return copy(completedSceneIds = completedSceneIds + sceneId)
        }
    }
}
