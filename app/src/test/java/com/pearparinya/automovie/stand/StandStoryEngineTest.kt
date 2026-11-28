package com.pearparinya.automovie.stand

import org.junit.Assert.*
import org.junit.Test

class StandStoryEngineTest {
    private val project = StandProject(StandChannel.PIA, "series")
    private val ownership = StandOwnership.restore(project)
    private val presence = StandPresence("ST-013", "PIEROS", true)
    private fun episode(number: Int = 1, mode: StandWritingMode = StandWritingMode.KOTLIN) =
        StandEpisode(project, number, StandStoryType.ORIGIN, mode,
            listOf(StandStoryScene(1, listOf(presence))))

    @Test fun bothModesUseSameValidation() {
        StandWritingMode.values().forEach { assertTrue(StandStoryEngine.validate(ownership, episode(mode = it))) }
    }
    @Test fun allTenStoryTypesSupported() {
        assertEquals(10, StandStoryType.values().size)
        StandStoryType.values().forEach {
            assertTrue(StandStoryEngine.validate(ownership, episode().copy(type = it)))
        }
    }
    @Test fun consecutiveEpisodesAccepted() {
        assertTrue(StandStoryEngine.validate(ownership, episode(2), episode()))
    }
    @Test fun skippedEpisodeRejected() {
        assertFalse(StandStoryEngine.validate(ownership, episode(3), episode()))
    }
    @Test fun missingPreviousEpisodeRejected() {
        assertFalse(StandStoryEngine.validate(ownership, episode(2)))
    }
    @Test fun differentProjectRejected() {
        assertFalse(StandStoryEngine.validate(ownership,
            episode().copy(project = StandProject(StandChannel.PIA, "other"))))
    }
    @Test fun nextSceneRetainsModeAndSequence() {
        val first = episode(mode = StandWritingMode.AI_KOTLIN)
        val next = StandStoryEngine.nextScene(ownership, first, listOf(presence.dismiss()))
        assertEquals(2, next.scenes.last().number)
        assertEquals(first.mode, next.mode)
        assertEquals(1, first.scenes.size)
    }
    @Test(expected = IllegalArgumentException::class)
    fun wrongOwnerRejectedWhenAppending() {
        StandStoryEngine.nextScene(ownership, episode(), listOf(presence.copy(ownerId = "other")))
    }
    @Test fun ownershipContextIncludesPieros() {
        assertEquals("ST-013 => PIEROS", StandStoryEngine.ownershipContext(ownership))
    }
}
