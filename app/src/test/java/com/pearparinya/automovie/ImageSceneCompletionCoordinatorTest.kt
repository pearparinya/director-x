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

    @Test fun batchCompletesAllPendingScenesInOrder() {
        val images = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        val start = ImageSceneQueue.State((1..5).toList()).markCompleted(1)
        val result = ImageSceneCompletionCoordinator.completePending(
            start,
            saveImage = { scene -> images.add(scene); true },
            saveProgress = { state -> progress.add(state.completedCount); true }
        )
        assertEquals(listOf(2, 3, 4, 5), images)
        assertEquals(listOf(2, 3, 4, 5), progress)
        assertEquals(100, result.progressPercent)
    }

    @Test fun batchStopsOnFirstFailedImageAndReturnsSavedProgress() {
        val images = mutableListOf<Int>()
        val result = ImageSceneCompletionCoordinator.completePending(
            ImageSceneQueue.State((1..5).toList()),
            saveImage = { scene -> images.add(scene); scene != 3 },
            saveProgress = { true }
        )
        assertEquals(listOf(1, 2, 3), images)
        assertEquals(2, result.completedCount)
        assertEquals(3, result.nextPendingScene)
    }

    @Test fun batchStopsOnProgressFailureWithoutSkippingScene() {
        val result = ImageSceneCompletionCoordinator.completePending(
            ImageSceneQueue.State((1..5).toList()),
            saveImage = { true },
            saveProgress = { state -> state.completedCount < 3 }
        )
        assertEquals(2, result.completedCount)
        assertEquals(3, result.nextPendingScene)
    }

    @Test fun cancellationStopsBeforeNextSceneAndPreservesSavedProgress() {
        val images = mutableListOf<Int>()
        val result = ImageSceneCompletionCoordinator.completePending(
            ImageSceneQueue.State((1..5).toList()),
            saveImage = { scene -> images.add(scene); true },
            saveProgress = { true },
            shouldContinue = { images.size < 2 }
        )
        assertEquals(listOf(1, 2), images)
        assertEquals(2, result.completedCount)
        assertEquals(3, result.nextPendingScene)
    }

    @Test fun cancellationBeforeFirstScenePerformsNoWrites() {
        var writes = 0
        val result = ImageSceneCompletionCoordinator.completePending(
            ImageSceneQueue.State(listOf(1, 2)),
            saveImage = { writes++; true },
            saveProgress = { writes++; true },
            shouldContinue = { false }
        )
        assertEquals(0, writes)
        assertEquals(1, result.nextPendingScene)
    }

    @Test fun cancellationCheckExceptionStopsSafely() {
        var writes = 0
        val result = ImageSceneCompletionCoordinator.completePending(
            ImageSceneQueue.State(listOf(1, 2)),
            saveImage = { writes++; true },
            saveProgress = { writes++; true },
            shouldContinue = { throw IllegalStateException("Worker stopped") }
        )
        assertEquals(0, writes)
        assertEquals(0, result.completedCount)
    }

    @Test fun batchLimitStopsAfterQuotaAndResumesRemainingScenes() {
        val saved = mutableListOf<Int>()
        val initial = ImageSceneQueue.State((1..5).toList())
        val first = ImageSceneCompletionCoordinator.completePending(
            initial,
            saveImage = { scene -> saved.add(scene); true },
            saveProgress = { true },
            maxScenes = 2
        )
        assertEquals(listOf(1, 2), saved)
        assertEquals(3, first.nextPendingScene)
        val resumed = ImageSceneCompletionCoordinator.completePending(
            first,
            saveImage = { scene -> saved.add(scene); true },
            saveProgress = { true },
            maxScenes = 3
        )
        assertEquals(listOf(1, 2, 3, 4, 5), saved)
        assertEquals(100, resumed.progressPercent)
    }

    @Test fun zeroQuotaDoesNotInvokeAnyWrites() {
        var writes = 0
        val result = ImageSceneCompletionCoordinator.completePending(
            ImageSceneQueue.State(listOf(1, 2)),
            saveImage = { writes++; true },
            saveProgress = { writes++; true },
            maxScenes = 0
        )
        assertEquals(0, writes)
        assertEquals(0, result.completedCount)
    }

    @Test fun negativeQuotaIsRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ImageSceneCompletionCoordinator.completePending(
                ImageSceneQueue.State(listOf(1)),
                saveImage = { true },
                saveProgress = { true },
                maxScenes = -1
            )
        }
    }

    @Test fun fiveSceneBatchStopsAtFiveAndResumesRemaining() {
        val saved = mutableListOf<Int>()
        val initial = ImageSceneQueue.State((1..13).toList())
        val first = ImageSceneCompletionCoordinator.completePending(
            initial, saveImage = { saved.add(it); true },
            saveProgress = { true }, maxScenes = 5
        )
        assertEquals(listOf(1, 2, 3, 4, 5), saved)
        assertEquals(6, first.nextPendingScene)
        val restored = ImageSceneQueue.restore(initial.sceneIds, first.encodeProgress())
        val second = ImageSceneCompletionCoordinator.completePending(
            restored, saveImage = { saved.add(it); true },
            saveProgress = { true }, maxScenes = 5
        )
        assertEquals((1..10).toList(), saved)
        assertEquals(11, second.nextPendingScene)
    }

    @Test fun fiveSceneBatchStopsOnFailedProgressAndResumesFailedScene() {
        val attempted = mutableListOf<Int>()
        val initial = ImageSceneQueue.State((1..8).toList())
        val first = ImageSceneCompletionCoordinator.completePending(
            initial, saveImage = { attempted.add(it); true },
            saveProgress = { it.completedCount < 3 }, maxScenes = 5
        )
        assertEquals(2, first.completedCount)
        assertEquals(3, first.nextPendingScene)
        val resumed = ImageSceneCompletionCoordinator.completePending(
            ImageSceneQueue.restore(initial.sceneIds, first.encodeProgress()),
            saveImage = { attempted.add(it); true },
            saveProgress = { true }, maxScenes = 5
        )
        assertEquals(listOf(1, 2, 3, 3, 4, 5, 6, 7), attempted)
        assertEquals(8, resumed.nextPendingScene)
    }

}
