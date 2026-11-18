package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFreeTierSceneRunnerTest {
    private fun provider(id: String, remaining: Int?, free: Boolean = true) =
        ImageProviderRotation.Provider(id, free, true, remaining)

    @Test fun runsOnlyConfirmedFreeQuotaAndResumesOnNextBatch() {
        val images = mutableListOf<String>()
        val start = ImageSceneQueue.State((1..5).toList())
        val first = ImageFreeTierSceneRunner.run(
            current = start,
            providers = listOf(provider("free-a", 2)),
            nowEpochMs = 1000L,
            requiresReferenceImage = true,
            requestedBatchSize = 5,
            saveImage = { id, scene -> images.add("$id:$scene"); true },
            saveProgress = { true }
        )
        assertEquals(ImageFreeTierBatchPlanner.Plan.Ready("free-a", 2), first.plan)
        assertEquals(2, first.state.completedCount)
        val second = ImageFreeTierSceneRunner.run(
            current = first.state,
            providers = listOf(provider("free-b", 3)),
            nowEpochMs = 1001L,
            requiresReferenceImage = true,
            requestedBatchSize = 5,
            saveImage = { id, scene -> images.add("$id:$scene"); true },
            saveProgress = { true }
        )
        assertEquals(listOf("free-a:1", "free-a:2", "free-b:3", "free-b:4", "free-b:5"), images)
        assertEquals(100, second.state.progressPercent)
    }

    @Test fun unknownOrPaidQuotaMakesNoCalls() {
        var calls = 0
        val start = ImageSceneQueue.State(listOf(1, 2))
        val result = ImageFreeTierSceneRunner.run(
            current = start,
            providers = listOf(provider("unknown", null), provider("paid", 10, false)),
            nowEpochMs = 1000L,
            requiresReferenceImage = false,
            requestedBatchSize = 2,
            saveImage = { _, _ -> calls++; true },
            saveProgress = { calls++; true }
        )
        assertEquals(ImageFreeTierBatchPlanner.Plan.NeedsFreeProvider, result.plan)
        assertEquals(0, calls)
        assertEquals(start, result.state)
    }

    @Test fun failedImageStopsWithoutAdvancingRemainingScenes() {
        val scenes = mutableListOf<Int>()
        val result = ImageFreeTierSceneRunner.run(
            current = ImageSceneQueue.State(listOf(1, 2, 3)),
            providers = listOf(provider("free", 3)),
            nowEpochMs = 1000L,
            requiresReferenceImage = false,
            requestedBatchSize = 3,
            saveImage = { _, scene -> scenes.add(scene); scene != 2 },
            saveProgress = { true }
        )
        assertEquals(listOf(1, 2), scenes)
        assertEquals(1, result.state.completedCount)
        assertEquals(2, result.state.nextPendingScene)
    }
}
