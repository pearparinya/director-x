package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ReferenceSnapshotTest {
    @Test fun stagedMasterCropAndPropMustMatchReviewedBytes() {
        val root=Files.createTempDirectory("reviewed-references").toFile()
        try {
            val master=root.resolve("master.jpg").apply { writeText("pia master") }
            val crop=root.resolve("crop.png").apply { writeText("pia crop") }
            val prop=root.resolve("prop.png").apply { writeText("sword") }
            val refs=listOf("PIA_MASTER" to master,"PIA_FACE_CROP" to crop,"PROP_01_MASTER" to prop)
            val hashes=ReferenceSnapshot.capture(refs)
            refs.forEach { (name,file) ->
                val output=root.resolve("shared/$name")
                ReferenceSnapshot.copyVerified(file,output,hashes.getValue(name))
                assertArrayEquals(file.readBytes(),output.readBytes())
            }
            crop.writeText("changed")
            val changed=root.resolve("shared/changed")
            assertTrue(runCatching { ReferenceSnapshot.copyVerified(crop,changed,hashes.getValue("PIA_FACE_CROP")) }.isFailure)
            assertFalse(changed.exists()); assertEquals("changed",crop.readText())
            assertTrue(runCatching { ReferenceSnapshot.copyVerified(master,master,hashes.getValue("PIA_MASTER")) }.isFailure)
            assertEquals("pia master",master.readText())
            assertTrue(runCatching { ReferenceSnapshot.capture(listOf("X" to master,"x" to prop)) }.isFailure)
            prop.delete()
            assertTrue(runCatching { ReferenceSnapshot.capture(refs) }.isFailure)
        } finally { root.deleteRecursively() }
    }
}
