package com.pearparinya.automovie.stand
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class StandMediaPipelineTest {
    @get:Rule val folder = TemporaryFolder()
    private val records = StandCatalog.all.map { StandMasterRecord(it.id,StandMasterIntegrity.digest(it.id.byteInputStream())) }
    private fun request(): StandCinematicRequest {
        val file = folder.newFile()
        file.writeText("ST-013")
        val binding = StandVisualBinding("ST-013","PIEROS",records.first { it.standId == "ST-013" }.sha256)
        return StandCinematicRequest("image","video",listOf(StandOriginalReference(binding,file)),
            StandContinuityFrame(StandProject(StandChannel.PIA,"test"),1,mapOf("ST-013" to binding)))
    }
    @Test fun reportsAllMissingMasters() {
        assertEquals(16,StandMediaPipeline.missingMasters(StandMasterStore(folder.root,records)).size)
    }
    @Test fun originalBytesPreserved() {
        assertArrayEquals("ST-013".toByteArray(),StandMediaPipeline.assets(request()).single().bytes)
    }
    @Test(expected=IllegalArgumentException::class)
    fun tamperedMasterRejected() {
        val r = request()
        r.references.single().file.writeText("tampered")
        StandMediaPipeline.assets(r)
    }
}
