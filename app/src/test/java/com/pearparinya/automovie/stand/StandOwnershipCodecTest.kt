package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandOwnershipCodecTest {
    private val project = StandProject(StandChannel.PIA, "project-1")
    @Test fun roundTrip() {
        val state = StandOwnership.restore(project).assign("ST-001", "rival")
        val restored = StandOwnershipCodec.decode(project, StandOwnershipCodec.encode(state))
        assertEquals(state.snapshot(), restored.snapshot())
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectDifferentChannel() {
        StandOwnershipCodec.decode(project.copy(channel = StandChannel.UNIVERSE),
            StandOwnershipCodec.encode(StandOwnership.restore(project)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectDifferentProject() {
        StandOwnershipCodec.decode(project.copy(projectId = "project-2"),
            StandOwnershipCodec.encode(StandOwnership.restore(project)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectTamperedOracle() {
        val raw = StandOwnershipCodec.encode(StandOwnership.restore(project)).replace("PIEROS", "rival")
        StandOwnershipCodec.decode(project, raw)
    }
}
