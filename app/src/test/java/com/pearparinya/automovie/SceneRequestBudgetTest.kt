package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class SceneRequestBudgetTest {
    @Test fun retriesAndRepairsShareCheckpointBudget() {
        var clock = 0L
        val budget = SceneRequestBudget({ clock }, 2, 100L)
        assertEquals(500L,budget.allow(500L))
        assertTrue(budget.select(1,6))
        assertEquals(90L,budget.allow(90L))
        clock = 70
        assertFalse(budget.select(1,6))
        assertEquals(30L,budget.allow(90L))
        assertThrows(SceneWorkPaused::class.java) { budget.allow(90L) }
        assertTrue(budget.select(2,6))
        assertEquals(90L,budget.allow(90L))
        clock = 171
        assertThrows(SceneWorkPaused::class.java) { budget.allow(90L) }
    }
}
