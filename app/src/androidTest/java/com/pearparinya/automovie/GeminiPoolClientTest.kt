package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class GeminiPoolClientTest {
    private fun fake(action: () -> JSONObject)=object: StoryTextClient {
        override fun generate(prompt: String,schema: JSONObject)=action()
        override fun cancel() {}
    }
    private val schema=JSONObject()
    @Test fun quotaFailureUsesNextAvailableSlot() {
        val calls=mutableListOf<Int>()
        val one=fake { calls.add(1); throw StoryHttpFailure(429,"65","quota") }
        val two=fake { calls.add(2); JSONObject().put("ok",true) }
        val pool=GeminiPoolClient(listOf(GeminiPoolClient.Slot(1,one){0},GeminiPoolClient.Slot(2,two){0}),{1},{false},{},false)
        assertTrue(pool.generate("",schema).getBoolean("ok"))
        assertEquals(listOf(1,2),calls)
    }
    @Test fun skipsCoolingKeysAndKeepsConfiguredOrderFromRotatedStart() {
        val calls=mutableListOf<Int>()
        val one=fake { calls.add(1); JSONObject() }
        val two=fake { calls.add(2); JSONObject() }
        val pool=GeminiPoolClient(listOf(GeminiPoolClient.Slot(1,one){1000},GeminiPoolClient.Slot(2,two){0}),{1},{false},{})
        pool.generate("",schema)
        assertEquals(listOf(2),calls)
    }
    @Test fun networkErrorsDoNotTryOtherKeysAndCancellationStops() {
        val calls=mutableListOf<Int>()
        val one=fake { calls.add(1); throw java.net.SocketTimeoutException() }
        val two=fake { calls.add(2); JSONObject() }
        val pool=GeminiPoolClient(listOf(GeminiPoolClient.Slot(1,one){0},GeminiPoolClient.Slot(2,two){0}),{1},{false},{})
        assertTrue(runCatching { pool.generate("",schema) }.exceptionOrNull() is java.net.SocketTimeoutException)
        assertEquals(listOf(1),calls)
        pool.cancel()
        assertTrue(runCatching { pool.generate("",schema) }.exceptionOrNull() is CancellationException)
    }
    @Test fun allCoolingReturnsEarliestRetry() {
        val unused=fake { throw AssertionError("should not call") }
        val pool=GeminiPoolClient(listOf(GeminiPoolClient.Slot(1,unused){9000},GeminiPoolClient.Slot(2,unused){3000}),{1},{false},{})
        val failure=runCatching { pool.generate("",schema) }.exceptionOrNull() as StoryHttpFailure
        assertEquals(429,failure.status); assertEquals("3",failure.retryAfter)
    }
}
