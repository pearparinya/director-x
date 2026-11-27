package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class CharacterAppearanceLockTest {
    @Test fun identityCannotBeDisabled() {
        val text = CharacterAppearanceLock(false,false,false).instruction("Actor A")
        assertTrue(text.contains("Original MASTER"))
        assertTrue(text.contains("Actor A"))
        assertFalse(text.contains("body proportions"))
        assertFalse(text.contains("hairstyle"))
        assertFalse(text.contains("approved outfit"))
    }
    @Test fun defaultsPreserveAppearanceWithoutForcingOldOutfit() {
        val text = CharacterAppearanceLock().instruction("Actor B")
        assertTrue(text.contains("body proportions"))
        assertTrue(text.contains("hairstyle"))
        assertTrue(text.contains("written scene outfit"))
        assertTrue(text.contains("Never reuse a different outfit"))
    }
}
