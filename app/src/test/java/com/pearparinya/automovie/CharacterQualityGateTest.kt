package com.pearparinya.automovie
import org.junit.Assert.*
import org.junit.Test
class CharacterQualityGateTest {
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
