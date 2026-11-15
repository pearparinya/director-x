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
    @Test fun retryBudgetAllowsOnlyOneApprovedBackupByDefault() {
        val next = StoryFallbackPolicy.nextCandidate(
            StoryProvider.GROQ, all, all, setOf(StoryProvider.GROQ),
            setOf(StoryProvider.GEMINI), 429, cancelled = false
        )
        assertEquals(StoryProvider.GEMINI, next)
        assertNull(StoryFallbackPolicy.nextCandidate(
            StoryProvider.GROQ, all, all,
            setOf(StoryProvider.GROQ, StoryProvider.GEMINI),
            setOf(StoryProvider.GEMINI), 429, cancelled = false
        ))
    }

    @Test fun cancellationOrNonTransientErrorNeverFallsBack() {
        for (cancelled in listOf(true, false)) {
            assertNull(StoryFallbackPolicy.nextCandidate(
                StoryProvider.GROQ, all, all, setOf(StoryProvider.GROQ),
                setOf(StoryProvider.GEMINI), 401, cancelled
            ))
        }
        assertNull(StoryFallbackPolicy.nextCandidate(
            StoryProvider.GROQ, all, all, setOf(StoryProvider.GROQ),
            setOf(StoryProvider.GEMINI), 503, cancelled = true
        ))
    }

    @Test fun primaryMustHaveBeenAttemptedAndBudgetMustBePositive() {
        assertNull(StoryFallbackPolicy.nextCandidate(
            StoryProvider.GROQ, all, all, emptySet(),
            setOf(StoryProvider.GEMINI), 429, cancelled = false
        ))
        assertNull(StoryFallbackPolicy.nextCandidate(
            StoryProvider.GROQ, all, all, setOf(StoryProvider.GROQ),
            setOf(StoryProvider.GEMINI), 429, cancelled = false,
            maxTotalAttempts = 1
        ))
    }

}
