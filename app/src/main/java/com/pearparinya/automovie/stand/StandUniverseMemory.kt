package com.pearparinya.automovie.stand

enum class StandRelationship { UNKNOWN, RIVAL, ALLY, ENEMY }
enum class StandArcPhase { ORIGIN, DEVELOPMENT, ESCALATION, CLIMAX, RESOLUTION }

data class StandMemoryEvent(val episode: Int, val summary: String)
data class StandCharacterRelationship(val first: String, val second: String, val relation: StandRelationship)
data class StandLongTermArc(val goal: String, val finalEpisode: Int) {
    init { require(goal.isNotBlank() && finalEpisode >= 5) }
    fun phase(episode: Int): StandArcPhase {
        require(episode in 1..finalEpisode)
        return when {
            episode == 1 -> StandArcPhase.ORIGIN
            episode == finalEpisode -> StandArcPhase.RESOLUTION
            episode == finalEpisode - 1 -> StandArcPhase.CLIMAX
            episode * 2 >= finalEpisode -> StandArcPhase.ESCALATION
            else -> StandArcPhase.DEVELOPMENT
        }
    }
}
object StandUniverseMemory {
    fun events(history: List<StandPersistentEpisode>): List<StandMemoryEvent> =
        history.map { StandMemoryEvent(it.number, it.narrative) }

    fun relationships(owners: Map<String,String>, previous: List<StandCharacterRelationship>,
                      first: String, second: String, type: StandStoryType): List<StandCharacterRelationship> {
        require(first != second && first in owners.values && second in owners.values)
        val pair = listOf(first,second).sorted()
        val old = previous.firstOrNull { it.first == pair[0] && it.second == pair[1] }
        val relation = when(type) {
            StandStoryType.ALLIANCE, StandStoryType.RESCUE -> StandRelationship.ALLY
            StandStoryType.BETRAYAL, StandStoryType.FINAL_CONFLICT -> StandRelationship.ENEMY
            StandStoryType.RIVALRY, StandStoryType.TOURNAMENT -> StandRelationship.RIVAL
            else -> old?.relation ?: StandRelationship.UNKNOWN
        }
        return previous.filterNot { it.first == pair[0] && it.second == pair[1] } +
            StandCharacterRelationship(pair[0],pair[1],relation)
    }

    fun context(history: List<StandPersistentEpisode>, arc: StandLongTermArc,
                relationships: List<StandCharacterRelationship>): String = buildString {
        val next = history.size + 1
        appendLine("Long-term goal: " + arc.goal)
        appendLine("Episode " + next + "/" + arc.finalEpisode + " phase " + arc.phase(next))
        relationships.forEach { appendLine("Relationship: " + it.first + " / " + it.second + " = " + it.relation) }
        events(history).forEach { appendLine("Established EP " + it.episode + ": " + it.summary) }
        appendLine("Preserve established events; resolve the long-term goal in the final episode.")
    }
}
