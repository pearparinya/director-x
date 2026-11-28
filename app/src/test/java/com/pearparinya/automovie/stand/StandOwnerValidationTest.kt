package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandOwnerValidationTest {
    private val pia = StandProject(StandChannel.PIA, "episode-1")
    private val universe = StandProject(StandChannel.UNIVERSE, "episode-1")

    @Test fun pierosOwnsSupremeByDefault() {
        val state = StandOwnership.restore(pia)
        assertEquals(setOf("ST-013"), state.standsOf("PIEROS"))
        state.requireOwner("ST-013", "PIEROS")
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongOwnerCannotControlSupreme() {
        StandOwnership.restore(pia).requireOwner("ST-013", "other")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unassignedStandCannotActForOwner() {
        StandOwnership.restore(universe).requireOwner("ST-001", "other")
    }

    @Test fun sameProjectIdDoesNotShareChannelOwnership() {
        val a = StandOwnership.restore(pia)
        val b = StandOwnership.restore(universe).assign("ST-013", "other")
        assertTrue(a.isOwner("ST-013", "PIEROS"))
        assertTrue(b.isOwner("ST-013", "other"))
        assertFalse(b.isOwner("ST-013", "PIEROS"))
    }

    @Test fun explicitAssignmentDoesNotMutatePreviousState() {
        val a = StandOwnership.restore(universe)
        val b = a.assign("ST-001", "owner")
        assertTrue(a.standsOf("owner").isEmpty())
        assertEquals(setOf("ST-001"), b.standsOf("owner"))
    }

    @Test fun blankOwnerNeverMatches() {
        assertFalse(StandOwnership.restore(universe).isOwner("ST-001", ""))
    }
}
