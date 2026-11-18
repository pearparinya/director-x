package com.pearparinya.automovie

/**
 * Connects verified free-tier batch planning with durable scene completion.
 * The caller supplies a provider-specific image save; this class makes no API calls.
 * Run on a background worker, not the UI thread.
 */
internal object ImageFreeTierSceneRunner {
    data class Result(
        val plan: ImageFreeTierBatchPlanner.Plan,
        val state: ImageSceneQueue.State,
        /** Attempts include failed requests because providers may still charge quota. */
        val attemptedRequests: Int,
        val completedScenes: Int
    )

    fun run(
        current: ImageSceneQueue.State,
        providers: List<ImageProviderRotation.Provider>,
        nowEpochMs: Long,
        requiresReferenceImage: Boolean,
        requestedBatchSize: Int,
        saveImage: (providerId: String, sceneId: Int) -> Boolean,
        saveProgress: (ImageSceneQueue.State) -> Boolean,
        shouldContinue: () -> Boolean = { true },
        previousProviderId: String? = null
    ): Result {
        val plan = ImageFreeTierBatchPlanner.plan(
            providers = providers,
            nowEpochMs = nowEpochMs,
            requiresReferenceImage = requiresReferenceImage,
            pendingScenes = current.totalCount - current.completedCount,
            requestedBatchSize = requestedBatchSize,
            previousProviderId = previousProviderId
        )
        if (plan !is ImageFreeTierBatchPlanner.Plan.Ready) return Result(plan, current, 0, 0)
        var attemptedRequests = 0
        val updated = ImageSceneCompletionCoordinator.completePending(
            current = current,
            saveImage = { sceneId ->
                attemptedRequests++
                saveImage(plan.providerId, sceneId)
            },
            saveProgress = saveProgress,
            shouldContinue = shouldContinue,
            maxScenes = plan.maxScenes
        )
        return Result(plan, updated, attemptedRequests, updated.completedCount - current.completedCount)
    }
}
