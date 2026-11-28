package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StandMasterStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val records = StandCatalog.all.map {
        StandMasterRecord(it.id, StandMasterIntegrity.digest(it.id.byteInputStream()))
    }
    @Test fun importAndRead() {
        val store = StandMasterStore(folder.root, records)
        assertTrue(store.importOriginal("ST-013", "ST-013".byteInputStream()))
        assertEquals("ST-013", store.verifiedFile("ST-013")!!.readText())
    }
    @Test fun invalidReplacementPreservesOriginal() {
        val store = StandMasterStore(folder.root, records)
        assertTrue(store.importOriginal("ST-013", "ST-013".byteInputStream()))
        assertFalse(store.importOriginal("ST-013", "wrong".byteInputStream()))
        assertEquals("ST-013", store.verifiedFile("ST-013")!!.readText())
    }
    @Test fun allMastersRequired() {
        val store = StandMasterStore(folder.root, records)
        assertFalse(store.ready())
        records.forEach { assertTrue(store.importOriginal(it.standId, it.standId.byteInputStream())) }
        assertTrue(store.ready())
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteRegistry() { StandMasterStore(folder.root, records.dropLast(1)) }
}
