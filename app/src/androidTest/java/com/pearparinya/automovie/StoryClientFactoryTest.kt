package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryClientFactoryTest {
    @Test fun activeProvidersAndLegacyIdsAreHandledSafely() {
        assertTrue(StoryClientFactory.create(StoryProvider.GROQ,"fake-key",StoryProvider.GROQ.defaultModel,{false}) is GroqClient)
        assertTrue(StoryClientFactory.create(StoryProvider.GEMINI,"fake-key",StoryProvider.GEMINI.defaultModel,{false}) is GeminiClient)
        for (provider in listOf(StoryProvider.OPENROUTER,StoryProvider.COHERE)) {
            assertTrue(StoryClientFactory.create(provider,"fake-key",provider.defaultModel,{false}) is CompatibleStoryClient)
        }
        for (provider in listOf(StoryProvider.DEEPSEEK,StoryProvider.MISTRAL,StoryProvider.SAMBANOVA,StoryProvider.CEREBRAS)) {
            assertEquals(provider,StoryProvider.from(provider.id))
            assertFalse(provider in StoryProvider.activeProviders)
            assertTrue(runCatching { StoryClientFactory.create(provider,"fake-key",provider.defaultModel,{false}) }.isFailure)
            val direct=CompatibleStoryClient(provider.extra()!!.service,"fake-key",provider.defaultModel,{false},automaticRetry=false)
            assertTrue(runCatching { direct.generate("test",JSONObject()) }.isFailure)
        }
        assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI),StoryProvider.hybridProviders)
    }
}
