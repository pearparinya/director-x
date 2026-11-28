package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandMasterIntegrityTest {
    private val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    @Test fun knownDigest() { assertEquals(hash, StandMasterIntegrity.digest("abc".byteInputStream())) }
    @Test fun originalAccepted() { assertTrue(StandMasterIntegrity.verify(StandMasterRecord("ST-013", hash), "abc".byteInputStream())) }
    @Test fun changedBytesRejected() { assertFalse(StandMasterIntegrity.verify(StandMasterRecord("ST-013", hash), "abd".byteInputStream())) }
    @Test fun missingMastersRejected() { assertFalse(StandMasterIntegrity.complete(emptyList())) }
    @Test fun duplicateHashesRejected() { assertFalse(StandMasterIntegrity.complete(StandCatalog.all.map { StandMasterRecord(it.id, hash) })) }
    @Test fun uniqueRecordsAccepted() {
        assertTrue(StandMasterIntegrity.complete(StandCatalog.all.map {
            StandMasterRecord(it.id, StandMasterIntegrity.digest(it.id.byteInputStream()))
        }))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unknownIdRejected() { StandMasterRecord("ST-999", hash) }
}
