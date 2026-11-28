package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandOwnershipTest {
    private val pia = StandProject(StandChannel.PIA, "p1")
    private val universe = StandProject(StandChannel.UNIVERSE, "p1")

    @Test fun piaStartsWithOracle() {
        assertEquals("PIEROS", StandOwnership.restore(pia).ownerOf("ST-013"))
    }
    @Test fun universeStartsWithoutPiaOwner() {
        assertNull(StandOwnership.restore(universe).ownerOf("ST-013"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun cannotRemoveOracleFromPia() { StandOwnership.restore(pia).remove("ST-013") }
    @Test(expected = IllegalArgumentException::class)
    fun cannotRestoreWrongPiaOwner() { StandOwnership.restore(pia, mapOf("ST-013" to "other")) }
    @Test(expected = IllegalArgumentException::class)
    fun cannotAssignWrongPiaOwner() { StandOwnership.restore(pia).assign("ST-013", "other") }
    @Test fun projectIsolation() {
        val first = StandOwnership.restore(universe).assign("ST-013", "a")
        val second = StandOwnership.restore(StandProject(StandChannel.UNIVERSE, "p2")).assign("ST-013", "b")
        assertEquals("a", first.ownerOf("ST-013"))
        assertEquals("b", second.ownerOf("ST-013"))
        assertEquals("PIEROS", StandOwnership.restore(pia).ownerOf("ST-013"))
    }
    @Test fun immutableAssignment() {
        val original = StandOwnership.restore(universe)
        val changed = original.assign("ST-001", "a")
        assertNull(original.ownerOf("ST-001"))
        assertEquals("a", changed.ownerOf("ST-001"))
        assertNull(changed.remove("ST-001").ownerOf("ST-001"))
    }
    @Test fun roundTrip() {
        val state = StandOwnership.restore(pia).assign("ST-002", "a")
        assertEquals(state.snapshot(), StandOwnership.restore(pia, state.snapshot()).snapshot())
    }
}
