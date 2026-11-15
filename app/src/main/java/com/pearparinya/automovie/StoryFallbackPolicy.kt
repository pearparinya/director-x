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

    /** Never switch on authentication, invalid input, or other non-transient errors. */
    fun canFallback(httpStatus: Int?): Boolean =
        httpStatus == 408 || httpStatus == 429 ||
            (httpStatus != null && httpStatus in 500..599)
}
