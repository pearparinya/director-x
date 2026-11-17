package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class CinematicPropEditorTest {
    private val cup = CinematicProp("cup", true, true, "left", "left", 1, 1)

    @Test fun addReplaceRemoveRoundTrip() {
        val added = CinematicPropEditor.add(emptyList(), cup)
        assertEquals(listOf(cup), added)
        val renamed = cup.copy(id = "glass", screenSide = "right", expectedScreenSide = "right")
        val replaced = CinematicPropEditor.replace(added, "CUP", renamed)
        assertEquals(listOf(renamed), replaced)
        assertTrue(CinematicPropEditor.remove(replaced, "GLASS").isEmpty())
        assertEquals(listOf(cup), added)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsAreRejected() {
        CinematicPropEditor.add(listOf(cup), cup.copy(id = " CUP "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidReplacementIsRejected() {
        CinematicPropEditor.replace(listOf(cup), "cup", cup.copy(presentInFirstFrame = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun replacingUnknownPropIsRejected() {
        CinematicPropEditor.replace(listOf(cup), "missing", cup)
    }

    @Test(expected = IllegalArgumentException::class)
    fun removingUnknownPropIsRejected() {
        CinematicPropEditor.remove(listOf(cup), "missing")
    }

    @Test(expected = IllegalArgumentException::class)
    fun addingBeyondInventoryLimitIsRejected() {
        val full = (1..50).map { CinematicProp("p$it", true, false) }
        CinematicPropEditor.add(full, CinematicProp("extra", true, false))
    }
}
