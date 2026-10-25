package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class StoryRequestRetryTest {
    @Test fun throttledRequestCountsDownAndContinuesWithoutUserAction() {
        var clock=0L; var calls=0
        val messages=mutableListOf<String>()
        val retry=StoryRequestRetry({clock},{clock+=it})
        val result=retry.execute({false},{messages.add(it)}) {
            calls++
            if(calls==1) throw StoryHttpFailure(429,"90","quota")
            "same pending chunk"
        }
        assertEquals("same pending chunk",result); assertEquals(2,calls)
        assertEquals(90_000L,clock)
        assertTrue(messages.first().contains("90 วินาที"))
        assertTrue(messages.any { it.contains("1 วินาที") })
        assertTrue(messages.last().contains("กำลังทำต่ออัตโนมัติ"))
    }
    @Test fun transportFailuresBackOffAndStopAfterFourRetries() {
        var clock=0L; var calls=0
        val retry=StoryRequestRetry({clock},{clock+=it})
        val error=runCatching { retry.execute({false},{}) { calls++; throw IOException("private server content") } }.exceptionOrNull()
        assertEquals(5,calls); assertEquals((65+130+260+520)*1000L,clock)
        assertTrue(error!!.message!!.contains("ครบ 4 ครั้ง"))
        assertFalse(error.message!!.contains("private"))
    }
    @Test fun temporaryHttpFailureRetriesButBadCredentialsContentAndTlsDoNot() {
        listOf<Exception>(StoryHttpFailure(401,null,"bad key"),StoryHttpFailure(400,null,"bad request"),
            IllegalArgumentException("invalid scene"),IllegalStateException("refused"),javax.net.ssl.SSLHandshakeException("certificate")).forEach { original ->
            var calls=0
            val error=runCatching { StoryRequestRetry({0L},{fail("must not wait")}).execute({false},{}) { calls++; throw original } }.exceptionOrNull()
            assertSame(original,error); assertEquals(1,calls)
        }
        listOf(408,500,503).forEach { code ->
            var clock=0L; var calls=0
            assertEquals("ok",StoryRequestRetry({clock},{clock+=it}).execute({false},{}) {
                if(++calls==1) throw StoryHttpFailure(code,null,"temporary")
                "ok"
            })
            assertEquals(2,calls)
        }
    }
    @Test fun stopDuringCountdownCancelsWithoutSendingAgain() {
        var clock=0L; var calls=0; var stopped=false
        val error=runCatching { StoryRequestRetry({clock},{clock+=it; stopped=true}).execute({stopped},{}) {
            calls++; throw StoryHttpFailure(429,null,"quota")
        } }.exceptionOrNull()
        assertTrue(error is CancellationException); assertEquals(1,calls)
        assertTrue(clock<=250L)
    }
    @Test fun longQuotaWaitStopsInsteadOfIgnoringServerDelay() {
        var calls=0
        val error=runCatching { StoryRequestRetry({0L},{fail("must not wait")}).execute({false},{}) {
            calls++; throw StoryHttpFailure(429,"86400","daily quota")
        } }.exceptionOrNull()
        assertEquals(1,calls); assertTrue(error!!.message!!.contains("15 นาที"))
    }
    @Test fun retryAfterAcceptsSecondsAndHttpDateAndRejectsInvalidHeaders() {
        assertEquals(66L,StoryRequestRetry.retrySeconds("65.2",0))
        assertEquals(90L,StoryRequestRetry.retrySeconds("Thu, 1 Jan 1970 00:01:30 GMT",0))
        assertEquals(0L,StoryRequestRetry.retrySeconds("Thu, 1 Jan 1970 00:01:30 GMT",100_000))
        listOf("bad","-1","NaN","Infinity").forEach { assertNull(StoryRequestRetry.retrySeconds(it,0)) }
    }
}
