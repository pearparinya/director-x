package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PiaReferencePolicyTest {
    @Test fun selectedOutfitDoesNotReplaceIdentityMaster() {
        val root=Files.createTempDirectory("pia-ref").toFile()
        try {
            val original=root.resolve("original").apply { writeText("identity") }
            val outfit=root.resolve("outfit").apply { writeText("wardrobe") }
            val master=root.resolve("project/master"); val clothing=root.resolve("project/outfit")
            PiaReferencePolicy.capture(original,outfit,master,clothing)
            assertEquals("identity",master.readText()); assertEquals("wardrobe",clothing.readText())
            assertEquals("identity",original.readText())
            assertTrue(runCatching { PiaReferencePolicy.capture(original,outfit,original,clothing) }.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun laterScenesRequireUserToConfirmInitialOutfit() {
        assertTrue(PiaReferencePolicy.outfitInitiallySelected("STORY_COVER_9x16"))
        assertTrue(PiaReferencePolicy.outfitInitiallySelected("EP01_SC001"))
        assertFalse(PiaReferencePolicy.outfitInitiallySelected("EP01_SC002"))
        assertFalse(PiaReferencePolicy.outfitInitiallySelected("EP02_SC001"))
    }
}
