package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class CharacterAgeTest {
    @Test fun ageRangeIncludesNewbornAndThousandYearGuardian() {
        assertTrue(CharacterAge.valid(0))
        assertTrue(CharacterAge.valid(1))
        assertTrue(CharacterAge.valid(18))
        assertTrue(CharacterAge.valid(999))
        assertTrue(CharacterAge.valid(1000))
        assertFalse(CharacterAge.valid(-1))
        assertFalse(CharacterAge.valid(1001))
    }
}
