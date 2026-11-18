package com.pearparinya.automovie

/**
 * Plans a safe image-scene batch using only confirmed free-tier quota.
 * A plan is not a reservation: the caller must refresh provider quota before
 * each subsequent batch and stop on rate limits or provider errors.
 */
internal object ImageFreeTierBatchPlanner {
    sealed class Plan {
        data class Ready(val providerId: String, val maxScenes: Int) : Plan()
        data class WaitUntil(val epochMs: Long) : Plan()
        object NeedsFreeProvider : Plan()
    }

    fun plan(
        providers: List<ImageProviderRotation.Provider>,
        nowEpochMs: Long,
        requiresReferenceImage: Boolean,
        pendingScenes: Int,
        requestedBatchSize: Int,
        previousProviderId: String? = null
    ): Plan {
        require(pendingScenes >= 0) { "Pending scene count must not be negative" }
        require(requestedBatchSize >= 0) { "Requested batch size must not be negative" }
        if (pendingScenes == 0 || requestedBatchSize == 0) return Plan.NeedsFreeProvider
        return when (val decision = ImageProviderRotation.choose(
            providers, nowEpochMs, requiresReferenceImage, previousProviderId
        )) {
            is ImageProviderRotation.Decision.Use -> {
                val provider = providers.first {
                    it.id == decision.providerId && it.freeTierConfirmed &&
                        (!requiresReferenceImage || it.supportsReferenceImage) &&
                        (it.remainingFreeRequests ?: 0) > 0 &&
                        it.retryAfterEpochMs <= nowEpochMs
                }
                Plan.Ready(
                    provider.id,
                    minOf(pendingScenes, requestedBatchSize, provider.remainingFreeRequests!!)
                )
            }
            is ImageProviderRotation.Decision.WaitUntil -> Plan.WaitUntil(decision.epochMs)
            ImageProviderRotation.Decision.NeedsFreeProvider -> Plan.NeedsFreeProvider
        }
    }
}
