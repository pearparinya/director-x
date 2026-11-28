package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandCinematicRulesTest {
    private val ownership = StandOwnership.restore(StandProject(StandChannel.PIA, "film"))
    private val masters = StandCatalog.all.map { StandMasterRecord(it.id, StandMasterIntegrity.digest(it.id.byteInputStream())) }
    private val scene = StandStoryScene(1, listOf(StandPresence("ST-013", "PIEROS", true)))
    @Test fun bindsExactId() {
        val binding = StandCinematicRules.bindings(ownership, scene, masters, true).single()
        assertEquals("ST-013", binding.standId)
        assertEquals(masters.single { it.standId == "ST-013" }.sha256, binding.masterSha256)
    }
    @Test fun ordinaryObserverSeesNoStand() {
        assertTrue(StandCinematicRules.bindings(ownership, scene, masters, false).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun missingMasterRejected() { StandCinematicRules.bindings(ownership, scene, masters.dropLast(1), true) }
    @Test(expected = IllegalArgumentException::class)
    fun wrongOwnerRejected() {
        StandCinematicRules.bindings(ownership, scene.copy(presences = listOf(StandPresence("ST-013", "other", true))), masters, true)
    }
}
