package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageFreeTierBatchPlannerTest {
    private fun provider(
        id: String = "free", remaining: Int? = 5, free: Boolean = true,
        references: Boolean = true, retryAt: Long = 0L
    ) = ImageProviderRotation.Provider(id, free, references, remaining, retryAt)

    @Test fun batchNeverExceedsConfirmedFreeQuota() {
        assertEquals(
            ImageFreeTierBatchPlanner.Plan.Ready("free", 2),
            ImageFreeTierBatchPlanner.plan(
                listOf(provider(remaining = 2)), 1000L, true, 23, 10
            )
        )
    }

    @Test fun batchNeverExceedsPendingScenesOrRequestedLimit() {
        assertEquals(
            ImageFreeTierBatchPlanner.Plan.Ready("free", 3),
            ImageFreeTierBatchPlanner.plan(
                listOf(provider(remaining = 20)), 1000L, false, 3, 10
            )
        )
        assertEquals(
            ImageFreeTierBatchPlanner.Plan.Ready("free", 2),
            ImageFreeTierBatchPlanner.plan(
                listOf(provider(remaining = 20)), 1000L, false, 23, 2
            )
        )
    }

    @Test fun unknownQuotaAndUnconfirmedFreeTierNeverStartBatch() {
        val blocked = ImageFreeTierBatchPlanner.Plan.NeedsFreeProvider
        assertEquals(blocked, ImageFreeTierBatchPlanner.plan(
            listOf(provider(remaining = null)), 1000L, false, 23, 10
        ))
        assertEquals(blocked, ImageFreeTierBatchPlanner.plan(
            listOf(provider(free = false)), 1000L, false, 23, 10
        ))
    }

    @Test fun respectsReferenceImageSupportAndProviderCooldown() {
        assertEquals(
            ImageFreeTierBatchPlanner.Plan.WaitUntil(5000L),
            ImageFreeTierBatchPlanner.plan(
                listOf(provider(retryAt = 5000L)), 1000L, true, 23, 10
            )
        )
        assertEquals(
            ImageFreeTierBatchPlanner.Plan.NeedsFreeProvider,
            ImageFreeTierBatchPlanner.plan(
                listOf(provider(references = false)), 1000L, true, 23, 10
            )
        )
    }

    @Test fun rotatesBetweenAvailableFreeProviders() {
        assertEquals(
            ImageFreeTierBatchPlanner.Plan.Ready("second", 4),
            ImageFreeTierBatchPlanner.plan(
                listOf(provider("first", 5), provider("second", 4)),
                1000L, false, 23, 10, "first"
            )
        )
    }

    @Test fun noPendingScenesOrZeroRequestedBatchDoesNotStart() {
        val blocked = ImageFreeTierBatchPlanner.Plan.NeedsFreeProvider
        assertEquals(blocked, ImageFreeTierBatchPlanner.plan(
            listOf(provider()), 1000L, false, 0, 10
        ))
        assertEquals(blocked, ImageFreeTierBatchPlanner.plan(
            listOf(provider()), 1000L, false, 23, 0
        ))
    }

    @Test fun negativeSceneCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageFreeTierBatchPlanner.plan(listOf(provider()), 1000L, false, -1, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImageFreeTierBatchPlanner.plan(listOf(provider()), 1000L, false, 23, -1)
        }
    }
}
