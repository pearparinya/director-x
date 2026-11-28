package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandCatalogTest {
    @Test fun catalogHasSixteenUniqueIds() {
        assertEquals(16, StandCatalog.all.size)
        assertEquals(16, StandCatalog.all.map { it.id }.toSet().size)
    }
    @Test fun onlyOracleIsSupreme() {
        assertEquals(listOf("ST-013"), StandCatalog.all.filter { it.supreme }.map { it.id })
    }
    @Test fun piaLocksOracleToPieros() {
        assertTrue(StandCatalog.permitsOwner(StandChannel.PIA, "ST-013", "PIEROS"))
        assertFalse(StandCatalog.permitsOwner(StandChannel.PIA, "ST-013", "OTHER"))
        assertFalse(StandCatalog.permitsOwner(StandChannel.PIA, "ST-001", "PIEROS"))
        assertTrue(StandCatalog.permitsOwner(StandChannel.PIA, "ST-001", "OTHER"))
    }
    @Test fun universeDoesNotInheritPiaLock() {
        assertTrue(StandCatalog.permitsOwner(StandChannel.UNIVERSE, "ST-013", "OTHER"))
    }
    @Test fun invalidAssignmentsRejected() {
        for (channel in StandChannel.values()) {
            assertFalse(StandCatalog.permitsOwner(channel, "ST-999", "OTHER"))
            assertFalse(StandCatalog.permitsOwner(channel, "ST-001", " "))
        }
    }
    @Test fun channelIdsAreStable() {
        assertEquals("PIA_STAND", StandChannel.PIA.id)
        assertEquals("UNIVERSE_STAND", StandChannel.UNIVERSE.id)
    }
}
