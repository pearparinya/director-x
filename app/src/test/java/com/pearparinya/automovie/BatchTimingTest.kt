package com.pearparinya.automovie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchTimingTest {
    @Test fun estimatesOnlyFromWorkCompletedInThisRun() {
        assertNull(BatchTiming.remaining(1000, 6, 7, 10, false))
        assertEquals(1000L, BatchTiming.remaining(1000, 6, 8, 10, false))
        assertNull(BatchTiming.remaining(1000, 6, 8, 10, true))
    }
}
