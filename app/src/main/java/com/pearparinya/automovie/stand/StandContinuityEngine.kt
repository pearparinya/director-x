package com.pearparinya.automovie.stand

import java.io.File

data class StandContinuityFrame(val project: StandProject, val sceneNumber: Int,
    val identities: Map<String, StandVisualBinding>)

data class StandOriginalReference(val binding: StandVisualBinding, val file: File)

object StandContinuityEngine {
    fun capture(ownership: StandOwnership, scene: StandStoryScene,
                masters: List<StandMasterRecord>, previous: StandContinuityFrame? = null): StandContinuityFrame {
        require(scene.number > 0)
        require(previous == null && scene.number == 1 ||
            previous != null && previous.project == ownership.project && previous.sceneNumber + 1 == scene.number)
        require(StandStoryRules.validate(ownership, listOf(scene.copy(number = 1))))
        require(StandMasterIntegrity.complete(masters))
        val records = masters.associateBy { it.standId }
        val retained = previous?.identities.orEmpty()
        val current = scene.presences.associate { presence ->
            presence.standId to StandVisualBinding(presence.standId, presence.ownerId,
                records.getValue(presence.standId).sha256)
        }
        require(current.all { (id, binding) -> retained[id]?.let { it == binding } ?: true })
        require(retained.all { (id, binding) ->
            ownership.isOwner(id, binding.ownerId) && records.getValue(id).sha256 == binding.masterSha256
        })
        return StandContinuityFrame(ownership.project, scene.number, retained + current)
    }

    fun originalReferences(ownership: StandOwnership, scene: StandStoryScene,
                           masters: List<StandMasterRecord>, store: StandMasterStore,
                           observerIsStandUser: Boolean): List<StandOriginalReference> =
        StandCinematicRules.bindings(ownership, scene, masters, observerIsStandUser).map { binding ->
            val file = requireNotNull(store.verifiedFile(binding.standId)) { "Missing verified MASTER: " + binding.standId }
            val record = masters.first { it.standId == binding.standId }
            require(file.inputStream().use { StandMasterIntegrity.verify(record, it) })
            StandOriginalReference(binding, file)
        }

    const val VISUAL_LOCK = "Preserve original MASTER anatomy, face, armor, colors, proportions and distinctive features. Never add a mouth or substitute another MASTER. Preserve owner identity separately."
}
