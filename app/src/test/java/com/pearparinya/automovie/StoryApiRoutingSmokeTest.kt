package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

/** Offline regression tests: never call paid APIs or access user API keys. */
class StoryApiRoutingSmokeTest {
    @Test fun activeProvidersHaveUniqueIdsAndValidDefaults() {
        val providers = StoryProvider.activeProviders
        assertTrue(providers.isNotEmpty())
        assertEquals(providers.size, providers.map { it.id }.distinct().size)
        providers.forEach {
            assertTrue(it.defaultModel.isNotBlank())
            assertTrue(it.keyUrl.startsWith("https://"))
            assertSame(it, StoryProvider.from(it.id))
        }
    }

    @Test fun routingRejectsUnknownProvider() {
        try {
            StoryProvider.from("not-a-provider")
            fail("Unknown API provider must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: unknown providers must not silently route to a paid API.
        }
    }

    @Test fun readinessRequiresSuitableActiveProviderCombination() {
        assertFalse(StoryProvider.viable(emptyList()))
        assertFalse(StoryProvider.viable(listOf(StoryProvider.GROQ)))
        assertFalse(StoryProvider.viable(listOf(StoryProvider.GEMINI)))
        assertTrue(StoryProvider.viable(listOf(StoryProvider.GROQ, StoryProvider.GEMINI)))
        assertTrue(StoryProvider.viable(listOf(StoryProvider.OPENROUTER)))
        assertTrue(StoryProvider.viable(listOf(StoryProvider.COHERE)))
        assertFalse(StoryProvider.viable(listOf(StoryProvider.DEEPSEEK)))
    }

    @Test fun activeProvidersAreSupportedByClientFactory() {
        // This tests the routing contract without making live network requests.
        val active = StoryProvider.activeProviders
        assertTrue(active.contains(StoryProvider.GROQ))
        assertTrue(active.contains(StoryProvider.GEMINI))
        assertTrue(active.contains(StoryProvider.OPENROUTER))
        assertTrue(active.contains(StoryProvider.COHERE))
    }
}
