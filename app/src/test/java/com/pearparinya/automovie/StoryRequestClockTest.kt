package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class StoryRequestClockTest {
    @Test fun repeatedProviderStartsANewClockAndResumeKeepsAttemptCount() {
        var now = 1000L
        val status = StoryJobStatus(null, 0, { now }, { now })
        status.beginRequest(StoryProvider.OPENROUTER, 45000L)
        now += 9000
        assertEquals(9000L, status.heartbeat().getLong("request_elapsed_ms"))
        status.endRequest()
        assertFalse(status.heartbeat().has("request_elapsed_ms"))
        status.beginRequest(StoryProvider.OPENROUTER, 45000L)
        val next = status.heartbeat()
        assertEquals(2L, next.getLong("request_number"))
        assertEquals(0L, next.getLong("request_elapsed_ms"))
        val resumed = StoryJobStatus(next, 0, { now }, { now })
        assertFalse(resumed.heartbeat().has("request_elapsed_ms"))
        resumed.beginRequest(StoryProvider.GEMINI, 45000L)
        assertEquals(3L, resumed.heartbeat().getLong("request_number"))
    }
}
