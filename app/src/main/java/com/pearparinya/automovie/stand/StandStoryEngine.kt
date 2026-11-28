package com.pearparinya.automovie.stand

enum class StandWritingMode { KOTLIN, AI_KOTLIN }

data class StandEpisode(
    val project: StandProject,
    val number: Int,
    val type: StandStoryType,
    val mode: StandWritingMode,
    val scenes: List<StandStoryScene>
)

object StandStoryEngine {
    fun validate(ownership: StandOwnership, episode: StandEpisode,
                 previous: StandEpisode? = null): Boolean {
        if (episode.project != ownership.project || episode.number < 1) return false
        if (previous == null && episode.number != 1) return false
        if (previous != null && (previous.project != episode.project ||
                previous.number != episode.number - 1 ||
                !StandStoryRules.validate(ownership, previous.scenes))) return false
        return StandStoryRules.validate(ownership, episode.scenes)
    }

    fun nextScene(ownership: StandOwnership, episode: StandEpisode,
                  presences: List<StandPresence>): StandEpisode {
        require(episode.project == ownership.project)
        val candidate = episode.copy(scenes = episode.scenes +
            StandStoryScene(episode.scenes.size + 1, presences.toList()))
        require(StandStoryRules.validate(ownership, candidate.scenes))
        return candidate
    }

    fun ownershipContext(ownership: StandOwnership): String =
        ownership.snapshot().toSortedMap().entries.joinToString("\n") {
            (stand, owner) -> "$stand => $owner"
        }
}
