package com.pearparinya.automovie

/**
 * Pure scheduling policy for free-tier image providers.
 * No network calls or charges are made by this class.
 * Callers must persist queue progress and provider cooldowns.
 */
internal object ImageProviderRotation {
    data class Provider(
        val id: String,
        val freeTierConfirmed: Boolean,
        val supportsReferenceImage: Boolean,
        val remainingFreeRequests: Int?,
        val retryAfterEpochMs: Long = 0L
    )

    sealed class Decision {
        data class Use(val providerId: String) : Decision()
        data class WaitUntil(val epochMs: Long) : Decision()
        object NeedsFreeProvider : Decision()
    }

    fun choose(
        providers: List<Provider>,
        nowEpochMs: Long,
        requiresReferenceImage: Boolean,
        previousProviderId: String? = null
    ): Decision {
        val eligible = providers.filter {
            it.id.isNotBlank() && it.freeTierConfirmed &&
                (!requiresReferenceImage || it.supportsReferenceImage) &&
                (it.remainingFreeRequests == null || it.remainingFreeRequests > 0)
        }.distinctBy { it.id }
        val ready = eligible.filter { it.retryAfterEpochMs <= nowEpochMs }
        if (ready.isNotEmpty()) {
            // Rotate rather than continually selecting the first provider.
            val next = ready.firstOrNull { it.id != previousProviderId } ?: ready.first()
            return Decision.Use(next.id)
        }
        val nextReset = eligible.map { it.retryAfterEpochMs }
            .filter { it > nowEpochMs }.minOrNull()
        return if (nextReset != null) Decision.WaitUntil(nextReset)
            else Decision.NeedsFreeProvider
    }
}
