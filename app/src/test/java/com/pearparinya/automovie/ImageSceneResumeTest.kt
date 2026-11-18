package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSceneResumeTest {
    @Test fun refreshedSnapshotSkipsScenesSavedByAnotherWorker() {
        val stale = ImageSceneQueue.State(listOf(1, 2, 3, 4), setOf(1))
        val persisted = ImageSceneQueue.State(listOf(1, 2, 3, 4), setOf(1, 2, 3))
        val resumed = ImageSceneProgressMerge.merge(persisted, stale)
        val generated = mutableListOf<Int>()
        val result = ImageSceneCompletionCoordinator.completePending(
            current = resumed,
            saveImage = { generated.add(it); true },
            saveProgress = { true }
        )
        assertEquals(listOf(4), generated)
        assertEquals(100, result.progressPercent)
    }

    @Test fun refreshedSnapshotPreservesBothWorkersProgress() {
        val stale = ImageSceneQueue.State(listOf(1, 2, 3), setOf(1, 3))
        val persisted = ImageSceneQueue.State(listOf(1, 2, 3), setOf(1, 2))
        val resumed = ImageSceneProgressMerge.merge(persisted, stale)
        var generated = 0
        val result = ImageSceneCompletionCoordinator.completePending(
            current = resumed,
            saveImage = { generated++; true },
            saveProgress = { true }
        )
        assertEquals(0, generated)
        assertEquals(3, result.completedCount)
    }

    @Test fun refreshedSnapshotStillHonorsCancellation() {
        val stale = ImageSceneQueue.State(listOf(1, 2, 3))
        val persisted = ImageSceneQueue.State(listOf(1, 2, 3), setOf(1))
        val resumed = ImageSceneProgressMerge.merge(persisted, stale)
        var generated = 0
        val result = ImageSceneCompletionCoordinator.completePending(
            current = resumed,
            saveImage = { generated++; true },
            saveProgress = { true },
            shouldContinue = { false }
        )
        assertEquals(0, generated)
        assertEquals(2, result.nextPendingScene)
    }
}
