package com.pearparinya.automovie.stand

data class StandProject(val channel: StandChannel, val projectId: String) {
    init { require(projectId.isNotBlank()) }
}

/** Immutable project ownership state; persistence adapters must retain the project key. */
class StandOwnership private constructor(
    val project: StandProject,
    private val assignments: Map<String, String>
) {
    fun ownerOf(standId: String): String? = assignments[standId]
    fun snapshot(): Map<String, String> = assignments.toMap()

    fun assign(standId: String, ownerId: String): StandOwnership {
        require(StandCatalog.permitsOwner(project.channel, standId, ownerId))
        return StandOwnership(project, assignments + (standId to ownerId))
    }

    fun remove(standId: String): StandOwnership {
        require(StandCatalog.find(standId) != null)
        require(project.channel != StandChannel.PIA || standId != StandCatalog.SUPREME_ID)
        return StandOwnership(project, assignments - standId)
    }

    companion object {
        fun restore(project: StandProject, saved: Map<String, String> = emptyMap()): StandOwnership {
            require(saved.all { (stand, owner) -> StandCatalog.permitsOwner(project.channel, stand, owner) })
            val initial = if (project.channel == StandChannel.PIA)
                saved + (StandCatalog.SUPREME_ID to StandCatalog.PIEROS)
            else saved.toMap()
            return StandOwnership(project, initial)
        }
    }
}
