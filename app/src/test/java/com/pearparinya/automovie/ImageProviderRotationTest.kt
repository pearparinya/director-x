package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProviderRotationTest {
    private fun provider(id: String, free: Boolean = true, references: Boolean = true,
                         remaining: Int? = 5, retryAt: Long = 0L) =
        ImageProviderRotation.Provider(id, free, references, remaining, retryAt)

    @Test fun rotatesToNextReadyFreeProvider() {
        val choice = ImageProviderRotation.choose(
            listOf(provider("a"), provider("b")), 1000L, false, "a")
        assertEquals(ImageProviderRotation.Decision.Use("b"), choice)
    }

    @Test fun neverSelectsUnconfirmedPaidProvider() {
        val choice = ImageProviderRotation.choose(
            listOf(provider("paid", free = false), provider("free")), 1000L, false)
        assertEquals(ImageProviderRotation.Decision.Use("free"), choice)
    }

    @Test fun waitsForEarliestProviderCooldown() {
        val choice = ImageProviderRotation.choose(
            listOf(provider("a", retryAt = 5000L), provider("b", retryAt = 3000L)),
            1000L, false)
        assertEquals(ImageProviderRotation.Decision.WaitUntil(3000L), choice)
    }

    @Test fun requiresReferenceSupportWhenMasterIsNeeded() {
        val choice = ImageProviderRotation.choose(
            listOf(provider("text-only", references = false), provider("reference")),
            1000L, true)
        assertEquals(ImageProviderRotation.Decision.Use("reference"), choice)
    }

    @Test fun zeroRemainingFreeRequestsAreNotUsed() {
        val choice = ImageProviderRotation.choose(
            listOf(provider("exhausted", remaining = 0)), 1000L, false)
        assertEquals(ImageProviderRotation.Decision.NeedsFreeProvider, choice)
    }
}
