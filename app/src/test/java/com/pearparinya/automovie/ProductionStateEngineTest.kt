package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionStateEngineTest {
    @Test fun savedProgressCannotMarkMissingFilesComplete() {
        val state = ProductionStateEngine.evaluate((1..8).toList(), true, emptySet(), true)
        assertEquals(0, state.verifiedImages)
        assertEquals(ProductionStateEngine.Status.PENDING, state.images)
        assertEquals(8, state.missingSceneIds.size)
    }

    @Test fun completeImagesRequireAllVerifiedSceneIds() {
        val state = ProductionStateEngine.evaluate((1..8).toList(), true, (1..8).toSet(), true)
        assertEquals(ProductionStateEngine.Status.COMPLETED, state.images)
    }

    @Test fun partialImagesRemainInProgress() {
        val state = ProductionStateEngine.evaluate((1..8).toList(), true, setOf(1, 2, 3), false)
        assertEquals(ProductionStateEngine.Status.IN_PROGRESS, state.images)
        assertEquals(listOf(4, 5, 6, 7, 8), state.missingSceneIds)
    }

    @Test fun invalidSceneIdsAreErrorsAndBlockProduction() {
        val state = ProductionStateEngine.evaluate(listOf(1, 1), true, setOf(1), true)
        assertEquals(ProductionStateEngine.Status.ERROR, state.story)
        assertEquals(ProductionStateEngine.Status.LOCKED, state.images)
    }

    @Test fun invalidStoryLocksDownstream() {
        val state = ProductionStateEngine.evaluate(listOf(1, 2), false, setOf(1, 2), true)
        assertEquals(ProductionStateEngine.Status.LOCKED, state.images)
        assertEquals(ProductionStateEngine.Status.LOCKED, state.flow)
    }
}
