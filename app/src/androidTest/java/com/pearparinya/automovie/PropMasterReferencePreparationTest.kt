package com.pearparinya.automovie

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PropMasterReferencePreparationTest {
    @Test fun resolvesOneAttachmentForRepeatedP017() {
        val file = File.createTempFile("p017", ".jpg")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0))
            val scene = JSONObject("""{"prop_links":[{"prop_master_id":"P017"},{"prop_master_id":"P017"}]}""")
            val result = PropMasterReferencePreparation.prepare(
                scene, listOf(PropMasterReferencePreparation.Source("P017", "lantern", file))
            )
            assertTrue(result.ready)
            assertEquals(1, result.attachments.size)
            assertEquals("PROP_MASTER", result.attachments.single().role)
            assertTrue(PropMasterReferencePreparation.bindingText(result, 3).contains("Reference image 3: PROP MASTER P017"))
        } finally { file.delete() }
    }

    @Test fun missingRequiredMasterFailsPreflight() {
        val scene = JSONObject("""{"prop_links":[{"prop_master_id":"P017"}]}""")
        val result = PropMasterReferencePreparation.prepare(scene, emptyList())
        assertFalse(result.ready)
        assertTrue(result.attachments.isEmpty())
    }

    @Test fun rejectsNonImageFilesEvenWithJpgExtension() {
        val file = File.createTempFile("fake-master", ".jpg")
        try {
            file.writeText("not an image")
            val scene = JSONObject("""{"prop_links":[{"prop_master_id":"P017"}]}""")
            val result = PropMasterReferencePreparation.prepare(
                scene, listOf(PropMasterReferencePreparation.Source("P017", "lantern", file))
            )
            assertFalse(result.ready)
            assertTrue(result.attachments.isEmpty())
        } finally { file.delete() }
    }

    @Test fun anyMissingMasterBlocksAllAttachments() {
        val file = File.createTempFile("p017", ".jpg")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0))
            val scene = JSONObject("""{"prop_links":[{"prop_master_id":"P017"},{"prop_master_id":"P018"}]}""")
            val result = PropMasterReferencePreparation.prepare(
                scene, listOf(PropMasterReferencePreparation.Source("P017", "lantern", file))
            )
            assertFalse(result.ready)
            assertTrue(result.attachments.isEmpty())
            assertTrue(result.issues.any { it.contains("P018") })
        } finally { file.delete() }
    }

    @Test fun rejectsMismatchedReviewedPropName() {
        val file = File.createTempFile("p017", ".jpg")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0))
            val scene = JSONObject("""{"prop_links":[{"item":"wrong item","prop_master_id":"P017"}]}""")
            val result = PropMasterReferencePreparation.prepare(
                scene, listOf(PropMasterReferencePreparation.Source("P017", "lantern", file))
            )
            assertFalse(result.ready)
            assertTrue(result.attachments.isEmpty())
        } finally { file.delete() }
    }

    @Test fun legacyPropsRemainOptional() {
        val scene = JSONObject("""{"prop_links":[{"item":"lantern"}]}""")
        assertTrue(PropMasterReferencePreparation.prepare(scene, emptyList()).ready)
    }
}
