package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class StoryFallbackPolicyTest {
    private val all = StoryProvider.activeProviders.toSet()

    @Test fun noExplicitApprovalMeansNoFallback() {
        assertTrue(StoryFallbackPolicy.candidates(
            StoryProvider.GROQ, all, all, emptySet(), emptySet()
        ).isEmpty())
    }

    @Test fun neverUsesMissingKeysOrDisabledProviders() {
        assertEquals(listOf(StoryProvider.GEMINI),
            StoryFallbackPolicy.candidates(
                StoryProvider.GROQ,
                all + StoryProvider.DEEPSEEK,
                setOf(StoryProvider.GROQ, StoryProvider.GEMINI, StoryProvider.DEEPSEEK),
                emptySet(),
                setOf(StoryProvider.GEMINI, StoryProvider.DEEPSEEK)
            ))
    }

    @Test fun neverRetriesPrimaryOrPreviouslyAttemptedProvider() {
        assertEquals(listOf(StoryProvider.COHERE),
            StoryFallbackPolicy.candidates(
                StoryProvider.GROQ, all, all,
                setOf(StoryProvider.GEMINI, StoryProvider.OPENROUTER),
                all
            ))
    }

    @Test fun onlyTransientHttpErrorsMayTriggerFallback() {
        listOf(408, 429, 500, 502, 503, 599).forEach {
            assertTrue("Expected retryable status $it", StoryFallbackPolicy.canFallback(it))
        }
        listOf(null, 200, 400, 401, 402, 403, 404, 422).forEach {
            assertFalse("Unexpected fallback for $it", StoryFallbackPolicy.canFallback(it))
        }
    }
}
