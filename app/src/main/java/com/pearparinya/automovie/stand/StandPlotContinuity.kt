package com.pearparinya.automovie.stand

data class StandCharacterGoal(val owner: String, val objective: String, val achieved: Boolean = false)
data class StandPlotThread(val id: String, val description: String, val opened: Int, val resolved: Int? = null) {
    init { require(id.isNotBlank() && description.isNotBlank() && opened > 0 && (resolved == null || resolved >= opened)) }
}
object StandPlotContinuity {
    fun validate(number: Int, owners: Map<String,String>, arc: StandLongTermArc,
                 previousGoals: List<StandCharacterGoal>, goals: List<StandCharacterGoal>,
                 previousPlots: List<StandPlotThread>, plots: List<StandPlotThread>): Boolean {
        if (number !in 1..arc.finalEpisode) return false
        if (goals.any { it.owner !in owners.values || it.objective.isBlank() }) return false
        if (goals.map { it.owner to it.objective }.distinct().size != goals.size) return false
        if (plots.map { it.id }.distinct().size != plots.size) return false
        if (plots.any { it.opened > number || (it.resolved ?: 0) > number }) return false
        if (previousGoals.any { old -> goals.none { it.owner == old.owner && it.objective == old.objective && (!old.achieved || it.achieved) } }) return false
        if (previousPlots.any { old -> plots.none { it.id == old.id && it.description == old.description &&
            it.opened == old.opened && (old.resolved == null || it.resolved == old.resolved) } }) return false
        return number != arc.finalEpisode || (goals.all { it.achieved } && plots.all { it.resolved != null })
    }
}
