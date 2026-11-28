package com.pearparinya.automovie.stand

data class StandAbilityEffect(val damageBonus: Int = 0, val shield: Int = 0,
    val initiative: Int = 0, val recovery: Int = 0, val drain: Int = 0)

object StandUniqueAbility {
    fun effect(id: String): StandAbilityEffect = when(id) {
        "ST-001" -> StandAbilityEffect(damageBonus=8, shield=4)
        "ST-002" -> StandAbilityEffect(initiative=30)
        "ST-003" -> StandAbilityEffect(damageBonus=12, initiative=5)
        "ST-004" -> StandAbilityEffect(shield=14, drain=3)
        "ST-005" -> StandAbilityEffect(drain=18)
        "ST-006" -> StandAbilityEffect(shield=22)
        "ST-007" -> StandAbilityEffect(shield=10, initiative=12)
        "ST-008" -> StandAbilityEffect(initiative=24, damageBonus=5)
        "ST-009" -> StandAbilityEffect(damageBonus=15, recovery=8)
        "ST-010" -> StandAbilityEffect(damageBonus=19)
        "ST-011" -> StandAbilityEffect(damageBonus=14, drain=6)
        "ST-012" -> StandAbilityEffect(shield=8, drain=12)
        "ST-013" -> StandAbilityEffect(damageBonus=24, shield=24, initiative=35, recovery=10, drain=10)
        "ST-014" -> StandAbilityEffect(damageBonus=10, shield=9)
        "ST-015" -> StandAbilityEffect(initiative=18, recovery=12)
        "ST-016" -> StandAbilityEffect(damageBonus=11, recovery=16)
        else -> error("Unknown stand: " + id)
    }
}
