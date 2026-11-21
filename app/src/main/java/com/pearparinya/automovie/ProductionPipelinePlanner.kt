package com.pearparinya.automovie

/**
 * Pure decision layer for production continuation. Artifact validation belongs to
 * callers; saved UI flags and resume cursors must never be treated as proof.
 */
internal object ProductionPipelinePlanner {
    enum class Stage { STORY, FLOW, IMAGE, VIDEO, FINAL_OUTPUT, DONE }
    data class Decision(val stage: Stage, val episode: Int = 0, val scene: Int = 0)

    data class Episode(
        val number: Int,
        val sceneIds: List<Int>,
        val flowVerified: Boolean,
        val imageIdsVerified: Set<Int>,
        val videoIdsVerified: Set<Int> = emptySet()
    )

    fun next(
        storyValid: Boolean,
        episodes: List<Episode>,
        finalOutputVerified: Boolean = false
    ): Decision {
        if (!storyValid || episodes.isEmpty() || episodes.any {
                it.number <= 0 || it.sceneIds.isEmpty() ||
                    it.sceneIds.any { id -> id <= 0 } ||
                    it.sceneIds.distinct().size != it.sceneIds.size
            } || episodes.map { it.number }.distinct().size != episodes.size
        ) return Decision(Stage.STORY)
        episodes.firstOrNull { !it.flowVerified }?.let {
            return Decision(Stage.FLOW, it.number)
        }
        for (ep in episodes) {
            ep.sceneIds.firstOrNull { it !in ep.imageIdsVerified }?.let {
                return Decision(Stage.IMAGE, ep.number, it)
            }
        }
        for (ep in episodes) {
            ep.sceneIds.firstOrNull { it !in ep.videoIdsVerified }?.let {
                return Decision(Stage.VIDEO, ep.number, it)
            }
        }
        return if (finalOutputVerified) Decision(Stage.DONE)
        else Decision(Stage.FINAL_OUTPUT)
    }
}
