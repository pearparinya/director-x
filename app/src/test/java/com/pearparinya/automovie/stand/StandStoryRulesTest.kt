package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandStoryRulesTest {
    private val ownership = StandOwnership.restore(StandProject(StandChannel.PIA, "story"))
    private val oracle = StandPresence("ST-013", "PIEROS", true)
    @Test fun tenStoryTypes() { assertEquals(10, StandStoryType.values().size) }
    @Test fun emptyStoryRejected() { assertFalse(StandStoryRules.validate(ownership, emptyList())) }
    @Test fun validStory() { assertTrue(StandStoryRules.validate(ownership, listOf(StandStoryScene(1, listOf(oracle)), StandStoryScene(2, emptyList())))) }
    @Test fun wrongFirstNumberRejected() { assertFalse(StandStoryRules.validate(ownership, listOf(StandStoryScene(2, listOf(oracle))))) }
    @Test fun duplicateNumberRejected() { assertFalse(StandStoryRules.validate(ownership, listOf(StandStoryScene(1, emptyList()), StandStoryScene(1, emptyList())))) }
    @Test fun duplicateStandRejected() { assertFalse(StandStoryRules.validate(ownership, listOf(StandStoryScene(1, listOf(oracle, oracle))))) }
    @Test fun wrongOwnerRejected() { assertFalse(StandStoryRules.validate(ownership, listOf(StandStoryScene(1, listOf(oracle.copy(ownerId = "other")))))) }
    @Test fun hundredScenesAccepted() { assertTrue(StandStoryRules.validate(ownership, (1..100).map { StandStoryScene(it, listOf(oracle)) })) }
}
