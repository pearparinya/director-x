package com.pearparinya.automovie.stand

enum class StandAspect(val ratio: String) { VERTICAL("9:16"), LANDSCAPE("16:9") }
data class StandCinematicRequest(val imagePrompt: String, val videoPrompt: String,
    val references: List<StandOriginalReference>, val continuity: StandContinuityFrame)

object StandCinematicEngine {
    fun compose(ownership: StandOwnership, scene: StandStoryScene,
                masters: List<StandMasterRecord>, store: StandMasterStore,
                observerId: String, setting: String, movement: List<StandMovementState>,
                dialogue: List<StandDialogue> = emptyList(),
                aspect: StandAspect = StandAspect.VERTICAL,
                previous: StandContinuityFrame? = null): StandCinematicRequest {
        require(setting.isNotBlank())
        val frame = StandContinuityEngine.capture(ownership, scene, masters, previous)
        val visible = ownership.standsOf(observerId).isNotEmpty()
        val refs = StandContinuityEngine.originalReferences(ownership, scene, masters, store, visible)
        val byId = scene.presences.associateBy { it.standId }
        require(movement.map { it.presence.standId }.distinct().size == movement.size)
        require(movement.all { byId[it.presence.standId] == it.presence })
        val ids = refs.map { it.binding.standId }.toSet()
        val visibleMovement = movement.filter { it.presence.standId in ids }
        val audible = dialogue.filter { line ->
            val speaker = byId[line.speakerId]
            val recipient = byId[line.recipientId]
            when {
                speaker != null -> line.speakerId in ids && line.recipientId == observerId &&
                    speaker.audibleTo(visible, observerId, line.speech)
                recipient != null -> recipient.standId in ids && recipient.ownerId == line.speakerId &&
                    (observerId == line.speakerId || line.speech == StandSpeech.SPOKEN)
                else -> false
            }
        }
        val common = buildString {
            appendLine("Photorealistic cinematic Thai drama. Aspect ratio " + aspect.ratio)
            appendLine("Setting: " + setting)
            appendLine(StandContinuityEngine.VISUAL_LOCK)
            appendLine("Only render the explicitly bound stands. No text, captions or watermark.")
            refs.forEach {
                appendLine("MASTER " + it.binding.standId + " owner " + it.binding.ownerId +
                    " SHA-256 " + it.binding.masterSha256)
            }
            visibleMovement.forEach {
                appendLine(it.presence.standId + " position " + it.position + " movement " + it.movement)
            }
        }
        val image = common + "START STATE: first frame only; no completed action or collage."
        val video = buildString {
            append(common)
            appendLine("8-second continuous shot. Locked camera; no zoom or pan. Preserve identities.")
            audible.forEach {
                appendLine("Dialogue " + it.speakerId + " voice " + (if (it.speakerId in ids) StandPersonas.get(it.speakerId).voiceId else "owner-voice-" + it.speakerId) +
                    " mode " + it.speech + ": " + it.text)
                appendLine(if (it.speech == StandSpeech.TELEPATHIC || !it.lipMovement)
                    "No lip movement." else "Natural lip movement only if present in original MASTER.")
            }
        }
        return StandCinematicRequest(image, video, refs, frame)
    }
}
