package com.pearparinya.automovie.stand

enum class StandCombatAction { ATTACK, GUARD, SPECIAL }
enum class StandRank { A, S, EX }

data class StandCombatProfile(val id: String, val power: Int, val speed: Int,
    val defense: Int, val ability: String, val weakness: String, val rank: StandRank = StandRank.S)

object StandCombatCatalog {
    val all = listOf(
        StandCombatProfile("ST-001", 82, 82, 78, "Violet pressure", "Sustained defense"),
        StandCombatProfile("ST-002", 78, 95, 65, "Time distortion", "Recovery interval"),
        StandCombatProfile("ST-003", 94, 65, 86, "Gravity field", "Fast flanking"),
        StandCombatProfile("ST-004", 83, 75, 88, "Polar field", "Heat pressure"),
        StandCombatProfile("ST-005", 72, 88, 60, "Mental disruption", "Mental resistance"),
        StandCombatProfile("ST-006", 92, 55, 98, "Iron impact", "Low mobility"),
        StandCombatProfile("ST-007", 75, 90, 72, "Crystal illusion", "Area detection"),
        StandCombatProfile("ST-008", 76, 99, 58, "Silent strike", "Area defense"),
        StandCombatProfile("ST-009", 96, 88, 91, "Cosmic surge", "Energy recovery"),
        StandCombatProfile("ST-010", 90, 92, 68, "Crimson slash", "Counter guard"),
        StandCombatProfile("ST-011", 98, 60, 85, "Inferno wave", "Cooling field"),
        StandCombatProfile("ST-012", 87, 73, 92, "Judgment seal", "Seal interruption"),
        StandCombatProfile("ST-013", 100, 100, 100, "Absolute sovereignty", "Energy expenditure", StandRank.EX),
        StandCombatProfile("ST-014", 95, 85, 82, "Warlord assault", "Prolonged combat"),
        StandCombatProfile("ST-015", 80, 97, 70, "Destiny shift", "Recovery interval"),
        StandCombatProfile("ST-016", 93, 91, 80, "Solar flare", "Energy recovery")
    )
    fun profile(id: String) = all.first { it.id == id }
}

data class StandFighter(val presence: StandPresence, val health: Int = 100, val energy: Int = 100) {
    init { require(health in 0..100 && energy in 0..100) }
}
data class StandCombatTurn(val first: StandFighter, val second: StandFighter, val firstActsFirst: Boolean)

object StandCombatEngine {
    fun resolve(ownership: StandOwnership, first: StandFighter, second: StandFighter,
                firstAction: StandCombatAction, secondAction: StandCombatAction): StandCombatTurn {
        require(StandSceneRules.canFight(ownership, first.presence, second.presence))
        require(first.health > 0 && second.health > 0)
        fun cost(action: StandCombatAction) = when(action) {
            StandCombatAction.ATTACK -> 10
            StandCombatAction.GUARD -> 0
            StandCombatAction.SPECIAL -> 30
        }
        require(first.energy >= cost(firstAction) && second.energy >= cost(secondAction))
        val a = StandCombatCatalog.profile(first.presence.standId)
        val b = StandCombatCatalog.profile(second.presence.standId)
        val ea = if (firstAction == StandCombatAction.SPECIAL) StandUniqueAbility.effect(a.id) else StandAbilityEffect()
        val eb = if (secondAction == StandCombatAction.SPECIAL) StandUniqueAbility.effect(b.id) else StandAbilityEffect()
        fun damage(attacker: StandCombatProfile, defender: StandCombatProfile,
                   action: StandCombatAction, response: StandCombatAction,
                   attack: StandAbilityEffect, defense: StandAbilityEffect): Int {
            if (action == StandCombatAction.GUARD) return 0
            val raw = (attacker.power / 3 - defender.defense / 6 +
                (if (action == StandCombatAction.SPECIAL) 15 else 0) +
                attack.damageBonus - defense.shield).coerceAtLeast(1)
            return if (response == StandCombatAction.GUARD) (raw / 2).coerceAtLeast(1) else raw
        }
        val firstActsFirst = a.speed + ea.initiative >= b.speed + eb.initiative
        val hitA = damage(b, a, secondAction, firstAction, eb, ea)
        val hitB = damage(a, b, firstAction, secondAction, ea, eb)
        val aActs = firstActsFirst || first.health > hitA
        val bActs = !firstActsFirst || second.health > hitB
        return StandCombatTurn(
            first.copy(health = (first.health - if (bActs) hitA else 0).coerceAtLeast(0),
                energy = (first.energy - (if (aActs) cost(firstAction) else 0) +
                    (if (aActs) ea.recovery else 0) - (if (bActs) eb.drain else 0)).coerceIn(0,100)),
            second.copy(health = (second.health - if (aActs) hitB else 0).coerceAtLeast(0),
                energy = (second.energy - (if (bActs) cost(secondAction) else 0) +
                    (if (bActs) eb.recovery else 0) - (if (aActs) ea.drain else 0)).coerceIn(0,100)),
            firstActsFirst)
    }
}
