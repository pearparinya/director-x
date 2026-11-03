package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class ThreeViewCharacterTest {
    @Test fun templateKeepsUserDetailsAndAllThreeViews() {
        val description = "adult woman aged 40, ผมสั้น, blue dress"
        val prompt = ImagePromptPolicy.threeViewCharacter(description)
        assertTrue(prompt.contains(description))
        assertTrue(prompt.contains("Front view, 45-degree profile view, and back view"))
        assertTrue(prompt.contains("Identical facial features and identical clothing across all three views"))
        assertTrue(prompt.endsWith("photorealistic cinematic quality. --ar 16:9"))
    }
}
