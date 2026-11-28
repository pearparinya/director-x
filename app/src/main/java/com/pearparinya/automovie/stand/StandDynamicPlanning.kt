package com.pearparinya.automovie.stand

enum class StandEnding { HOPEFUL, TRAGIC, BITTERSWEET, OPEN_SEQUEL }
data class StandSeriesPlan(val arc: StandLongTermArc, val ending: StandEnding)
object StandDynamicPlanning {
    fun recommend(plan: StandSeriesPlan, history: List<StandPersistentEpisode>): StandStoryType {
        val next = history.size + 1
        val prior = history.lastOrNull()
        return when(plan.arc.phase(next)) {
            StandArcPhase.ORIGIN -> StandStoryType.ORIGIN
            StandArcPhase.DEVELOPMENT -> if (prior?.relationships.orEmpty().any { it.relation == StandRelationship.ENEMY })
                StandStoryType.INVESTIGATION else StandStoryType.TRAINING
            StandArcPhase.ESCALATION -> if (prior?.plots.orEmpty().any { it.resolved == null })
                StandStoryType.INVESTIGATION else StandStoryType.RIVALRY
            StandArcPhase.CLIMAX -> StandStoryType.FINAL_CONFLICT
            StandArcPhase.RESOLUTION -> StandStoryType.ALLIANCE
        }
    }
    fun context(plan: StandSeriesPlan, history: List<StandPersistentEpisode>): String = buildString {
        appendLine("Ending tone: " + plan.ending.name)
        appendLine("Recommended episode type: " + recommend(plan,history).name)
        appendLine("Develop characters through earned choices and consequences, not unexplained personality changes.")
        val prior = history.lastOrNull()
        prior?.owners.orEmpty().values.distinct().sorted().forEach { owner ->
            val goals = prior?.goals.orEmpty().filter { it.owner == owner }
            appendLine("Character " + owner + ": completed goals " + goals.count { it.achieved } +
                ", pending goals " + goals.count { !it.achieved })
        }
        prior?.plots.orEmpty().filter { it.resolved == null }.forEach {
            appendLine("Outstanding plot " + it.id + ": " + it.description)
        }
        appendLine("Resolve the main arc by the final episode. An open sequel may introduce a future hook without undoing resolved events.")
    }
}
