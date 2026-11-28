package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class StandContinuityEngineTest {
    @get:Rule val folder = TemporaryFolder()
    private val ownership = StandOwnership.restore(StandProject(StandChannel.PIA, "continuity"))
    private val presence = StandPresence("ST-013", "PIEROS", true)
    private val records = StandCatalog.all.map {
        StandMasterRecord(it.id, StandMasterIntegrity.digest(it.id.byteInputStream()))
    }
    private fun scene(number: Int, presences: List<StandPresence> = listOf(presence)) =
        StandStoryScene(number, presences)

    @Test fun identityRetainedAcrossHiddenScenes() {
        val first = StandContinuityEngine.capture(ownership, scene(1), records)
        val second = StandContinuityEngine.capture(ownership, scene(2, emptyList()), records, first)
        val third = StandContinuityEngine.capture(ownership, scene(3), records, second)
        assertEquals(first.identities, third.identities)
    }
    @Test(expected = IllegalArgumentException::class)
    fun changedMasterRejected() {
        val first = StandContinuityEngine.capture(ownership, scene(1), records)
        val changed = records.map { if (it.standId == "ST-013") it.copy(sha256 = "f".repeat(64)) else it }
        StandContinuityEngine.capture(ownership, scene(2), changed, first)
    }
    @Test(expected = IllegalArgumentException::class)
    fun skippedSceneRejected() {
        val first = StandContinuityEngine.capture(ownership, scene(1), records)
        StandContinuityEngine.capture(ownership, scene(3), records, first)
    }
    @Test(expected = IllegalArgumentException::class)
    fun missingOriginalRejected() {
        StandContinuityEngine.originalReferences(ownership, scene(1), records,
            StandMasterStore(folder.root, records), true)
    }
    @Test fun originalBytesPreserved() {
        val store = StandMasterStore(folder.root, records)
        assertTrue(store.importOriginal("ST-013", "ST-013".byteInputStream()))
        val ref = StandContinuityEngine.originalReferences(ownership, scene(1), records, store, true).single()
        assertArrayEquals("ST-013".toByteArray(), ref.file.readBytes())
    }
    @Test fun civilianDoesNotReceiveMasterReference() {
        assertTrue(StandContinuityEngine.originalReferences(ownership, scene(1), records,
            StandMasterStore(folder.root, records), false).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun crossProjectContinuityRejected() {
        val first = StandContinuityEngine.capture(ownership, scene(1), records)
        val other = StandOwnership.restore(StandProject(StandChannel.PIA, "other"))
        StandContinuityEngine.capture(other, scene(2), records, first)
    }
}
