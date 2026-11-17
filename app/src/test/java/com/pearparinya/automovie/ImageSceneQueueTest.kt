package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageSceneQueueTest {
    @Test fun twentyThreeScenesResumeFromFirstMissingImage() {
        val original = ImageSceneQueue.State((1..23).toList())
        val saved = (1..15).fold(original) { state, scene -> state.markCompleted(scene) }
        val restored = ImageSceneQueue.State(saved.sceneIds, saved.completedSceneIds)
        assertEquals(15, restored.completedCount)
        assertEquals(65, restored.progressPercent)
        assertEquals(16, restored.nextPendingScene)
    }

    @Test fun completingSameSceneTwiceDoesNotIncreaseProgress() {
        val state = ImageSceneQueue.State(listOf(1, 2)).markCompleted(1).markCompleted(1)
        assertEquals(1, state.completedCount)
    }

    @Test fun completedQueueHasNoPendingScene() {
        val state = ImageSceneQueue.State(listOf(1)).markCompleted(1)
        assertEquals(100, state.progressPercent)
        assertNull(state.nextPendingScene)
    }

    @Test fun duplicateSceneIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSceneQueue.State(listOf(1, 1))
        }
    }
}
