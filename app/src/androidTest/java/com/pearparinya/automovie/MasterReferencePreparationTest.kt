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
    @Test fun fiveCharactersUseFiveOriginalMastersWithoutFaceCrops() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ref-five-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val sources = (1..5).map { index ->
                MasterReferencePreparation.CharacterSource(
                    "Character $index", File(dir, "master-$index.jpg").apply { writeText("unique master $index") }
                )
            }
            val result = MasterReferencePreparation.prepareOriginalMastersOnly(sources)
            assertTrue(result.warnings.toString(), result.ready)
            assertEquals(5, result.attachments.size)
            assertEquals(sources.map { it.name }, result.attachments.map { it.character })
            assertTrue(result.attachments.all { it.role == "ORIGINAL_MASTER" })
            assertTrue(MasterReferencePreparation.bindingText(result).contains("Reference image 5: Character 5"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun overNineMastersWarnsWithoutSilentlyDroppingCharacters() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ref-ten-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val sources = (1..10).map { index ->
                MasterReferencePreparation.CharacterSource(
                    "Character $index", File(dir, "$index.jpg").apply { writeText("unique master $index") }
                )
            }
            val result = MasterReferencePreparation.prepareOriginalMastersOnly(sources)
            assertFalse(result.ready)
            assertEquals(10, result.attachments.size)
            assertTrue(result.warnings.any { it.contains("9") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun masterOnlyRejectsDuplicateBytesAndNames() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ref-check-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val a = File(dir, "a.jpg").apply { writeText("same") }
            val b = File(dir, "b.jpg").apply { writeText("same") }
            val result = MasterReferencePreparation.prepareOriginalMastersOnly(listOf(
                MasterReferencePreparation.CharacterSource("A", a),
                MasterReferencePreparation.CharacterSource("B", b),
                MasterReferencePreparation.CharacterSource("A", a)
            ))
            assertFalse(result.ready)
            assertEquals(1, result.attachments.size)
            assertEquals(2, result.warnings.size)
        } finally { dir.deleteRecursively() }
    }

}
