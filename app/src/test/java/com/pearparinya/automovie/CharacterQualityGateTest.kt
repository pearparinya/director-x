package com.pearparinya.automovie
import org.junit.Assert.*
import org.junit.Test
class CharacterQualityGateTest {
    @Test fun requiredReferencesCannotBeSubstitutedOrDropped() {
        val master = "MASTER_A" to "/a"
        val outfit = "OUTFIT_CAST_A" to "/outfit"
        assertTrue(CharacterQualityGate.referenceIssues(listOf(master,outfit),listOf(master,outfit)).isEmpty())
        assertFalse(CharacterQualityGate.referenceIssues(listOf(master,outfit),listOf(master)).isEmpty())
        assertFalse(CharacterQualityGate.referenceIssues(listOf(master),listOf("MASTER_A" to "/b")).isEmpty())
        assertFalse(CharacterQualityGate.referenceIssues(listOf(master),listOf(master,master)).isEmpty())
        assertFalse(CharacterQualityGate.referenceIssues(emptyList(),(1..10).map { it.toString() to "/$it" }).isEmpty())
    }
    @Test fun conflictingRequiredBindingsAndBlankAttachmentsAreRejected() {
        assertTrue(CharacterQualityGate.referenceIssues(listOf("MASTER" to "/a", "MASTER" to "/b"), emptyList()).any { it.contains("หลายไฟล์") })
        assertTrue(CharacterQualityGate.referenceIssues(emptyList(), listOf("" to "/a")).any { it.contains("ว่าง") })
        assertTrue(CharacterQualityGate.referenceIssues(emptyList(), listOf("MASTER" to "")).any { it.contains("ว่าง") })
        assertTrue(CharacterQualityGate.referenceIssues((1..10).map { it.toString() to "/$it" }, emptyList()).any { it.contains("ขีดจำกัด") })
    }
    @Test fun emptyIsNotReady() {
        assertFalse(CharacterQualityGate.Report(emptyList(),emptyList(),emptyList()).ready)
        assertFalse(CharacterQualityGate.Check("MASTER",0,0).ready)
    }
    @Test fun incompleteOrWarningsPreventReady() {
        val check = CharacterQualityGate.Check("MASTER",1,1)
        assertFalse(CharacterQualityGate.Report(listOf(check),emptyList(),listOf("warning")).ready)
        assertFalse(CharacterQualityGate.Report(listOf(check),listOf("missing"),emptyList()).ready)
        assertFalse(CharacterQualityGate.Report(listOf(CharacterQualityGate.Check("outfit",1,2)),emptyList(),emptyList()).ready)
        assertTrue(CharacterQualityGate.Report(listOf(check),emptyList(),emptyList()).ready)
    }
}
