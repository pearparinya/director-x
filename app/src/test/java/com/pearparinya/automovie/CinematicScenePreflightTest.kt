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
}
