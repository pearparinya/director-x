package com.pearparinya.automovie

/**
 * Preserves all durably completed scene IDs when an older worker saves its state.
 * Both states must belong to the same ordered scene list.
 */
internal object ImageSceneProgressMerge {
    fun merge(
        persisted: ImageSceneQueue.State,
        incoming: ImageSceneQueue.State
    ): ImageSceneQueue.State {
        require(persisted.sceneIds == incoming.sceneIds) {
            "Cannot merge progress from different scene lists"
        }
        return persisted.copy(
            completedSceneIds = persisted.completedSceneIds + incoming.completedSceneIds
        )
    }
}
