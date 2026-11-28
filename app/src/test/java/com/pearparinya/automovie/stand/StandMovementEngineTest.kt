package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandMovementEngineTest {
    private val ownership = StandOwnership.restore(StandProject(StandChannel.PIA, "movement"))
        .assign("ST-001", "rival")
    private val origin = StandPosition(0.0, 0.0)
    private val initial = StandMovementEngine.initial(ownership, "ST-013", "PIEROS", origin)
    private val summoned = StandMovementEngine.summon(ownership, initial)

    @Test fun startsHidden() {
        assertFalse(initial.presence.summoned)
        assertEquals(StandMovement.HIDDEN, initial.movement)
    }
    @Test fun summonBesideOwner() {
        assertTrue(summoned.presence.summoned)
        assertEquals(StandPosition(1.0, 0.0), summoned.position)
    }
    @Test fun followsMovingOwner() {
        val moved = StandMovementEngine.moveOwner(ownership, summoned, StandPosition(5.0, 3.0))
        assertEquals(StandPosition(6.0, 3.0), moved.position)
    }
    @Test fun independentMovementDoesNotFollow() {
        val independent = StandMovementEngine.moveStand(ownership, summoned, StandPosition(8.0, 2.0))
        val moved = StandMovementEngine.moveOwner(ownership, independent, StandPosition(5.0, 3.0))
        assertEquals(independent.position, moved.position)
        assertEquals(StandMovement.INDEPENDENT, moved.movement)
    }
    @Test fun combatAndReturnToOwner() {
        val combat = StandMovementEngine.moveStand(ownership, summoned, StandPosition(8.0, 2.0), true)
        assertEquals(StandMovement.COMBAT, combat.movement)
        assertEquals(summoned, StandMovementEngine.followOwner(ownership, combat))
    }
    @Test fun onlyStandUsersSeeSummonedStand() {
        assertTrue(StandMovementEngine.visibleTo(ownership, summoned, "PIEROS"))
        assertTrue(StandMovementEngine.visibleTo(ownership, summoned, "rival"))
        assertFalse(StandMovementEngine.visibleTo(ownership, summoned, "civilian"))
        assertFalse(StandMovementEngine.visibleTo(ownership, initial, "PIEROS"))
    }
    @Test fun dismissHidesStand() {
        assertEquals(initial, StandMovementEngine.dismiss(ownership, summoned))
    }
    @Test fun supremeAutomaticallyProtects() {
        val protected = StandMovementEngine.protectIfThreatened(ownership, initial, true)
        assertTrue(protected.presence.summoned)
        assertEquals(StandMovement.PROTECTING, protected.movement)
        assertEquals(initial, StandMovementEngine.protectIfThreatened(ownership, initial, false))
    }
    @Test fun ordinaryStandDoesNotAutoSummon() {
        val rival = StandMovementEngine.initial(ownership, "ST-001", "rival", origin)
        assertEquals(rival, StandMovementEngine.protectIfThreatened(ownership, rival, true))
    }
    @Test(expected = IllegalArgumentException::class)
    fun hiddenStandCannotMoveIndependently() {
        StandMovementEngine.moveStand(ownership, initial, origin)
    }
    @Test(expected = IllegalArgumentException::class)
    fun wrongOwnerRejected() {
        StandMovementEngine.initial(ownership, "ST-013", "rival", origin)
    }
}
