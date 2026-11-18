package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ImageSceneCompletionCoordinatorTest {
    @Test fun savesImageBeforeProgressAndAdvancesOneScene() {
        val events = mutableListOf<String>()
        val start = ImageSceneQueue.State((1..23).toList())
        val result = ImageSceneCompletionCoordinator.completeNext(
            start,
            saveImage = { scene -> events.add("image:$scene"); true },
            saveProgress = { state -> events.add("progress:${state.completedCount}"); true }
        )
        assertEquals(listOf("image:1", "progress:1"), events)
        assertEquals(2, result?.nextPendingScene)
        assertEquals(1, result?.completedCount)
        assertEquals(0, start.completedCount)
    }

    @Test fun failedImageSaveDoesNotRecordCompletion() {
        var progressCalled = false
        val result = ImageSceneCompletionCoordinator.completeNext(
            ImageSceneQueue.State(listOf(1, 2)),
            saveImage = { false },
            saveProgress = { progressCalled = true; true }
        )
        assertNull(result)
        assertFalse(progressCalled)
    }

    @Test fun failedProgressSaveDoesNotReturnAdvancedState() {
        val result = ImageSceneCompletionCoordinator.completeNext(
            ImageSceneQueue.State(listOf(1, 2)),
            saveImage = { true },
            saveProgress = { false }
        )
        assertNull(result)
    }

    @Test fun resumeSkipsAlreadyCompletedScenes() {
        val completed = ImageSceneQueue.State((1..23).toList())
            .markCompleted(1).markCompleted(2).markCompleted(3)
        var savedScene = -1
        val result = ImageSceneCompletionCoordinator.completeNext(
            completed,
            saveImage = { scene -> savedScene = scene; true },
            saveProgress = { true }
        )
        assertEquals(4, savedScene)
        assertEquals(5, result?.nextPendingScene)
    }

    @Test fun completedQueuePerformsNoWrites() {
        var calls = 0
        val done = ImageSceneQueue.State(listOf(1)).markCompleted(1)
        val result = ImageSceneCompletionCoordinator.completeNext(
            done,
            saveImage = { calls++; true },
            saveProgress = { calls++; true }
        )
        assertEquals(0, calls)
        assertEquals(done, result)
    }
    @Test fun thrownImageSaveDoesNotRecordProgress() {
        var progressCalled = false
        val result = ImageSceneCompletionCoordinator.completeNext(
            ImageSceneQueue.State(listOf(1, 2)),
            saveImage = { throw IllegalStateException("Disk unavailable") },
            saveProgress = { progressCalled = true; true }
        )
        assertNull(result)
        assertFalse(progressCalled)
    }

    @Test fun thrownProgressSaveDoesNotReturnAdvancedState() {
        val result = ImageSceneCompletionCoordinator.completeNext(
            ImageSceneQueue.State(listOf(1, 2)),
            saveImage = { true },
            saveProgress = { throw IllegalStateException("Preferences unavailable") }
        )
        assertNull(result)
    }

}
