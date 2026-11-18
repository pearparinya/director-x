package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageSceneProgressMergeTest {
    @Test fun staleWorkerCannotRemoveAlreadyCompletedScenes() {
        val persisted = ImageSceneQueue.State(listOf(1, 2, 3, 4), setOf(1, 2, 3))
        val staleWorker = ImageSceneQueue.State(listOf(1, 2, 3, 4), setOf(1, 4))
        val merged = ImageSceneProgressMerge.merge(persisted, staleWorker)
        assertEquals(setOf(1, 2, 3, 4), merged.completedSceneIds)
        assertEquals(100, merged.progressPercent)
    }

    @Test fun mergedProgressIsIdempotent() {
        val state = ImageSceneQueue.State(listOf(1, 2, 3), setOf(1, 2))
        assertEquals(state, ImageSceneProgressMerge.merge(state, state))
    }

    @Test fun mergeRejectsDifferentSceneOrder() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSceneProgressMerge.merge(
                ImageSceneQueue.State(listOf(1, 2)),
                ImageSceneQueue.State(listOf(2, 1))
            )
        }
    }

    @Test fun mergePreservesUnfinishedScenes() {
        val result = ImageSceneProgressMerge.merge(
            ImageSceneQueue.State(listOf(1, 2, 3), setOf(1)),
            ImageSceneQueue.State(listOf(1, 2, 3), setOf(2))
        )
        assertEquals(3, result.nextPendingScene)
        assertEquals(2, result.completedCount)
    }
}
