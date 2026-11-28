package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandCombatEngineTest {
    private val ownership = StandOwnership.restore(StandProject(StandChannel.PIA, "fight"))
        .assign("ST-001", "rival")
    private val a = StandFighter(StandPresence("ST-013", "PIEROS", true))
    private val b = StandFighter(StandPresence("ST-001", "rival", true))
    private val attack = StandCombatAction.ATTACK
    private val guard = StandCombatAction.GUARD
    private val special = StandCombatAction.SPECIAL

    @Test fun allProfilesMatchCatalog() {
        assertEquals(16, StandCombatCatalog.all.size)
        assertEquals(StandCatalog.all.map { it.id }.toSet(), StandCombatCatalog.all.map { it.id }.toSet())
        assertEquals(StandRank.EX, StandCombatCatalog.profile("ST-013").rank)
    }
    @Test fun fasterFighterActsFirst() {
        assertTrue(StandCombatEngine.resolve(ownership, a, b, attack, attack).firstActsFirst)
    }
    @Test fun guardReducesDamage() {
        val guarded = StandCombatEngine.resolve(ownership, a, b, attack, guard)
        val open = StandCombatEngine.resolve(ownership, a, b, attack, attack)
        assertTrue(guarded.second.health > open.second.health)
    }
    @Test fun specialCostsEnergy() {
        val result = StandCombatEngine.resolve(ownership, a, b, special, guard)
        assertEquals(80, result.first.energy) // 100 - 30 cost + 10 Oracle recovery
        assertEquals(90, result.second.energy) // Oracle drains 10
        val ordinary = StandCombatEngine.resolve(ownership, a, b, guard, special)
        assertEquals(70, ordinary.second.energy)
    }
    @Test fun everyStandHasDistinctAbilityEffect() {
        assertEquals(16, StandCatalog.all.map { StandUniqueAbility.effect(it.id) }.toSet().size)
    }
    @Test fun defeatedOpponentCannotCounter() {
        val result = StandCombatEngine.resolve(ownership, a, b.copy(health = 1), attack, attack)
        assertEquals(0, result.second.health)
        assertEquals(100, result.first.health)
        assertEquals(100, result.second.energy)
    }
    @Test(expected = IllegalArgumentException::class)
    fun insufficientEnergyRejected() {
        StandCombatEngine.resolve(ownership, a.copy(energy = 0), b, special, guard)
    }
    @Test(expected = IllegalArgumentException::class)
    fun unsummonedFighterRejected() {
        StandCombatEngine.resolve(ownership, a.copy(presence = a.presence.dismiss()), b, attack, attack)
    }
    @Test(expected = IllegalArgumentException::class)
    fun wrongOwnerRejected() {
        StandCombatEngine.resolve(ownership, a.copy(presence = a.presence.copy(ownerId = "other")), b, attack, attack)
    }
}
