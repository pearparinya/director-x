package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AutomaticStoryRouterTest {
    private fun draft()=JSONObject().put("scenes",JSONArray().put(JSONObject().put("scene",1).put("dialogue","สวัสดี").put("action","เปิดประตู")))
    private fun schema()=JSONObject().put("properties",JSONObject().put("scenes",JSONObject()))
    private fun client(f: (String,JSONObject)->JSONObject)=object: StoryTextClient {
        override fun generate(prompt: String,schema: JSONObject)=f(prompt,schema)
        override fun cancel() {}
    }
    private fun xml(line: String="สวัสดี")=JSONObject().put("xml","<flow><scene id=\"1\"><prompt>May opens the door</prompt><dialogue>$line</dialogue></scene></flow>")
    @Test fun pairsRunInOrderAndKeepOriginalDialogue() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-test-${UUID.randomUUID()}")
        try {
            val calls=mutableListOf<String>()
            val a=client { _,_ -> calls.add("groq"); draft() }
            val b=client { prompt,_ -> calls.add("gemini"); assertTrue(prompt.contains("สวัสดี")); xml() }
            val out=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to a,StoryProvider.GEMINI to b),{false},{_,_->}).generate("brief",schema())
            assertEquals(listOf("groq","gemini"),calls)
            assertEquals("สวัสดี",out.getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
            assertEquals("May opens the door",out.getJSONArray("scenes").getJSONObject(0).getString("flow_action"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun quotaFallsBackAndRetainsDraftForResume() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-resume-${UUID.randomUUID()}")
        try {
            var writes=0; var ready=false
            val groq=client { _,_ -> writes++; draft() }
            val gemini=client { _,_ -> if(!ready) throw StoryHttpFailure(429,"1","quota") else xml() }
            val cerebras=client { _,_ -> draft() }
            val deepseek=client { _,_ -> xml() }
            val calls=mutableListOf<StoryProvider>()
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to groq,StoryProvider.GEMINI to gemini,StoryProvider.CEREBRAS to cerebras,StoryProvider.DEEPSEEK to deepseek),{false},{p,_->calls.add(p)})
            router.generate("brief",schema())
            assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI,StoryProvider.CEREBRAS,StoryProvider.DEEPSEEK),calls)
            ready=true; calls.clear(); router.generate("brief",schema())
            assertEquals(1,writes); assertEquals(listOf(StoryProvider.GEMINI),calls)
        } finally { folder.deleteRecursively() }
    }
    @Test fun rejectsChangedSpeechAndExternalEntities() {
        assertTrue(runCatching { SceneXml.apply(draft(),xml("เปลี่ยนบท").getString("xml")) }.isFailure)
        assertTrue(runCatching { SceneXml.apply(draft(),"<!DOCTYPE flow [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><flow/>") }.isFailure)
    }
    @Test fun openRouterIsSinglePassAndStopsOnInvalidOutput() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-free-${UUID.randomUUID()}")
        try {
            var calls=0
            val open=client { _,_ -> calls++; draft() }
            val result=AutomaticStoryRouter(folder,mapOf(StoryProvider.OPENROUTER to open),{false},{_,_->}).generate("brief",schema())
            assertEquals(1,calls)
            assertEquals("สวัสดี",result.getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
            assertTrue(runCatching { AutomaticStoryRouter(folder,emptyMap(),{false},{_,_->}).generate("other",schema()) }.isFailure)
        } finally { folder.deleteRecursively() }
    }
}
