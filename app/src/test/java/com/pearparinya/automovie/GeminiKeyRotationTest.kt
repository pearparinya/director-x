package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class GeminiKeyRotationTest {
    @Test fun cyclesOnlyAvailableSlotsAndKeepsTheirOrder() {
        val rotation=GeminiKeyRotation()
        val slots=listOf(1,3,5)
        assertEquals(listOf(1,3,5,1,3,5),List(6) { rotation.next(slots) })
    }
    @Test fun emptySlotsFailClearlyAndSingleSlotRemainsStable() {
        val rotation=GeminiKeyRotation()
        assertTrue(runCatching { rotation.next(emptyList<Int>()) }.isFailure)
        assertEquals(listOf(4,4,4),List(3) { rotation.next(listOf(4)) })
    }
}
