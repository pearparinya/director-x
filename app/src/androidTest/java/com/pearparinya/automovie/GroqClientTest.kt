package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GroqClientTest {
    @Test fun requestUsesGroqStrictSchemaAndResponsesRequireCompleteJson() {
        val schema=GeminiStoryEngine.batchSchema()
        val req=GroqClient.request(StoryProvider.GROQ.defaultModel,"Thai script",schema)
        assertEquals("openai/gpt-oss-120b",req.getString("model"))
        assertEquals(3072,req.getInt("max_completion_tokens"))
        val format=req.getJSONObject("response_format").getJSONObject("json_schema")
        assertTrue(format.getBoolean("strict"))
        val converted=format.getJSONObject("schema")
        assertFalse(converted.getBoolean("additionalProperties"))
        assertFalse(converted.getJSONObject("properties").getJSONObject("scenes").getJSONObject("items").getBoolean("additionalProperties"))
        assertFalse(schema.has("additionalProperties"))
        fun response(reason: String="stop",content: String="{\"ok\":true}",refusal: String="")=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",reason)
            .put("message",JSONObject().put("content",content).put("refusal",refusal))))
        assertTrue(GroqClient.parseResponse(response()).getBoolean("ok"))
        assertTrue(runCatching { GroqClient.parseResponse(response("length")) }.isFailure)
        assertTrue(runCatching { GroqClient.parseResponse(response(content="bad json")) }.isFailure)
        assertTrue(runCatching { GroqClient.parseResponse(response(refusal="blocked")) }.isFailure)
        assertTrue(runCatching { GroqClient.request("bad-model","",schema) }.isFailure)
        assertTrue(GroqClient.errorFor(429).contains("โควตา"))
        assertTrue(GroqClient.errorFor(401).contains("Groq API Key"))
    }
    @Test fun knownRequestErrorsCanRecoverWithoutLeakingProviderPayloads() {
        fun failure(code: String,status: Int=400)=GroqClient.failure(status,null,JSONObject().put("error",JSONObject().put("code",code)
            .put("message","private-key-and-story").put("failed_generation","private draft")).toString())
        val json=failure("json_validate_failed")
        assertTrue(StoryRequestRetry.canRetry(json)); assertTrue(StoryRequestRetry.canFallback(json))
        val context=failure("context_length_exceeded")
        assertFalse(StoryRequestRetry.canRetry(context)); assertTrue(StoryRequestRetry.canFallback(context))
        listOf(failure("invalid_request_error"),failure("content_policy_violation"),failure("json_validate_failed",401),GroqClient.failure(400,null,"not-json")).forEach { error ->
            assertFalse(StoryRequestRetry.canRetry(error)); assertFalse(StoryRequestRetry.canFallback(error))
            assertFalse(error.message!!.contains("private"))
        }
        assertTrue(StoryRequestRetry.canFallback(failure("request_too_large",413)))
    }
    @Test fun pacingWaitsBetweenCallsAndCanBeCancelledWithoutSleepingInTests() {
        var time=100L; var waits=0; var stop=false
        val pacer=GroqRequestPacer({ time },{ time+=it; waits++ })
        pacer.await({false},{ fail("first request should start immediately") })
        pacer.await({false},{ assertTrue(it in 1..65) })
        assertEquals(65100L,time); assertTrue(waits>0)
        assertTrue(runCatching { pacer.await({stop},{ stop=true }) }.exceptionOrNull() is java.util.concurrent.CancellationException)
    }
    @Test fun providerKeysAndModelsAreEncryptedSeparatelyWithoutChangingOldGeminiKey() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val settings=StoryProvider.entries.associateWith { GeminiSettings(context,it) }
        val files=StoryProvider.entries.associateWith { File(context.noBackupFilesDir,"${it.id}/settings.json") }
        val old=files.mapValues { it.value.takeIf { f -> f.exists() }?.readBytes() }
        try {
            val gemini="gemini-test-only-fake-key-1234567890"; val groq="gsk_test-only-fake-key-123456789012345"
            settings.getValue(StoryProvider.GEMINI).save(gemini,StoryProvider.GEMINI.defaultModel)
            settings.getValue(StoryProvider.GROQ).save(groq,StoryProvider.GROQ.defaultModel)
            assertEquals(gemini,GeminiSettings(context).key())
            assertEquals(groq,settings.getValue(StoryProvider.GROQ).key())
            assertEquals("openai/gpt-oss-120b",settings.getValue(StoryProvider.GROQ).model())
            assertFalse(files.getValue(StoryProvider.GROQ).readText().contains(groq))
            settings.getValue(StoryProvider.GROQ).clear()
            assertFalse(settings.getValue(StoryProvider.GROQ).hasKey()); assertEquals(gemini,GeminiSettings(context).key())
        } finally { files.forEach { (provider,file) -> old[provider]?.let { file.writeBytes(it) } ?: settings.getValue(provider).clear() } }
    }
}
