package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandConsciousnessEngineTest {
    private val ownership = StandOwnership.restore(StandProject(StandChannel.PIA, "dialogue"))
        .assign("ST-001", "rival")
    private val supreme = StandPresence("ST-013", "PIEROS", true)
    private val engine = StandConsciousnessEngine

    @Test fun uniquePersonasAndVoices() {
        assertEquals(16, StandPersonas.all.size)
        assertEquals(16, StandPersonas.all.map { it.voiceId }.toSet().size)
        assertEquals(16, StandPersonas.all.map { it.personality }.toSet().size)
        assertTrue(StandPersonas.get("ST-013").highConsciousness)
    }
    @Test fun telepathyNeverMovesLips() {
        val line = engine.speak(ownership, supreme, "PIEROS", "warning", StandSpeech.TELEPATHIC, true)
        assertFalse(line.lipMovement)
        assertEquals("ST-013", line.speakerId)
    }
    @Test fun mouthlessStandDoesNotMoveLips() {
        assertFalse(engine.speak(ownership, supreme, "rival", "warning", StandSpeech.SPOKEN).lipMovement)
    }
    @Test fun spokenDialogueWithMouthCanMoveLips() {
        assertTrue(engine.speak(ownership, supreme, "rival", "warning", StandSpeech.SPOKEN, true).lipMovement)
    }
    @Test(expected = IllegalArgumentException::class)
    fun civilianCannotHear() {
        engine.speak(ownership, supreme, "civilian", "warning", StandSpeech.SPOKEN)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rivalCannotHearPrivateTelepathy() {
        engine.speak(ownership, supreme, "rival", "warning", StandSpeech.TELEPATHIC)
    }
    @Test(expected = IllegalArgumentException::class)
    fun hiddenStandCannotSpeak() {
        engine.speak(ownership, supreme.dismiss(), "PIEROS", "warning", StandSpeech.TELEPATHIC)
    }
    @Test fun ownerReplyHasSeparateSpeaker() {
        val reply = engine.ownerReply(ownership, supreme, "PIEROS", "ready", StandSpeech.TELEPATHIC)
        assertEquals("PIEROS", reply.speakerId)
        assertEquals("ST-013", reply.recipientId)
        assertFalse(reply.lipMovement)
    }
    @Test fun supremeProtectsEvenWhenHidden() {
        val initial = StandMovementEngine.initial(ownership, "ST-013", "PIEROS", StandPosition(0.0, 0.0))
        assertEquals(StandIntent.PROTECT, engine.decide(ownership, initial.presence, true, false))
        assertEquals(StandMovement.PROTECTING, engine.react(ownership, initial, true).movement)
    }
    @Test fun ordinaryStandWarnsAndAdvises() {
        val rival = StandPresence("ST-001", "rival", true)
        assertEquals(StandIntent.WARN, engine.decide(ownership, rival, true, false))
        assertEquals(StandIntent.ADVISE, engine.decide(ownership, rival, false, true))
    }
}
