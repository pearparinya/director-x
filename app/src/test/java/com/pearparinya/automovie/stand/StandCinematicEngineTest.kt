package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class StandCinematicEngineTest {
    @get:Rule val folder = TemporaryFolder()
    private val ownership = StandOwnership.restore(StandProject(StandChannel.PIA, "cinema"))
    private val presence = StandPresence("ST-013", "PIEROS", true)
    private val scene = StandStoryScene(1, listOf(presence))
    private val records = StandCatalog.all.map {
        StandMasterRecord(it.id, StandMasterIntegrity.digest(it.id.byteInputStream()))
    }
    private fun store() = StandMasterStore(folder.root, records).also {
        assertTrue(it.importOriginal("ST-013", "ST-013".byteInputStream()))
    }
    @Test fun verifiedOriginalIncluded() {
        val request = StandCinematicEngine.compose(ownership, scene, records, store(),
            "PIEROS", "street", emptyList())
        assertEquals("ST-013", request.references.single().binding.standId)
        assertTrue(request.imagePrompt.contains("START STATE"))
        assertTrue(request.videoPrompt.contains("8-second"))
    }
    @Test fun civilianDoesNotReceiveStandReferences() {
        val request = StandCinematicEngine.compose(ownership, scene, records, store(),
            "civilian", "street", emptyList())
        assertTrue(request.references.isEmpty())
        assertFalse(request.imagePrompt.contains("ST-013"))
    }
    @Test fun telepathyHasNoLipMovement() {
        val line = StandConsciousnessEngine.speak(ownership, presence, "PIEROS",
            "stay alert", StandSpeech.TELEPATHIC, true)
        val request = StandCinematicEngine.compose(ownership, scene, records, store(),
            "PIEROS", "street", emptyList(), listOf(line))
        assertTrue(request.videoPrompt.contains("stay alert"))
        assertTrue(request.videoPrompt.contains("No lip movement"))
        assertFalse(request.imagePrompt.contains("stay alert"))
    }
    @Test fun landscapeSupported() {
        val request = StandCinematicEngine.compose(ownership, scene, records, store(),
            "PIEROS", "street", emptyList(), aspect = StandAspect.LANDSCAPE)
        assertTrue(request.imagePrompt.contains("16:9"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun missingMasterBlocksGeneration() {
        StandCinematicEngine.compose(ownership, scene, records, StandMasterStore(folder.root, records),
            "PIEROS", "street", emptyList())
    }
    @Test(expected = IllegalArgumentException::class)
    fun mismatchedMovementRejected() {
        val hidden = StandMovementEngine.initial(ownership, "ST-013", "PIEROS", StandPosition(0.0, 0.0))
        StandCinematicEngine.compose(ownership, scene, records, store(),
            "PIEROS", "street", listOf(hidden))
    }
    @Test fun ownerReplyIncludedInVideo() {
        val reply = StandConsciousnessEngine.ownerReply(ownership, presence, "PIEROS",
            "understood", StandSpeech.TELEPATHIC)
        val request = StandCinematicEngine.compose(ownership, scene, records, store(),
            "PIEROS", "street", emptyList(), listOf(reply))
        assertTrue(request.videoPrompt.contains("understood"))
        assertTrue(request.videoPrompt.contains("owner-voice-PIEROS"))
        assertTrue(request.videoPrompt.contains("No lip movement"))
    }
}
