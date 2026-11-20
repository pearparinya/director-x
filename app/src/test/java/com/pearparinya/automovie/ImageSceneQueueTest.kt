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

    @Test fun savedProgressRestoresOnlyUnfinishedScenes() {
        val state = ImageSceneQueue.State((1..23).toList())
            .markCompleted(1).markCompleted(3).markCompleted(2)
        val restored = ImageSceneQueue.restore((1..23).toList(), state.encodeProgress())
        assertEquals("1,2,3", state.encodeProgress())
        assertEquals(4, restored.nextPendingScene)
        assertEquals(3, restored.completedCount)
    }

    @Test fun corruptedSavedProgressIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSceneQueue.restore(listOf(1, 2), "1,invalid")
        }
    }

    @Test fun progressFromDifferentProjectIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSceneQueue.restore(listOf(1, 2), "1,3")
        }
    }

    @Test fun duplicateSceneIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSceneQueue.State(listOf(1, 1))
        }
    }
    @Test fun twentyThreeScenesAfterResumeAreSplitIntoTwoBatches() {
        val restored = ImageSceneQueue.restore((1..23).toList(), (1..15).joinToString(","))
        val pending = restored.sceneIds.filter { it !in restored.completedSceneIds }
        assertEquals(listOf((16..20).toList(), (21..23).toList()), pending.chunked(5))
    }

    @Test fun sparseSceneNumbersKeepOriginalIdsAcrossBatches() {
        val ids = listOf(2, 4, 6, 8, 10, 12, 14)
        val restored = ImageSceneQueue.restore(ids, "4,10")
        val pending = restored.sceneIds.filter { it !in restored.completedSceneIds }
        assertEquals(listOf(listOf(2, 6, 8, 12, 14)), pending.chunked(5))
    }

    @Test fun allCompletedScenesProduceNoPendingBatch() {
        val ids = (1..5).toList()
        val restored = ImageSceneQueue.restore(ids, ids.joinToString(","))
        val pending = restored.sceneIds.filter { it !in restored.completedSceneIds }
        assertEquals(emptyList<List<Int>>(), pending.chunked(5))
    }

}
