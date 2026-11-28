package com.pearparinya.automovie.stand

data class StandVisualBinding(val standId: String, val ownerId: String, val masterSha256: String)

object StandCinematicRules {
    fun bindings(ownership: StandOwnership, scene: StandStoryScene,
                 masters: List<StandMasterRecord>, observerIsStandUser: Boolean): List<StandVisualBinding> {
        require(StandStoryRules.validate(ownership, listOf(scene.copy(number = 1))))
        require(StandMasterIntegrity.complete(masters))
        val byId = masters.associateBy { it.standId }
        return scene.presences.filter { it.visibleTo(observerIsStandUser) }.map {
            StandVisualBinding(it.standId, it.ownerId, byId.getValue(it.standId).sha256)
        }
    }
}
