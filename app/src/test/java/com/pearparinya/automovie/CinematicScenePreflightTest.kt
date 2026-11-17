package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicScenePreflightTest {
    @Test fun legacyScenesWithoutInventoryAreNotBlocked() {
        assertTrue(CinematicScenePreflight.issues(emptyList()).isEmpty())
    }

    @Test fun missingRequiredFirstFramePropIsRejected() {
        val errors = CinematicScenePreflight.issues(listOf(
            CinematicProp("letter", presentInFirstFrame = false, requiredForAction = true)
        ))
        assertEquals(1, errors.size)
        assertTrue(errors.first().contains("letter"))
    }

    @Test fun swappedSidesAndDuplicateIdsAreRejected() {
        val errors = CinematicScenePreflight.issues(listOf(
            CinematicProp("phone", true, true, "left", "right"),
            CinematicProp("phone", true, false, "right", "right")
        ))
        assertTrue(errors.any { it.contains("ซ้ำ") })
        assertTrue(errors.any { it.contains("ซ้าย–ขวา") })
    }

    @Test fun consistentInventoryPasses() {
        assertTrue(CinematicScenePreflight.issues(listOf(
            CinematicProp("folder", true, true, "center", "center")
        )).isEmpty())
    }
    @Test fun quantityMismatchIsFlaggedWhenExplicitlyProvided() {
        val errors = CinematicScenePreflight.issues(listOf(
            CinematicProp("cups", true, true, expectedCount = 3, firstFrameCount = 2)
        ))
        assertTrue(errors.any { it.contains("จำนวนไม่เพียงพอ") })
    }

    @Test fun optionalQuantityDoesNotChangeLegacyBehavior() {
        assertTrue(CinematicScenePreflight.issues(listOf(
            CinematicProp("cups", true, true)
        )).isEmpty())
    }

    @Test fun conflictingPresenceAndCountIsFlagged() {
        val errors = CinematicScenePreflight.issues(listOf(
            CinematicProp("letter", false, false, firstFrameCount = 1)
        ))
        assertTrue(errors.any { it.contains("ไม่มีในภาพเริ่มต้น") })
    }

    @Test fun explicitInventoryIsIncludedInPromptDirection() {
        val direction = CinematicScenePreflight.promptDirection(listOf(
            CinematicProp("cup", true, true, "left", "left", expectedCount = 2, firstFrameCount = 2)
        ))
        assertTrue(direction.contains("exactly 2 visible at start"))
        assertTrue(direction.contains("screen side left"))
    }

    @Test fun unspecifiedCountsAreNotInvented() {
        val direction = CinematicScenePreflight.promptDirection(listOf(
            CinematicProp("folder", true, true)
        ))
        assertTrue(direction.contains("count not specified"))
    }

    @Test fun multiSpeakerXmlCarriesValidatedInventory() {
        val spec = FlowSceneSpec(
            cast = listOf(
                FlowPerson("A", "adult", "left", "shirt"),
                FlowPerson("B", "adult", "right", "jacket")
            ),
            speaker = "", dialogue = "", narration = "",
            action = "They discuss a cup", location = "room",
            startState = "cup on table", endState = "cup remains on table",
            duration = 8, aspect = "9:16",
            turns = listOf(
                FlowDialogueTurn("A", "สวัสดี"),
                FlowDialogueTurn("B", "สวัสดีครับ")
            ),
            cinematicProps = listOf(
                CinematicProp("cup", true, true, "center", "center", firstFrameCount = 1)
            )
        )
        val output = FlowScenePrompt.build(spec)
        assertTrue(output.contains("Prop cup: exactly 1 visible at start"))
        assertTrue(output.contains("สวัสดีครับ"))
    }

}
