package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionPipelinePlannerTest {
    private val p = ProductionPipelinePlanner
    private fun episode(
        flow: Boolean = true,
        images: Set<Int> = setOf(1, 2),
        videos: Set<Int> = emptySet()
    ) = ProductionPipelinePlanner.Episode(1, listOf(1, 2), flow, images, videos)

    @Test fun invalidStoryCannotAdvance() {
        assertEquals(ProductionPipelinePlanner.Stage.STORY,
            p.next(false, listOf(episode())).stage)
    }

    @Test fun missingFlowTakesPriorityOverImages() {
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.FLOW, 1),
            p.next(true, listOf(episode(flow = false, images = emptySet()))))
    }

    @Test fun missingImageResumesAtFirstUnverifiedScene() {
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.IMAGE, 1, 2),
            p.next(true, listOf(episode(images = setOf(1)))))
    }

    @Test fun imagesDoNotProveVideosExist() {
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.VIDEO, 1, 1),
            p.next(true, listOf(episode())))
    }

    @Test fun finalOutputRequiresSeparateVerification() {
        val all = listOf(episode(videos = setOf(1, 2)))
        assertEquals(ProductionPipelinePlanner.Stage.FINAL_OUTPUT, p.next(true, all).stage)
        assertEquals(ProductionPipelinePlanner.Stage.DONE,
            p.next(true, all, finalOutputVerified = true).stage)
    }

    @Test fun invalidOrDuplicateSceneIdsBlockProduction() {
        val invalid = ProductionPipelinePlanner.Episode(1, listOf(1, 1), true, setOf(1))
        assertEquals(ProductionPipelinePlanner.Stage.STORY, p.next(true, listOf(invalid)).stage)
    }

    @Test fun resumedCursorDoesNotOverrideActualArtifacts() {
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.IMAGE, 1, 1),
            p.next(true, listOf(episode(images = emptySet()))))
    }
    @Test fun missingVideoInSecondEpisodeResumesExactScene() {
        val first = episode(videos = setOf(1, 2))
        val second = ProductionPipelinePlanner.Episode(
            2, listOf(1, 2), true, setOf(1, 2), setOf(1)
        )
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.VIDEO, 2, 2),
            p.next(true, listOf(first, second)))
    }

    @Test fun finalOutputFlagCannotBypassMissingVideo() {
        assertEquals(ProductionPipelinePlanner.Stage.VIDEO,
            p.next(true, listOf(episode()), finalOutputVerified = true).stage)
    }

    @Test fun duplicateEpisodeIdsBlockProduction() {
        assertEquals(ProductionPipelinePlanner.Stage.STORY,
            p.next(true, listOf(episode(), episode())).stage)
    }

    @Test fun missingImageCannotBeBypassedByCompletedVideo() {
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.IMAGE, 1, 2),
            p.next(true, listOf(episode(images = setOf(1), videos = setOf(1, 2)))))
    }

    @Test fun completeVideosWithoutFinalOutputNeverMarkDone() {
        val all = listOf(episode(videos = setOf(1, 2)))
        assertEquals(ProductionPipelinePlanner.Stage.FINAL_OUTPUT, p.next(true, all).stage)
    }

    @Test fun emptyEpisodesCannotAdvance() {
        assertEquals(ProductionPipelinePlanner.Stage.STORY, p.next(true, emptyList()).stage)
    }

    @Test fun missingVideoAfterVerifiedFirstSceneResumesSecondScene() {
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.VIDEO, 1, 2),
            p.next(true, listOf(episode(videos = setOf(1)))))
    }

    @Test fun completedEpisodeDoesNotSkipIncompleteSecondEpisode() {
        val first = episode(videos = setOf(1, 2))
        val second = ProductionPipelinePlanner.Episode(2, listOf(1, 2), false, emptySet())
        assertEquals(ProductionPipelinePlanner.Decision(ProductionPipelinePlanner.Stage.FLOW, 2),
            p.next(true, listOf(first, second), finalOutputVerified = true))
    }

    @Test fun invalidSceneNumberBlocksDoneEvenWithFinalOutputFlag() {
        val invalid = ProductionPipelinePlanner.Episode(1, listOf(0, 2), true, setOf(2), setOf(2))
        assertEquals(ProductionPipelinePlanner.Stage.STORY,
            p.next(true, listOf(invalid), finalOutputVerified = true).stage)
    }

}
