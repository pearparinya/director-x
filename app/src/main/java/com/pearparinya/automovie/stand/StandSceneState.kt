package com.pearparinya.automovie.stand

enum class StandSpeech { SPOKEN, TELEPATHIC }
data class StandPresence(val standId: String, val ownerId: String, val summoned: Boolean = false) {
    init {
        require(StandCatalog.find(standId) != null)
        require(ownerId.isNotBlank())
    }
    fun summon() = copy(summoned = true)
    fun dismiss() = copy(summoned = false)
    fun visibleTo(observerIsStandUser: Boolean) = summoned && observerIsStandUser
    fun audibleTo(observerIsStandUser: Boolean, observerId: String, speech: StandSpeech): Boolean =
        summoned && observerIsStandUser && (speech == StandSpeech.SPOKEN || observerId == ownerId)
    fun lipMovement(speech: StandSpeech, hasMouth: Boolean) =
        summoned && speech == StandSpeech.SPOKEN && hasMouth
}

object StandSceneRules {
    fun validate(project: StandOwnership, presence: StandPresence): Boolean =
        project.ownerOf(presence.standId) == presence.ownerId
    fun canFight(project: StandOwnership, first: StandPresence, second: StandPresence): Boolean =
        first.summoned && second.summoned && first.standId != second.standId &&
            first.ownerId != second.ownerId && validate(project, first) && validate(project, second)
}
