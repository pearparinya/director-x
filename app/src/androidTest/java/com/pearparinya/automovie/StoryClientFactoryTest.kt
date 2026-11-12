package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryClientFactoryTest {
    @Test fun routesAllFiveProvidersAndKeepsLegacyHybridRestricted() {
        assertTrue(StoryClientFactory.create(StoryProvider.GROQ,"fake-key",StoryProvider.GROQ.defaultModel,{false}) is GroqClient)
        assertTrue(StoryClientFactory.create(StoryProvider.GEMINI,"fake-key",StoryProvider.GEMINI.defaultModel,{false}) is GeminiClient)
        for(provider in listOf(StoryProvider.DEEPSEEK,StoryProvider.OPENROUTER,StoryProvider.COHERE,StoryProvider.MISTRAL,StoryProvider.SAMBANOVA,StoryProvider.CEREBRAS)) {
            assertTrue(StoryClientFactory.create(provider,"fake-key",provider.defaultModel,{false}) is CompatibleStoryClient)
            assertEquals(provider.id,provider.extra()!!.id)
        }
        assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI),StoryProvider.hybridProviders)
    }
}
