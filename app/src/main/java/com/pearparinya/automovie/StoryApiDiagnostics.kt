package com.pearparinya.automovie

/**
 * Safe, offline diagnostics for the API test screen.
 *
 * No network access, no credentials in results, and no provider quota usage.
 * A real connectivity test must be a separate, explicitly requested action.
 */
internal object StoryApiDiagnostics {
    enum class State { READY, MISSING_KEY, DISABLED, NOT_APPROVED_FOR_FALLBACK }

    data class ProviderStatus(
        val provider: StoryProvider,
        val state: State,
        val canUseAsFallback: Boolean
    )

    fun inspect(
        providers: List<StoryProvider>,
        enabled: Set<StoryProvider>,
        configured: Set<StoryProvider>,
        approvedForAutomaticUse: Set<StoryProvider>
    ): List<ProviderStatus> = providers.distinct().map { provider ->
        val state = when {
            provider !in enabled -> State.DISABLED
            provider !in configured -> State.MISSING_KEY
            provider !in approvedForAutomaticUse -> State.NOT_APPROVED_FOR_FALLBACK
            else -> State.READY
        }
        ProviderStatus(provider, state, state == State.READY)
    }

    /**
     * Simulate one failover without performing any HTTP request.
     * The primary is recorded as attempted, and the same safety budget
     * used by the runtime policy is applied.
     */
    fun simulateFallback(
        primary: StoryProvider,
        enabled: Set<StoryProvider>,
        configured: Set<StoryProvider>,
        approvedForAutomaticUse: Set<StoryProvider>,
        httpStatus: Int?,
        cancelled: Boolean = false,
        maxTotalAttempts: Int = 2,
        order: List<StoryProvider> = StoryProvider.activeProviders
    ): StoryProvider? = StoryFallbackPolicy.nextCandidate(
        primary = primary,
        allowed = enabled,
        configured = configured,
        attempted = setOf(primary),
        approvedForAutomaticUse = approvedForAutomaticUse,
        httpStatus = httpStatus,
        cancelled = cancelled,
        maxTotalAttempts = maxTotalAttempts,
        active = order
    )
}
