package com.pearparinya.automovie

import org.junit.Assert.assertFalse
import org.junit.Test

/** Regression guards: never attach stale or unconfirmed product facts to a new video. */
class ProductConfirmationTest {
    @Test fun emptyConfirmationIsNeverReusable() {
        assertFalse(ProductAssist.validConfirmation("", "", """{"name":"A","facts":"B"}"""))
        assertFalse(ProductAssist.validConfirmation(" ", " ", """{"name":"A","facts":"B"}"""))
    }

    @Test fun differentProductCannotBeAttachedToReview() {
        assertFalse(ProductAssist.validConfirmation("สินค้า B", "สินค้า A", """{"name":"A","facts":"B"}"""))
    }

    @Test fun missingProductPayloadCannotBeReused() {
        assertFalse(ProductAssist.validConfirmation("สินค้า A", "สินค้า A", null))
        assertFalse(ProductAssist.validConfirmation("สินค้า A", "สินค้า A", ""))
    }
}
