package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandStoryProductionTest {
    @Test fun allTypesAndChannelsProduceValidScenes() {
        for (channel in StandChannel.values()) for (type in StandStoryType.values()) {
            val initial = StandOwnership.restore(StandProject(channel, "production"))
            val result = StandStoryProduction.generate(initial, type)
            assertEquals(4, result.beats.size)
            assertTrue(StandStoryEngine.validate(result.ownership, result.episode))
            assertTrue(result.beats[2].narrative.contains("พลังชีวิต"))
            assertTrue(initial.snapshot().all { (id, owner) -> result.ownership.isOwner(id, owner) })
        }
    }
    @Test fun existingOwnersPreserved() {
        val initial = StandOwnership.restore(StandProject(StandChannel.UNIVERSE, "existing"))
            .assign("ST-001", "alice").assign("ST-002", "bob")
        val result = StandStoryProduction.generate(initial, StandStoryType.RIVALRY)
        assertEquals(initial.snapshot(), result.ownership.snapshot())
    }
    @Test fun piaLockRetained() {
        val initial = StandOwnership.restore(StandProject(StandChannel.PIA, "pia"))
        val result = StandStoryProduction.generate(initial, StandStoryType.RESCUE)
        assertEquals(StandCatalog.PIEROS, result.ownership.ownerOf("ST-013"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun aiModeNeverSilentlyFallsBack() {
        StandStoryProduction.generate(StandOwnership.restore(StandProject(StandChannel.PIA, "ai")),
            StandStoryType.ORIGIN, StandWritingMode.AI_KOTLIN)
    }
}
