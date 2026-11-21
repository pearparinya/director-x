package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class EightSecondDialoguePlannerTest {
    @Test fun shortSingleSpeakerFits() {
        val result = EightSecondDialoguePlanner.assess(listOf(
            EightSecondDialoguePlanner.Turn("ภาคิน", "ไปกันเถอะ")
        ))
        assertTrue(result.fits)
        assertNull(result.warning)
    }

    @Test fun crowdedFiveSpeakerSceneNeedsRevision() {
        val longLine = "พวกเราต้องรีบออกจากบริเวณนี้ก่อนที่น้ำจะท่วมสูงกว่านี้"
        val turns = (1..5).map { EightSecondDialoguePlanner.Turn("คนที่$it", longLine) }
        val result = EightSecondDialoguePlanner.assess(turns)
        assertFalse(result.fits)
        assertNotNull(result.warning)
        assertTrue(result.availableSpeechSeconds < 8.0)
    }

    @Test fun preservesOriginalSpeakerOrderAndWords() {
        val turns = listOf(
            EightSecondDialoguePlanner.Turn("กวิน", "ช่วยด้วย"),
            EightSecondDialoguePlanner.Turn("ภาคิน", "จับมือผม")
        )
        EightSecondDialoguePlanner.assess(turns)
        assertEquals("กวิน", turns[0].speaker)
        assertEquals("ช่วยด้วย", turns[0].text)
        assertEquals("ภาคิน", turns[1].speaker)
        assertEquals("จับมือผม", turns[1].text)
    }

    @Test fun noSpeechFits() {
        assertTrue(EightSecondDialoguePlanner.assess(emptyList()).fits)
    }
}
