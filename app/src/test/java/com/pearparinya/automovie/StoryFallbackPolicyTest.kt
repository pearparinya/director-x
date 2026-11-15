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

    @Test fun onlyApprovedConfiguredAndAllowedProviderCanRun() {
        val next = StoryFallbackPolicy.nextCandidate(
            primary = StoryProvider.GROQ,
            allowed = setOf(StoryProvider.GROQ, StoryProvider.GEMINI),
            configured = setOf(StoryProvider.GROQ, StoryProvider.GEMINI, StoryProvider.COHERE),
            attempted = setOf(StoryProvider.GROQ),
            approvedForAutomaticUse = setOf(StoryProvider.COHERE),
            httpStatus = 503,
            cancelled = false
        )
        assertNull("An approved provider must also be allowed", next)
    }

    @Test fun missingBackupKeyPreventsFallback() {
        assertNull(StoryFallbackPolicy.nextCandidate(
            primary = StoryProvider.GROQ,
            allowed = all,
            configured = setOf(StoryProvider.GROQ),
            attempted = setOf(StoryProvider.GROQ),
            approvedForAutomaticUse = setOf(StoryProvider.GEMINI),
            httpStatus = 429,
            cancelled = false
        ))
    }

    @Test fun fallbackNeverRepeatsAnAlreadyAttemptedBackup() {
        assertEquals(StoryProvider.COHERE, StoryFallbackPolicy.nextCandidate(
            primary = StoryProvider.GROQ,
            allowed = all,
            configured = all,
            attempted = setOf(StoryProvider.GROQ, StoryProvider.GEMINI),
            approvedForAutomaticUse = setOf(StoryProvider.GEMINI, StoryProvider.COHERE),
            httpStatus = 503,
            cancelled = false,
            maxTotalAttempts = 3
        ))
    }

    @Test fun defaultBudgetStopsAfterPrimaryAndOneBackup() {
        assertNull(StoryFallbackPolicy.nextCandidate(
            primary = StoryProvider.GROQ,
            allowed = all,
            configured = all,
            attempted = setOf(StoryProvider.GROQ, StoryProvider.GEMINI),
            approvedForAutomaticUse = setOf(StoryProvider.COHERE),
            httpStatus = 503,
            cancelled = false
        ))
    }

}
