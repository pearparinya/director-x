package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MasterReferencePreparationTest {
    @Test fun usesOnlyActualCharacterSourcesAndReviewedCropsInStableOrder() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ref-prep-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val a = File(dir, "a.jpg").apply { writeText("master A") }
            val b = File(dir, "b.jpg").apply { writeText("master B") }
            val store = FaceReferenceStore(File(dir, "crops"))
            val crop = store.newFile(a).apply { parentFile!!.mkdirs(); writeText("face A") }
            val result = MasterReferencePreparation.prepare(listOf(
                MasterReferencePreparation.CharacterSource("A", a),
                MasterReferencePreparation.CharacterSource("B", b)
            ), store)
            assertEquals(listOf(a, crop, b), result.attachments.map { it.file })
            assertEquals(listOf("ORIGINAL_MASTER", "FACE_CROP", "ORIGINAL_MASTER"), result.attachments.map { it.role })
            assertTrue(result.warnings.any { it.contains("B:") })
            assertTrue(MasterReferencePreparation.bindingText(result).contains("Reference image 3: B"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun refusesDuplicateIdentitySources() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ref-dup-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val a = File(dir, "a.jpg").apply { writeText("same bytes") }
            val b = File(dir, "b.jpg").apply { writeText("same bytes") }
            val result = MasterReferencePreparation.prepare(listOf(
                MasterReferencePreparation.CharacterSource("A", a),
                MasterReferencePreparation.CharacterSource("B", b)
            ), FaceReferenceStore(File(dir, "crops")))
            assertEquals(1, result.attachments.size)
            assertTrue(result.warnings.any { it.contains("เดียวกับตัวละครอื่น") })
        } finally { dir.deleteRecursively() }
    }
    @Test fun rejectsNamesThatOnlyDifferByExtraWhitespace() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "ref-name-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val a = File(dir, "a.jpg").apply { writeText("master A") }
            val b = File(dir, "b.jpg").apply { writeText("master B") }
            val result = MasterReferencePreparation.prepare(listOf(
                MasterReferencePreparation.CharacterSource("นาย เปีย", a),
                MasterReferencePreparation.CharacterSource(" นาย   เปีย ", b)
            ), FaceReferenceStore(File(dir, "crops")))
            assertEquals(1, result.attachments.size)
            assertTrue(result.warnings.any { it.contains("ชื่อตัวละครว่างหรือซ้ำ") })
        } finally { dir.deleteRecursively() }
    }

}
