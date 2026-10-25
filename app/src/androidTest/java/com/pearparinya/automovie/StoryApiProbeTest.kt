package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryApiProbeTest {
    @Test fun probesValidateRealGenerationContractAndNeverExposePrivateErrors() {
        var calls=0
        val client=object: StoryTextClient {
            override fun cancel() {}
            override fun generate(prompt: String,schema: JSONObject): JSONObject {
                calls++; assertTrue(prompt.contains("Connection test only")); assertFalse(prompt.contains("MASTER"))
                assertEquals("boolean",schema.getJSONObject("properties").getJSONObject("ok").getString("type"))
                assertEquals("ok",schema.getJSONArray("required").getString(0))
                assertTrue(GroqClient.request(StoryProvider.GROQ.defaultModel,prompt,schema).getJSONObject("response_format").getJSONObject("json_schema").getBoolean("strict"))
                return JSONObject().put("ok",calls==1)
            }
        }
        StoryApiProbe.check(client)
        assertTrue(runCatching { StoryApiProbe.check(client) }.isFailure)
        assertTrue(StoryApiProbe.error(StoryHttpFailure(429,null,"secret-key")).contains("โควตา"))
        assertFalse(StoryApiProbe.error(IllegalStateException("secret-key")).contains("secret-key"))
    }
}
