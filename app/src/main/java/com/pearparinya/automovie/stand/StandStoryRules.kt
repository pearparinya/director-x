package com.pearparinya.automovie.stand

enum class StandStoryType {
    ORIGIN, RIVALRY, INVESTIGATION, RESCUE, TOURNAMENT,
    ALLIANCE, BETRAYAL, TRAINING, SUPERNATURAL_MYSTERY, FINAL_CONFLICT
}

data class StandStoryScene(val number: Int, val presences: List<StandPresence>)

object StandStoryRules {
    fun validate(ownership: StandOwnership, scenes: List<StandStoryScene>): Boolean {
        if (scenes.isEmpty()) return false
        return scenes.withIndex().all { (index, scene) ->
            scene.number == index + 1 &&
                scene.presences.map { it.standId }.distinct().size == scene.presences.size &&
                scene.presences.all { StandSceneRules.validate(ownership, it) }
        }
    }
}
