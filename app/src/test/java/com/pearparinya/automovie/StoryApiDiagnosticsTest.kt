package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class StoryApiDiagnosticsTest {
    private val primary = StoryProvider.GROQ
    private val backup = StoryProvider.GEMINI
    private val all = StoryProvider.activeProviders.toSet()

    @Test fun offlineInspectionDoesNotExposeKeysAndDeduplicates() {
        val result = StoryApiDiagnostics.inspect(
            listOf(primary, primary, backup),
            enabled = all,
            configured = setOf(primary, backup),
            approvedForAutomaticUse = setOf(backup)
        )
        assertEquals(2, result.size)
        assertEquals(StoryApiDiagnostics.State.NOT_APPROVED_FOR_FALLBACK, result[0].state)
        assertEquals(StoryApiDiagnostics.State.READY, result[1].state)
        assertFalse(result[0].canUseAsFallback)
        assertTrue(result[1].canUseAsFallback)
    }

    @Test fun disabledProviderIsNeverMarkedReady() {
        val result = StoryApiDiagnostics.inspect(
            listOf(backup), emptySet(), setOf(backup), setOf(backup)
        )
        assertEquals(StoryApiDiagnostics.State.DISABLED, result.single().state)
    }

    @Test fun missingKeyTakesPriorityOverApproval() {
        val result = StoryApiDiagnostics.inspect(
            listOf(backup), setOf(backup), emptySet(), setOf(backup)
        )
        assertEquals(StoryApiDiagnostics.State.MISSING_KEY, result.single().state)
    }

    @Test fun simulationUsesSameTransientErrorPolicyAsRuntime() {
        assertEquals(backup, StoryApiDiagnostics.simulateFallback(
            primary, all, all, setOf(backup), 429
        ))
        assertNull(StoryApiDiagnostics.simulateFallback(
            primary, all, all, setOf(backup), 401
        ))
    }

    @Test fun simulationHonorsCancellationAndAttemptBudget() {
        assertNull(StoryApiDiagnostics.simulateFallback(
            primary, all, all, setOf(backup), 503, cancelled = true
        ))
        assertNull(StoryApiDiagnostics.simulateFallback(
            primary, all, all, setOf(backup), 503, maxTotalAttempts = 1
        ))
    }

    @Test fun simulationCannotUseUnapprovedOrUnconfiguredProvider() {
        assertNull(StoryApiDiagnostics.simulateFallback(
            primary, all, setOf(primary), setOf(backup), 503
        ))
        assertNull(StoryApiDiagnostics.simulateFallback(
            primary, all, all, emptySet(), 503
        ))
    }
}
