package com.pearparinya.automovie

/**
 * Pure policy for future story API fallback. This class never makes a request,
 * reads a secret, or assumes that a provider is free.
 *
 * A fallback is only eligible if the user explicitly allowed its provider,
 * a key is configured, and the provider is not already attempted.
 */
internal object StoryFallbackPolicy {
    fun candidates(
        primary: StoryProvider,
        allowed: Set<StoryProvider>,
        configured: Set<StoryProvider>,
        attempted: Set<StoryProvider>,
        approvedForAutomaticUse: Set<StoryProvider>,
        active: List<StoryProvider> = StoryProvider.activeProviders
    ): List<StoryProvider> {
        val permitted = allowed.intersect(configured).intersect(approvedForAutomaticUse)
        return active.distinct().filter {
            it != primary && it !in attempted && it in permitted
        }
    }

    /**
     * Limit total requests per story operation. This counts the primary request too.
     * The caller must record every attempted provider before requesting another.
     * No fallback is allowed after cancellation, when no attempt remains, or
     * for non-transient HTTP failures.
     */
    fun nextCandidate(
        primary: StoryProvider,
        allowed: Set<StoryProvider>,
        configured: Set<StoryProvider>,
        attempted: Set<StoryProvider>,
        approvedForAutomaticUse: Set<StoryProvider>,
        httpStatus: Int?,
        cancelled: Boolean,
        maxTotalAttempts: Int = 2,
        active: List<StoryProvider> = StoryProvider.activeProviders
    ): StoryProvider? {
        if (cancelled || !canFallback(httpStatus)) return null
        if (maxTotalAttempts <= 1 || attempted.size >= maxTotalAttempts) return null
        // Only a previously attempted primary may trigger fallback.
        if (primary !in attempted) return null
        return candidates(
            primary, allowed, configured, attempted,
            approvedForAutomaticUse, active
        ).firstOrNull()
    }

    /** Never switch on authentication, invalid input, or other non-transient errors. */
    fun canFallback(httpStatus: Int?): Boolean =
        httpStatus == 408 || httpStatus == 429 ||
            (httpStatus != null && httpStatus in 500..599)
}
