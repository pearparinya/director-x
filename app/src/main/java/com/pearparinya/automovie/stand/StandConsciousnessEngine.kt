package com.pearparinya.automovie.stand

enum class StandIntent { WAIT, ADVISE, WARN, PROTECT }
data class StandPersona(val standId: String, val personality: String, val voiceId: String,
                        val highConsciousness: Boolean = false)
object StandPersonas {
    private val traits = listOf("Regal and composed", "Patient and precise", "Resolute and commanding",
        "Calm and disciplined", "Analytical and elusive", "Loyal and steadfast",
        "Inventive and observant", "Quiet and vigilant", "Reflective and expansive",
        "Intense and focused", "Bold and energetic", "Solemn and impartial",
        "Wise and protective", "Assertive and tactical", "Methodical and perceptive", "Hopeful and resilient")
    val all = StandCatalog.all.mapIndexed { index, stand ->
        StandPersona(stand.id, traits[index], "stand-voice-" + stand.id,
            stand.id == StandCatalog.SUPREME_ID)
    }
    fun get(id: String) = all.first { it.standId == id }
}

data class StandDialogue(val speakerId: String, val recipientId: String, val text: String,
                         val speech: StandSpeech, val voiceId: String?, val lipMovement: Boolean)

object StandConsciousnessEngine {
    fun speak(ownership: StandOwnership, presence: StandPresence, recipientId: String,
              text: String, speech: StandSpeech, masterHasMouth: Boolean = false): StandDialogue {
        ownership.requireOwner(presence.standId, presence.ownerId)
        require(text.isNotBlank())
        require(presence.audibleTo(ownership.standsOf(recipientId).isNotEmpty(), recipientId, speech))
        return StandDialogue(presence.standId, recipientId, text, speech,
            StandPersonas.get(presence.standId).voiceId, presence.lipMovement(speech, masterHasMouth))
    }

    fun ownerReply(ownership: StandOwnership, presence: StandPresence, ownerId: String,
                   text: String, speech: StandSpeech): StandDialogue {
        ownership.requireOwner(presence.standId, ownerId)
        require(presence.ownerId == ownerId && presence.summoned && text.isNotBlank())
        return StandDialogue(ownerId, presence.standId, text, speech, null, speech == StandSpeech.SPOKEN)
    }

    fun decide(ownership: StandOwnership, presence: StandPresence,
               ownerThreatened: Boolean, adviceNeeded: Boolean): StandIntent {
        ownership.requireOwner(presence.standId, presence.ownerId)
        val persona = StandPersonas.get(presence.standId)
        return when {
            ownerThreatened && persona.highConsciousness -> StandIntent.PROTECT
            !presence.summoned -> StandIntent.WAIT
            ownerThreatened -> StandIntent.WARN
            adviceNeeded -> StandIntent.ADVISE
            else -> StandIntent.WAIT
        }
    }

    fun react(ownership: StandOwnership, state: StandMovementState,
              ownerThreatened: Boolean): StandMovementState =
        if (decide(ownership, state.presence, ownerThreatened, false) == StandIntent.PROTECT)
            StandMovementEngine.protectIfThreatened(ownership, state, ownerThreatened)
        else state
}
