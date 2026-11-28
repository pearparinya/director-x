package com.pearparinya.automovie.stand
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class StandMasterAuditTest {
    @get:Rule val folder = TemporaryFolder()
    private val records = StandCatalog.all.map { StandMasterRecord(it.id, StandMasterIntegrity.digest(it.id.byteInputStream())) }
    @Test fun missingAssetsBlockProduction() {
        val store = StandMasterStore(folder.root, records)
        assertEquals(16, store.audit().count { it.status == StandMasterStatus.MISSING })
        assertFalse(store.ready())
        assertTrue(runCatching { StandMediaPipeline.requireReady(store) }.isFailure)
    }
    @Test fun completeAssetsPassAndTamperingBlocks() {
        val store = StandMasterStore(folder.root, records)
        records.forEach { assertTrue(store.importOriginal(it.standId, it.standId.byteInputStream())) }
        assertTrue(store.ready())
        StandMediaPipeline.requireReady(store)
        File(folder.root,"stand_masters_v1/ST-013.master").writeText("corrupted")
        assertEquals(StandMasterStatus.HASH_MISMATCH, store.audit().first { it.standId == "ST-013" }.status)
        assertFalse(store.ready())
        assertTrue(runCatching { StandMediaPipeline.requireReady(store) }.isFailure)
    }
    @Test fun invalidImportPreservesOriginal() {
        val store = StandMasterStore(folder.root, records)
        assertTrue(store.importOriginal("ST-013","ST-013".byteInputStream()))
        assertFalse(store.importOriginal("ST-013","wrong".byteInputStream()))
        assertEquals(StandMasterStatus.VERIFIED,store.audit().first { it.standId == "ST-013" }.status)
    }
}
