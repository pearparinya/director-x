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
class GeminiStoryTest {
    private fun outline(): JSONObject = JSONObject().put("title","ซองบนโต๊ะ").put("tagline","ใครเป็นคนส่ง")
        .put("episodes",JSONArray().apply { repeat(3) { n -> put(JSONObject().put("ep",n+1).put("title","ตอน ${n+1}")
            .put("tagline","คำโปรย ${n+1}").put("synopsis","พบซอง").put("cover_incident","ซองบนโต๊ะ").put("ending","คลี่ปม")) } })
    private fun batch(prompt: String): JSONObject {
        val match=Regex("Write exactly scenes ([0-9]+) to ([0-9]+) for EP ([0-9]+)").find(prompt)!!
        val start=match.groupValues[1].toInt(); val ep=match.groupValues[3].toInt()
        return JSONObject().put("scenes",JSONArray().apply { repeat(6) { n ->
            put(JSONObject().put("scene",start+n).put("duration_seconds",8)
                .put("characters",JSONArray().put(JSONObject().put("name","เมย์").put("master","MIND_MASTER.png").put("outfit","เสื้อครีม กางเกงเบจ")
                    .put("visual_description","short auburn bob").put("screen_position","center")))
                .put("story_day","DAY01").put("story_time","บ่าย").put("activity","home").put("wardrobe_event","DAY01_HOME").put("continuity_group","DAY01_HOME")
                .put("location","ห้องนั่งเล่น").put("start_state","ถือซอง").put("end_state","วางซอง").put("action","EP $ep ซีน ${start+n} วางซอง")
                .put("speaker","").put("dialogue","").put("narration","")
                .put("image_prompt","May with short auburn bob wears cream shirt, holds envelope in living room. MIND_MASTER.png. Neutral white light.")
                .put("flow_prompt","8 seconds 9:16 locked camera. May puts envelope on table, no speech. MIND_MASTER.png."))
        } })
    }
    private fun spec()=JSONObject().put("category","แนว").put("channel","ช่อง").put("episodes",3).put("premise","ซองบนโต๊ะ")
    @Test fun quotaFailureKeepsValidatedChunksAndResumeBuildsCompleteStoryWithoutRepeatingCalls() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"gemini-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var calls=0
            val first=GeminiStoryEngine(folder,{ prompt,_ ->
                calls++
                if(calls==4) error(GeminiClient.errorFor(429))
                if(prompt.contains("Plan a complete story")) outline() else batch(prompt)
            })
            assertTrue(runCatching { first.run(spec()) }.isFailure)
            assertTrue(File(folder,"outline.json").exists()); assertTrue(File(folder,"EP01_01.json").exists())
            assertTrue(File(folder,"EP01_07.json").exists()); assertFalse(File(folder,"EP01_13.json").exists())
            var resumed=0
            val story=GeminiStoryEngine(folder,{ prompt,_ -> resumed++; batch(prompt) }).run(spec())
            assertEquals(10,resumed); assertEquals(3,story.getInt("episode_count")); assertEquals(72,story.getInt("scene_count"))
            assertTrue(FlowProductionPack.issues(story).isEmpty())
            assertEquals(24,story.getJSONArray("episodes").getJSONObject(2).getJSONArray("scenes").length())
            assertEquals(192,story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").let { rows -> (0 until rows.length()).sumOf { rows.getJSONObject(it).getInt("duration_seconds") } })
            val cached=GeminiStoryEngine(folder,{ _,_ -> error("must not call API for completed chunks") }).run(spec())
            assertEquals(story.toString(),cached.toString())
        } finally { folder.deleteRecursively() }
    }
    @Test fun truncatedBlockedAndInvalidJsonResponsesAreNeverAcceptedAsStory() {
        fun response(text: String,reason: String="STOP")=JSONObject().put("candidates",JSONArray().put(JSONObject().put("finishReason",reason)
            .put("content",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",text))))))
        assertEquals(1,GeminiClient.parseResponse(response("{\"ok\":1}")).getInt("ok"))
        assertTrue(runCatching { GeminiClient.parseResponse(response("{}","MAX_TOKENS")) }.isFailure)
        assertTrue(runCatching { GeminiClient.parseResponse(response("not json")) }.isFailure)
        assertTrue(runCatching { GeminiClient.parseResponse(JSONObject().put("promptFeedback",JSONObject().put("blockReason","SAFETY"))) }.isFailure)
        assertTrue(GeminiClient.errorFor(429).contains("โควตา")); assertTrue(GeminiClient.errorFor(403).contains("API Key"))
        assertTrue(runCatching { GeminiStoryEngine.validateOutline(outline(),4) }.isFailure)
        assertTrue(runCatching { GeminiStoryEngine.validateBatch(batch("Write exactly scenes 1 to 6 for EP 1").apply { getJSONArray("scenes").getJSONObject(0).put("duration_seconds",10) },1,1) }.isFailure)
    }
    @Test fun mixedAudioIsRepairedOnceAndResumeUsesValidatedChunks() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"gemini-audio-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var bad=false; var repairs=0
            val story=GeminiStoryEngine(folder,{ prompt,_ ->
                if(prompt.contains("Plan a complete story")) outline() else {
                    assertTrue(prompt.contains("AUDIO FIELDS"))
                    batch(prompt).apply {
                        if(prompt.contains("Your previous JSON failed validation")) { repairs++; assertTrue(prompt.contains("บทพูดกับบรรยายต้องแยกซีน")) }
                        else if(!bad) { bad=true; getJSONArray("scenes").getJSONObject(0)
                            .put("speaker","เมย์").put("dialogue","ซองนี้ของใครคะ").put("narration","เธอถามด้วยความสงสัย") }
                    }
                }
            }).run(spec())
            assertEquals(1,repairs); assertTrue(FlowProductionPack.issues(story).isEmpty())
            assertEquals(story.toString(),GeminiStoryEngine(folder,{ _,_ -> error("must use cache") }).run(spec()).toString())
        } finally { folder.deleteRecursively() }
    }
    @Test fun repeatedInvalidAudioStopsWithoutSavingOrUnboundedRetry() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"gemini-invalid-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var batches=0
            val failure=runCatching { GeminiStoryEngine(folder,{ prompt,_ ->
                if(prompt.contains("Plan a complete story")) outline() else {
                    batches++; batch(prompt).apply { getJSONArray("scenes").getJSONObject(0)
                        .put("speaker","เมย์").put("dialogue","ใครส่งมา").put("narration","เธอถาม") }
                }
            }).run(spec()) }
            assertTrue(failure.isFailure); assertEquals(2,batches)
            assertTrue(failure.exceptionOrNull()!!.message!!.contains("หลังแก้ 1 ครั้ง"))
            assertTrue(File(folder,"outline.json").exists()); assertFalse(File(folder,"EP01_01.json").exists())
        } finally { folder.deleteRecursively() }
    }
    @Test fun keyIsEncryptedOutsideBackupsAndCanBeDeleted() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // Preserve any settings on a developer device; CI starts with none.
        val file=File(context.noBackupFilesDir,"gemini/settings.json"); val old=file.takeIf { it.exists() }?.readBytes()
        val settings=GeminiSettings(context); val fake="test-only-not-a-real-key-1234567890"
        try {
            settings.save(fake,GeminiSettings.DEFAULT_MODEL)
            assertTrue(settings.hasKey()); assertEquals(fake,settings.key()); assertEquals(GeminiSettings.DEFAULT_MODEL,settings.model())
            assertFalse(file.readText().contains(fake)); assertFalse(file.canonicalPath.startsWith(context.filesDir.canonicalPath+"/"))
            settings.clear(); assertFalse(settings.hasKey())
        } finally { if(old!=null) { file.parentFile!!.mkdirs(); file.writeBytes(old) } else settings.clear() }
    }
}
