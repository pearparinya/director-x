package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandSceneStateTest {
    private val oracle = StandPresence("ST-013", "PIEROS")
    private val other = StandPresence("ST-001", "rival", true)
    private val project = StandOwnership.restore(StandProject(StandChannel.PIA, "scene")).assign("ST-001", "rival")
    @Test fun initiallyHidden() { assertFalse(oracle.visibleTo(true)) }
    @Test fun summonedVisibleToUsers() { assertTrue(oracle.summon().visibleTo(true)) }
    @Test fun ordinaryHumansCannotSee() { assertFalse(oracle.summon().visibleTo(false)) }
    @Test fun ordinaryHumansCannotHear() { assertFalse(oracle.summon().audibleTo(false, "human", StandSpeech.SPOKEN)) }
    @Test fun usersCanHearSpeech() { assertTrue(oracle.summon().audibleTo(true, "rival", StandSpeech.SPOKEN)) }
    @Test fun telepathyPrivate() {
        assertTrue(oracle.summon().audibleTo(true, "PIEROS", StandSpeech.TELEPATHIC))
        assertFalse(oracle.summon().audibleTo(true, "rival", StandSpeech.TELEPATHIC))
    }
    @Test fun telepathyNoLipMovement() { assertFalse(oracle.summon().lipMovement(StandSpeech.TELEPATHIC, true)) }
    @Test fun mouthlessNoLipMovement() { assertFalse(oracle.summon().lipMovement(StandSpeech.SPOKEN, false)) }
    @Test fun dismissHidesStand() { assertFalse(oracle.summon().dismiss().visibleTo(true)) }
    @Test fun fightRequiresSummoning() { assertFalse(StandSceneRules.canFight(project, oracle, other)) }
    @Test fun validCombat() { assertTrue(StandSceneRules.canFight(project, oracle.summon(), other)) }
    @Test fun wrongOwnerRejected() { assertFalse(StandSceneRules.validate(project, oracle.copy(ownerId = "rival"))) }
    @Test fun cannotFightSelf() { assertFalse(StandSceneRules.canFight(project, other, other)) }
}
