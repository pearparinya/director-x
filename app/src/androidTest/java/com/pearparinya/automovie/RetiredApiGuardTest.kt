package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetiredApiGuardTest {
    private val retired=listOf("cerebras","deepseek","sambanova","mistral")
    @Test fun retiredProvidersCannotBeSelectedFromSavedIds() {
        retired.forEach { id ->
            assertTrue(runCatching { StoryProvider.from(id) }.isFailure)
            assertFalse(StoryProvider.activeProviders.any { it.id==id })
        }
    }
    @Test fun retiredApiSettingsAreNotAccessible() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val retiredSettings=listOf(ExtraApiProvider.CEREBRAS,ExtraApiProvider.DEEPSEEK,
            ExtraApiProvider.SAMBANOVA,ExtraApiProvider.MISTRAL)
        retiredSettings.forEach { provider ->
            assertFalse(provider in ExtraApiProvider.activeProviders)
            assertTrue(runCatching { ExtraApiSettings(context,provider) }.isFailure)
        }
    }
    @Test fun retiredEndpointsCannotBuildRequests() {
        val retiredServices=listOf(CompatibleService.CEREBRAS,CompatibleService.DEEPSEEK,
            CompatibleService.SAMBANOVA,CompatibleService.MISTRAL)
        retiredServices.forEach { service ->
            assertTrue(runCatching {
                CompatibleStoryClient.buildRequest(service,service.defaultModel,"test",JSONObject())
            }.isFailure)
        }
    }
}
