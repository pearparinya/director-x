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
    @Test fun cohereFormatsGroqDraftWhenGeminiIsNotConfigured() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"cohere-missing-gemini-${UUID.randomUUID()}")
        try {
            var writes=0; var formats=0
            val groq=client { _,_ -> writes++; draft() }
            val cohere=client { prompt,_ -> formats++; assertTrue(prompt.contains("สวัสดี")); xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to groq,StoryProvider.COHERE to cohere),{false},{_,_->})
            val result=router.generate("brief",schema())
            assertEquals(1,writes); assertEquals(1,formats)
            assertEquals("สวัสดี",result.getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun cohereReceivesCompletedDraftWhenGeminiFails() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"cohere-handoff-${UUID.randomUUID()}")
        try {
            var writes=0; var formats=0
            val groq=client { _,_ -> writes++; draft() }
            val gemini=client { _,_ -> throw StoryHttpFailure(503,null,"unavailable") }
            val cohere=client { prompt,_ -> formats++; assertTrue(prompt.contains("สวัสดี")); xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to groq,StoryProvider.GEMINI to gemini,StoryProvider.COHERE to cohere),{false},{_,_->})
            val result=router.generate("brief",schema())
            assertEquals(1,writes); assertEquals(1,formats)
            assertEquals("สวัสดี",result.getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
        } finally { folder.deleteRecursively() }
    }
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
    @Test fun quotaRetainsDraftForResume() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-resume-${UUID.randomUUID()}")
        try {
            var writes=0; var ready=false
            val groq=client { _,_ -> writes++; draft() }
            val gemini=client { _,_ -> if(!ready) throw StoryHttpFailure(429,"1","quota") else xml() }
            val calls=mutableListOf<StoryProvider>()
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to groq,StoryProvider.GEMINI to gemini),{false},{p,_->calls.add(p)})
            assertTrue(runCatching { router.generate("brief",schema()) }.isFailure)
            assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI),calls)
            ready=true; calls.clear(); router.generate("brief",schema())
            assertEquals(1,writes); assertEquals(listOf(StoryProvider.GEMINI),calls)
        } finally { folder.deleteRecursively() }
    }
    @Test fun geminiAndDeepSeekWorkWithoutGroqOrCerebras() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-gemini-deepseek-${UUID.randomUUID()}")
        try {
            val calls=mutableListOf<String>()
            val gemini=client { _,_ -> calls.add("gemini"); draft() }
            val deepseek=client { prompt,_ -> calls.add("deepseek"); assertTrue(prompt.contains("สวัสดี")); xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GEMINI to gemini,StoryProvider.DEEPSEEK to deepseek),{false},{_,_->})
            assertEquals("สวัสดี",router.generate("brief",schema()).getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
            assertEquals(listOf("gemini","deepseek"),calls)
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
    @Test fun invalidJsonRetriesOnceAndOversizedScenesSignalSplit() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-json-${UUID.randomUUID()}")
        try {
            var calls=0
            val open=client { _,_ -> calls++; if(calls==1) throw InvalidStoryResponse("invalid") else draft() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.OPENROUTER to open),{false},{_,_->})
            assertEquals("สวัสดี",router.generate("brief",schema()).getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
            assertEquals(2,calls)
            val large=client { _,_ -> throw StoryHttpFailure(413,null,"large") }
            val bad=client { _,_ -> throw InvalidStoryResponse("invalid") }
            val writer=client { _,_ -> draft() }
            val failing=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to large,StoryProvider.GEMINI to writer,StoryProvider.OPENROUTER to bad),{false},{_,_->})
            assertTrue(runCatching { failing.generate("other",schema()) }.exceptionOrNull() is StoryBatchTooLarge)
        } finally { folder.deleteRecursively() }
    }
    @Test fun oversizedWriterSplitsMultiSceneImmediatelyButSingleSceneUsesFallback() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-split-${UUID.randomUUID()}")
        try {
            val calls=mutableListOf<StoryProvider>()
            val large=client { _,_ -> calls.add(StoryProvider.GROQ); throw StoryHttpFailure(413,null,"large") }
            val gemini=client { _,_ -> calls.add(StoryProvider.GEMINI); draft() }
            val deepseek=client { _,_ -> calls.add(StoryProvider.DEEPSEEK); xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to large,StoryProvider.GEMINI to gemini,StoryProvider.DEEPSEEK to deepseek),{false},{_,_->})
            assertTrue(runCatching { router.generate("Write exactly scenes 5 to 6",schema()) }.exceptionOrNull() is StoryBatchTooLarge)
            assertEquals(listOf(StoryProvider.GROQ),calls)
            calls.clear()
            assertEquals("สวัสดี",router.generate("Write exactly scenes 5 to 5",schema()).getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
            assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI,StoryProvider.DEEPSEEK),calls)
        } finally { folder.deleteRecursively() }
    }
    @Test fun oneSceneRetriesTemporaryBackupAfterOversizedPrimary() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"retry-after-413-${UUID.randomUUID()}")
        try {
            val large=client { _,_ -> throw StoryHttpFailure(413,null,"large") }
            val unavailable=client { _,_ -> throw StoryHttpFailure(503,null,"temporary") }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to large,StoryProvider.GEMINI to unavailable,StoryProvider.DEEPSEEK to unavailable),{false},{_,_->})
            val error=runCatching { router.generate("Write exactly scenes 5 to 5",schema()) }.exceptionOrNull()
            assertTrue(error is StoryHttpFailure && error.status==503)
        } finally { folder.deleteRecursively() }
    }
    @Test fun skipsPaymentRequiredAndUsesOpenRouter() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-402-${UUID.randomUUID()}")
        try {
            var deepCalls=0; var openCalls=0
            val groq=client { _,_ -> throw StoryHttpFailure(413,null,"large") }
            val gemini=client { _,_ -> draft() }
            val deep=client { _,_ -> deepCalls++; throw StoryHttpFailure(402,null,"balance") }
            val open=client { _,schema -> openCalls++; if(schema.getJSONObject("properties").has("xml")) xml() else draft() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to groq,StoryProvider.GEMINI to gemini,StoryProvider.DEEPSEEK to deep,StoryProvider.OPENROUTER to open),{false},{_,_->})
            router.generate("Write exactly scenes 5 to 5",schema())
            router.generate("Write exactly scenes 6 to 6",schema())
            assertEquals(1,deepCalls); assertEquals(2,openCalls)
        } finally { folder.deleteRecursively() }
    }
    @Test fun secondRepairStartsWithConfiguredBackupWriter() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"repair-route-${UUID.randomUUID()}")
        try {
            val calls=mutableListOf<String>()
            val groq=client { _,_ -> calls.add("groq"); draft() }
            val gemini=client { _,_ -> calls.add("gemini"); draft() }
            val deep=client { _,_ -> calls.add("deepseek"); xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to groq,StoryProvider.GEMINI to gemini,StoryProvider.DEEPSEEK to deep),{false},{_,_->})
            router.generate("Write exactly scenes 1 to 1\nDIRECTOR_INTERNAL_REPAIR_ATTEMPT=2",schema())
            assertEquals(listOf("gemini","deepseek"),calls)
        } finally { folder.deleteRecursively() }
    }
    @Test fun passesCompletedDraftToOpenRouterFormatterWithoutRewriting() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"handoff-${UUID.randomUUID()}")
        try {
            var writes=0; var formatted=0
            val groq=client { _,_ -> writes++; draft() }
            val gemini=client { _,_ -> throw StoryHttpFailure(503,null,"temporary") }
            val deep=client { _,_ -> throw StoryHttpFailure(402,null,"balance") }
            val open=client { prompt,schema -> formatted++; assertTrue(schema.getJSONObject("properties").has("xml")); assertTrue(prompt.contains("เปิดประตู")); xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to groq,StoryProvider.GEMINI to gemini,StoryProvider.DEEPSEEK to deep,StoryProvider.OPENROUTER to open),{false},{_,_->})
            assertEquals("สวัสดี",router.generate("brief",schema()).getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
            assertEquals(1,writes); assertEquals(1,formatted)
        } finally { folder.deleteRecursively() }
    }
    @Test fun temporaryFormatterErrorIsRetriedAfterPaymentRequired() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-402-retry-${UUID.randomUUID()}")
        try {
            val writer=client { _,_ -> draft() }
            val gemini=client { _,_ -> throw StoryHttpFailure(503,null,"temporary") }
            val deep=client { _,_ -> throw StoryHttpFailure(402,null,"balance") }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to writer,StoryProvider.GEMINI to gemini,StoryProvider.DEEPSEEK to deep),{false},{_,_->})
            val error=runCatching { router.generate("Write exactly scenes 1 to 1",schema()) }.exceptionOrNull()
            assertTrue(error is StoryHttpFailure && error.status==503)
        } finally { folder.deleteRecursively() }
    }
    @Test fun alteredDialogueTriggersBackupInsteadOfSavingIt() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-invalid-${UUID.randomUUID()}")
        try {
            val calls=mutableListOf<StoryProvider>()
            val writer=client { _,_ -> draft() }
            val bad=client { _,schema -> if(schema.getJSONObject("properties").has("xml")) xml("เปลี่ยนบท") else draft() }
            val good=client { _,_ -> xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to writer,StoryProvider.GEMINI to bad,StoryProvider.DEEPSEEK to good),{false},{p,_->calls.add(p)})
            val result=router.generate("brief",schema())
            assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI,StoryProvider.DEEPSEEK),calls)
            assertEquals("สวัสดี",result.getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun quotaUsesBackupAndCleansUnneededDraft() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-quota-${UUID.randomUUID()}")
        try {
            val calls=mutableListOf<StoryProvider>()
            val writer=client { _,_ -> draft() }
            val quota=client { _,schema -> if(schema.getJSONObject("properties").has("xml")) throw StoryHttpFailure(429,"1","quota") else draft() }
            val formatter=client { prompt,_ -> assertTrue(prompt.contains("เปิดประตู")); xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to writer,StoryProvider.GEMINI to quota,StoryProvider.DEEPSEEK to formatter),{false},{p,_->calls.add(p)})
            router.generate("brief",schema())
            assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI,StoryProvider.DEEPSEEK),calls)
            assertTrue(File(folder,"auto_drafts").listFiles().orEmpty().isEmpty())
        } finally { folder.deleteRecursively() }
    }
    @Test fun recordsSanitizedProviderFailureBeforeUsingBackup() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-status-${UUID.randomUUID()}")
        try {
            val failures=mutableListOf<Pair<StoryProvider,String>>()
            val fail=client { _,_ -> throw StoryHttpFailure(429,null,"secret should not appear") }
            val writer=client { _,_ -> draft() }
            val format=client { _,_ -> xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to fail,StoryProvider.GEMINI to writer,StoryProvider.DEEPSEEK to format),{false},{_,_->})
            router.onFailure={ p,c -> failures.add(p to c) }
            router.generate("brief",schema())
            assertEquals(listOf(StoryProvider.GROQ to "HTTP 429"),failures)
        } finally { folder.deleteRecursively() }
    }
    @Test fun formatterFailureNamesFormatterNotWriter() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-formatter-${UUID.randomUUID()}")
        try {
            val failures=mutableListOf<Pair<StoryProvider,String>>()
            val writer=client { _,_ -> draft() }
            val formatter=client { _,_ -> throw StoryHttpFailure(429,null,"private info") }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to writer,StoryProvider.GEMINI to formatter),{false},{_,_->})
            router.onFailure={ p,c -> failures.add(p to c) }
            assertTrue(runCatching { router.generate("brief",schema()) }.isFailure)
            assertEquals(listOf(StoryProvider.GEMINI to "HTTP 429"),failures)
        } finally { folder.deleteRecursively() }
    }
    @Test fun recognizedInvalidSchemaUsesBackupWithoutExposingRequest() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"auto-schema-${UUID.randomUUID()}")
        try {
            val calls=mutableListOf<StoryProvider>()
            val failed=client { _,_ -> throw StoryHttpFailure(400,null,"invalid schema","invalid_schema") }
            val writer=client { _,_ -> draft() }
            val formatter=client { _,_ -> xml() }
            val router=AutomaticStoryRouter(folder,mapOf(StoryProvider.GROQ to failed,StoryProvider.GEMINI to writer,StoryProvider.DEEPSEEK to formatter),{false},{p,_->calls.add(p)})
            val result=router.generate("brief",schema())
            assertEquals(listOf(StoryProvider.GROQ,StoryProvider.GEMINI,StoryProvider.DEEPSEEK),calls)
            assertEquals("สวัสดี",result.getJSONArray("scenes").getJSONObject(0).getString("dialogue"))
        } finally { folder.deleteRecursively() }
    }
}
