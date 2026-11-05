package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibleStoryClientTest {
    @Test fun onlyFreeModelsAllowedForOpenRouter() {
        val schema=JSONObject().put("type","object")
        val req=CompatibleStoryClient.buildRequest(CompatibleService.OPENROUTER,"openrouter/free","JSON please",schema)
        assertEquals("openrouter/free",req.getString("model"))
        assertTrue(runCatching { CompatibleStoryClient.buildRequest(CompatibleService.OPENROUTER,"paid/model","test",schema) }.isFailure)
        assertFalse(req.toString().contains("fake-api-key"))
    }
    @Test fun rejectsTruncatedOrNonJsonResponse() {
        fun response(reason: String, content: String)=JSONObject().put("choices",JSONArray().put(JSONObject().put("finish_reason",reason).put("message",JSONObject().put("content",content))))
        assertTrue(CompatibleStoryClient.parse(response("stop","{\"ok\":true}"),CompatibleService.CEREBRAS).getBoolean("ok"))
        assertTrue(runCatching { CompatibleStoryClient.parse(response("length","{\"ok\":true}"),CompatibleService.CEREBRAS) }.exceptionOrNull() is InvalidStoryResponse)
        assertTrue(runCatching { CompatibleStoryClient.parse(response("stop","invalid"),CompatibleService.DEEPSEEK) }.exceptionOrNull() is InvalidStoryResponse)
        assertFalse(runCatching { CompatibleStoryClient.parse(response("content_filter",""),CompatibleService.DEEPSEEK) }.exceptionOrNull() is InvalidStoryResponse)
        val refusal=response("length","").apply { getJSONArray("choices").getJSONObject(0).getJSONObject("message").put("refusal","not allowed") }
        assertFalse(runCatching { CompatibleStoryClient.parse(refusal,CompatibleService.OPENROUTER) }.exceptionOrNull() is InvalidStoryResponse)
    }
}
