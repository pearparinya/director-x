package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class GeminiPacingTest {
    @Test fun cooldownIsLimitedToAffectedProjectKey() {
        GeminiPacing.on429("cooldown-project-A", "120")
        assertTrue(GeminiPacing.delayMillis("cooldown-project-A") >= 100_000L)
        assertTrue(GeminiPacing.delayMillis("other-project-B") < 10_000L)
    }
    @Test fun concurrentRequestsAreSerializedAndWaitingCanBeCancelled() {
        val acquired=CountDownLatch(1)
        val cancel=AtomicBoolean(false)
        val stopped=CountDownLatch(1)
        GeminiPacing.acquire("test-key-1") { false }
        val thread=Thread {
            try { GeminiPacing.acquire("test-key-2") { cancel.get() }; acquired.countDown(); GeminiPacing.release() }
            catch(_: java.util.concurrent.CancellationException) { stopped.countDown() }
        }
        try {
            thread.start()
            assertFalse(acquired.await(500,TimeUnit.MILLISECONDS))
            cancel.set(true)
            assertTrue(stopped.await(2,TimeUnit.SECONDS))
            assertEquals(1L,acquired.count)
        } finally { GeminiPacing.release(); thread.join(2000) }
    }
}
