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
    @Test fun providerReceivesOriginalAndStartFrame() {
        val calls = mutableListOf<String>()
        val provider = object : StandMediaProvider {
            override fun image(prompt: String,references: List<StandMediaAsset>): ByteArray {
                calls.add("image")
                assertArrayEquals("ST-013".toByteArray(),references.single().bytes)
                return byteArrayOf(1)
            }
            override fun video(prompt: String,references: List<StandMediaAsset>,startFrame: ByteArray): ByteArray {
                calls.add("video")
                assertArrayEquals(byteArrayOf(1),startFrame)
                assertArrayEquals("ST-013".toByteArray(),references.single().bytes)
                return byteArrayOf(2)
            }
            override fun voice(request: StandVoiceRequest): ByteArray {
                calls.add("voice")
                assertTrue(request.telepathic)
                assertFalse(request.lipMovement)
                return byteArrayOf(3)
            }
        }
        val result = StandMediaPipeline.produce(request(),listOf(StandVoiceRequest("ST-013","test-voice","hello",true,false)),provider)
        assertEquals(listOf("image","video","voice"),calls)
        assertArrayEquals(byteArrayOf(2),result.video)
    }
}
