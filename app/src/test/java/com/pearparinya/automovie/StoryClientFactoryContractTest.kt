package com.pearparinya.automovie

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure JVM regression tests for story API client routing.
 * No network calls, Android Context, or real API keys are needed.
 */
class StoryClientFactoryContractTest {
    @Test fun disabledProvidersAreRejectedBeforeClientConstruction() {
        val disabled = StoryProvider.entries.filterNot { it in StoryProvider.activeProviders }
        assertTrue("Expected at least one inactive provider", disabled.isNotEmpty())
        disabled.forEach { provider ->
            try {
                StoryClientFactory.create(
                    provider = provider,
                    key = "placeholder-not-a-real-key",
                    model = provider.defaultModel,
                    cancelled = { false },
                    context = null
                )
                fail("Inactive provider ${provider.id} was accepted")
            } catch (expected: IllegalArgumentException) {
                assertTrue(
                    "Expected a clear inactive-provider message for ${provider.id}",
                    expected.message.orEmpty().contains("ไม่เปิดใช้งาน")
                )
            }
        }
    }

    @Test fun groqAndGeminiCanConstructClientsWithoutNetworkRequests() {
        listOf(StoryProvider.GROQ, StoryProvider.GEMINI).forEach { provider ->
            val client = StoryClientFactory.create(
                provider = provider,
                key = "placeholder-not-a-real-key",
                model = provider.defaultModel,
                cancelled = { false },
                context = null
            )
            assertTrue("Client must implement StoryTextClient", client is StoryTextClient)
            client.cancel()
        }
    }
}
