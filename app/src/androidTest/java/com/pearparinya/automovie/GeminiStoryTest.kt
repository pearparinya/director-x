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
        return JSONObject().put("scenes",JSONArray().apply { repeat(match.groupValues[2].toInt()-start+1) { n ->
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
    @Test fun selectedClipDurationsCompleteResumeAndExportWithoutLegacyMinimum() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(10,ChannelCatalog.groups.size)
        assertEquals((1..35).toList(),ChannelCatalog.topics.map { it.id })
        for(format in listOf("short","long")) for(seconds in if(format=="short") ClipPlan.shortSeconds else ClipPlan.longSeconds) {
            val folder=File(context.cacheDir,"selected-clip-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val selected=ClipPlan.apply(spec().put("episodes",1).put("channel",ChannelCatalog.channel)
                    .put("category",ChannelCatalog.category(ChannelCatalog.topics.first())),format,seconds)
                GeminiStoryFiles.write(File(folder,"spec.json"),selected)
                var fail=true
                val generate: (String,JSONObject)->JSONObject = { prompt,_ ->
                    assertTrue(prompt.contains("Exactly ${ClipPlan.scenes(seconds)} scenes per episode"))
                    if(prompt.contains("Plan a complete story")) outline().apply { put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0))) }
                    else { if(fail) { fail=false; error("simulated interruption") }; batch(prompt) }
                }
                assertTrue(runCatching { GeminiStoryEngine(folder,generate).run(selected) }.isFailure)
                val story=GeminiStoryEngine(folder,generate).run(selected)
                assertEquals(ClipPlan.scenes(seconds),story.getInt("scene_count"))
                assertEquals(seconds,story.getInt("target_seconds"))
                assertEquals(1,story.getInt("subcategory_id"))
                assertEquals(99,GeminiStoryEngine.checkpointPercent(folder))
                assertTrue(FlowProductionPack.issues(story).toString(),FlowProductionPack.issues(story).isEmpty())
                assertEquals(story.toString(),GeminiStoryEngine(folder,{ _,_-> error("must reuse completed checkpoints") }).run(selected).toString())
                val rows=story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
                rows.remove(rows.length()-1)
                assertTrue(FlowProductionPack.issues(story).any { it.contains("จำนวนซีนหรือเวลา") })
            } finally { folder.deleteRecursively() }
        }
        assertTrue(runCatching { ClipPlan.count(ClipPlan.apply(JSONObject(),"short",600)) }.isFailure)
        assertEquals(24,ClipPlan.count(JSONObject()))
    }
    @Test fun modernCommandChecksRepairAudibleLaughBeforeCheckpointAndKeepAutomaticWriting() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"command-check-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val selected=ClipPlan.apply(spec().put("episodes",1).put("command_version",1),"short",30)
            var attempts=0
            val story=GeminiStoryEngine(folder,{ prompt,_ ->
                if(prompt.contains("Plan a complete story") && !prompt.contains("Write exactly scenes")) outline().apply { put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0))) }
                else {
                    attempts++
                    batch(prompt).apply {
                        val rows=getJSONArray("scenes")
                        for(i in 0 until rows.length()) {
                            rows.getJSONObject(i).getJSONArray("characters").getJSONObject(0).put("outfit","Top: cream shirt; Bottom: beige trousers; Shoes: brown shoes; Accessories: none")
                            rows.getJSONObject(i).put("flow_action",if(attempts==1 && i==0) "May laughs heartily" else "May places the envelope on the table")
                        }
                    }
                }
            }).run(selected)
            assertEquals(2,attempts)
            assertEquals(1,story.getInt("command_version"))
            assertTrue(ProductionCommandCheck.issues(story).toString(),ProductionCommandCheck.issues(story).isEmpty())
            assertTrue(File(folder,"EP01_01.json").readText().contains("places the envelope"))
            assertFalse(File(folder,"EP01_01.json").readText().contains("laughs heartily"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun comedyCatalogGuidesNewStoriesWithoutChangingExistingCategoryIds() {
        val expected=listOf("Clumsy Secretary","The Scaredy Boss","Pantry Rivalry","The Oblivious Neighbor","Delivery Disaster","Pet Lover's Panic","Gym Fail","Camping Chaos","Drunk Confession","The Clueless Tutor","Cooking Disaster")
        assertEquals(24,ChannelCatalog.topics.count { it.tone=="drama" })
        assertEquals(expected,ChannelCatalog.topics.filter { it.tone=="comedy" }.map { it.title.substringAfter(" ") })
        assertEquals("🧹 Maid's Secret",ChannelCatalog.topics.first().title)
        assertEquals("🎬 Backstage Drama",ChannelCatalog.topics[23].title)
        ChannelCatalog.topics.forEach { topic ->
            val category=ChannelCatalog.category(topic)
            val data=ChannelCatalog.stamp(JSONObject().put("category",category))
            assertEquals(topic.id,data.getInt("subcategory_id"))
            assertEquals(topic.tone,data.getString("story_tone"))
            assertTrue(data.getString("main_category").isNotBlank())
            assertEquals(topic.tone=="comedy",ChannelCatalog.direction(category).contains(ChannelCatalog.comedy))
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"comedy-story-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val selected=ClipPlan.apply(spec().put("episodes",1).put("category",ChannelCatalog.category(ChannelCatalog.topics[24])),"short",30)
            val story=GeminiStoryEngine(folder,{ prompt,_ ->
                assertTrue(prompt.contains(ChannelCatalog.comedy))
                if(prompt.contains("Plan a complete story")) outline().apply { put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0))) } else batch(prompt)
            }).run(selected)
            assertEquals(25,story.getInt("subcategory_id"))
            assertEquals("comedy",story.getString("story_tone"))
            val scene=story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
            assertEquals("comedy",scene.getString("story_tone"))
            assertTrue(FlowReferencePrompt.scene(scene,1,"video").contains(ChannelCatalog.comedy))
            assertTrue(FlowProductionPack.masterCommand(selected.getString("category"),ChannelCatalog.channel).contains(ChannelCatalog.comedy))
            assertTrue(ChannelCatalog.performance(JSONObject().put("category",ChannelCatalog.category(ChannelCatalog.topics.first()))).isBlank())
        } finally { folder.deleteRecursively() }
    }
    @Test fun soundPlansAreRequestedValidatedAndPreservedAcrossResume() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"sound-story-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val selected=ClipPlan.apply(spec().put("episodes",1).put("audio_version",1),"short",30)
            val story=GeminiStoryEngine(folder,{ prompt,schema ->
                assertTrue(prompt.contains(SoundDesign.planning))
                if(prompt.contains("Plan a complete story")) outline().apply { put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0))) }
                else batch(prompt).apply { val rows=getJSONArray("scenes"); for(i in 0 until rows.length()) rows.getJSONObject(i).put("sound_design",JSONObject().put("ambience","soft room tone").put("sfx","paper rustle").put("music","none")) }
            }).run(selected)
            assertEquals(1,story.getInt("audio_version"))
            assertEquals("9:16",story.getString("aspect_ratio"))
            val scene=story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
            assertEquals("paper rustle",scene.getJSONObject("sound_design").getString("sfx"))
            assertEquals(story.toString(),GeminiStoryEngine(folder,{ _,_ -> error("must resume cached audio") }).run(selected).toString())
            scene.remove("sound_design")
            assertTrue(FlowProductionPack.issues(story).any { it.contains("แผนเสียง") })
        } finally { folder.deleteRecursively() }
    }
    @Test fun mainCategoryContractsUseTenReadableStylesWithoutSelectingPresetSubplots() {
        val categories=ChannelCatalog.mainCategories
        assertEquals(12,categories.size)
        assertEquals(12,categories.map { it.id }.distinct().size)
        assertEquals("รักวุ่นชวนเข้าใจผิด",categories[8].name)
        assertEquals("คู่ป่วนต่างอาชีพ",categories[9].name)
        for(c in categories) {
            assertTrue(c.label.contains("(${c.style})"))
            assertTrue(c.menuLabel.contains("\n("))
            assertTrue(ChannelCatalog.acceptedCategories.contains(c.label))
            val data=ChannelCatalog.stamp(JSONObject().put("category",c.label).put("subcategory_id",25).put("subcategory","old"))
            assertEquals(c.id,data.getString("category_id"))
            assertEquals(c.name,data.getString("main_category"))
            assertEquals(c.style,data.getString("category_style"))
            assertEquals(c.tone,data.getString("story_tone"))
            assertFalse(data.has("subcategory")); assertFalse(data.has("subcategory_id"))
            assertTrue(ChannelCatalog.direction(c.label).contains("MAIN CATEGORY ONLY"))
            assertFalse(ChannelCatalog.direction(c.label).contains("Selected premise:"))
            assertEquals(c.tone=="comedy",ChannelCatalog.performance(data).isNotBlank())
            assertTrue(ChannelCatalog.genre(data).contains(c.id))
        }
        assertEquals(35,ChannelCatalog.topics.size)
        ChannelCatalog.topics.forEach { assertTrue(ChannelCatalog.acceptedCategories.contains(ChannelCatalog.category(it))) }
    }
    @Test fun mainCategoryAutomaticallyContinuesPastLegacyApprovalFlagAndKeepsGenre() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for(category in listOf(ChannelCatalog.mainCategories[3],ChannelCatalog.mainCategories[6],ChannelCatalog.mainCategories[8],ChannelCatalog.mainCategories[9])) {
            val folder=File(context.cacheDir,"main-genre-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val selected=ClipPlan.apply(spec().put("channel",ChannelCatalog.channel).put("category",category.label).put("episodes",1).put("outline_review_required",true).put("creative_seed","test-variation"),"short",30)
                var outlines=0; var sceneCalls=0
                val generate: (String,JSONObject)->JSONObject = { prompt,_ ->
                    assertTrue(prompt.contains("MAIN CATEGORY ONLY")); assertTrue(prompt.contains(category.direction))
                    if(prompt.contains("Plan a complete story")) { outlines++; outline().apply { put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0))) } }
                    else { sceneCalls++; batch(prompt) }
                }
                val story=GeminiStoryEngine(folder,generate).run(selected)
                assertEquals(1,outlines); assertEquals(1,sceneCalls)
                assertEquals(category.id,story.getString("category_id"))
                assertEquals(category.style,story.getString("category_style"))
                assertFalse(story.has("subcategory_id"))
                val scene=story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
                assertEquals(category.id,scene.getString("category_id"))
                assertTrue(FlowReferencePrompt.scene(scene,1,"video").contains(category.direction))
                assertTrue(SceneCommandCards.videos(story,1).first().command.contains(category.direction))
                assertTrue(SceneCommandCards.images(story,1).first().command.contains(category.direction))
                assertEquals(story.toString(),GeminiStoryEngine(folder,{ _,_-> error("must use approved checkpoints") }).run(selected).toString())
            } finally { folder.deleteRecursively() }
        }
    }
    @Test fun hybridSharesSamePromptAndCompletesWithFallbackWithoutRewritingCheckpoints() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"hybrid-story-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val providers=mutableListOf<StoryProvider>(); var pending: String?=null
            val router=StoryProviderRouter({false},{_,_->},{0L})
            val hybridSpec=spec().put("hybrid",true)
            val story=GeminiStoryEngine(folder,{ prompt,schema ->
                router.generate(StoryProviderRouter.preferred(schema.getJSONObject("properties").has("scenes"))) { provider ->
                    providers.add(provider)
                    if(provider==StoryProvider.GROQ) { pending=prompt; throw StoryHttpFailure(429,"90","quota") }
                    if(prompt.contains("Plan a complete story")) outline() else {
                        if(pending!=null) { assertEquals(pending,prompt); pending=null }
                        val rows=batch(prompt); assertEquals(2,rows.getJSONArray("scenes").length()); rows
                    }
                }
            }).run(hybridSpec)
            assertEquals(StoryProvider.GEMINI,providers.first()); assertEquals(1,providers.count { it==StoryProvider.GROQ })
            assertEquals(72,story.getInt("scene_count")); assertTrue(FlowProductionPack.issues(story).isEmpty())
            assertTrue(File(folder,"EP01_03.json").isFile)
            val cached=GeminiStoryEngine(folder,{_,_->error("must not regenerate accepted chunks")}).run(hybridSpec)
            assertEquals(story.toString(),cached.toString())
        } finally { folder.deleteRecursively() }
    }
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
    @Test fun failedSceneRepairKeepsOtherScenesAndResumesPendingResponse() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"target-repair-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val selected=ClipPlan.apply(spec().put("episodes",1),"short",30)
            val progress=mutableListOf<String>(); var repairs=0
            assertTrue(runCatching { GeminiStoryEngine(folder,{ prompt,_ ->
                if(prompt.contains("Plan a complete story")) outline().apply { put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0))) }
                else if(prompt.contains("Repair ONLY")) { repairs++; error("offline during repair") }
                else batch(prompt).apply { getJSONArray("scenes").getJSONObject(2).put("speaker","เมย์").put("dialogue","ใครส่งมา").put("narration","เธอถาม") }
            },progress={ _,m -> progress.add(m) }).run(selected) }.isFailure)
            val pending=File(folder,"EP01_01.json.pending.json")
            assertTrue(pending.isFile); assertEquals(1,repairs)
            val before=JSONObject(pending.readText()).getJSONArray("scenes").getJSONObject(0).toString()
            var calls=0
            val story=GeminiStoryEngine(folder,{ prompt,_ -> calls++; assertTrue(prompt.contains("Repair ONLY scene 3")); batch(prompt) }).run(selected)
            assertEquals(1,calls)
            assertEquals(before,JSONObject(File(folder,"EP01_01.json").readText()).getJSONArray("scenes").getJSONObject(0).toString())
            assertFalse(pending.exists()); assertEquals(4,story.getInt("scene_count"))
            assertTrue(progress.any { it.contains("กำลังแก้ซีน 3") })
        } finally { folder.deleteRecursively() }
    }
    @Test fun pendingBatchRetainsItsRangeAfterProviderSwitchAndRepairsOnlyWrongDuration() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"pending-provider-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val selected=ClipPlan.apply(spec().put("episodes",1).put("provider","groq"),"short",30)
            GeminiStoryFiles.write(File(folder,"outline.json"),outline().apply {
                put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0)))
            })
            // Gemini returned four scenes before the user resumed through Groq (two per new request).
            val original=batch("Write exactly scenes 1 to 4 for EP 1")
            original.getJSONArray("scenes").getJSONObject(2).put("duration_seconds",10)
            val pending=File(folder,"EP01_01.json.pending.json")
            GeminiStoryFiles.write(pending,original)
            var calls=0
            val story=GeminiStoryEngine(folder,{ prompt,_ ->
                calls++
                assertTrue(prompt.contains("Repair ONLY scene 3"))
                assertTrue(prompt.contains("Write exactly scenes 3 to 3 for EP 1"))
                batch(prompt)
            }).run(selected)
            assertEquals(1,calls)
            val saved=JSONObject(File(folder,"EP01_01.json").readText()).getJSONArray("scenes")
            for(i in listOf(0,1,3)) assertEquals(original.getJSONArray("scenes").getJSONObject(i).toString(),saved.getJSONObject(i).toString())
            assertEquals(8,saved.getJSONObject(2).getInt("duration_seconds"))
            assertEquals(4,story.getInt("scene_count"))
            assertFalse(pending.exists())
            assertEquals(story.toString(),GeminiStoryEngine(folder,{ _,_ -> error("must reuse repaired checkpoint") }).run(selected).toString())
        } finally { folder.deleteRecursively() }
    }
    @Test fun pendingStoryResumesWhenListenerVoiceWasNotSilentWithoutRegenerating() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"pending-listener-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val selected=ClipPlan.apply(spec().put("episodes",1).put("performance_version",1),"short",30)
            GeminiStoryFiles.write(File(folder,"outline.json"),outline().apply { put("episodes",JSONArray().put(getJSONArray("episodes").getJSONObject(0))) })
            val saved=batch("Write exactly scenes 1 to 4 for EP 1")
            val rows=saved.getJSONArray("scenes")
            for(i in 0 until rows.length()) {
                val acting=JSONObject().apply { PerformanceDirection.fields.forEach { put(it,"controlled choice") }; put("thai_direction","นิ่งฟังและมองอีกฝ่าย"); put("voice","soft whisper") }
                rows.getJSONObject(i).getJSONArray("characters").getJSONObject(0).put("performance",acting)
            }
            val originalAction=rows.getJSONObject(0).getString("action")
            val pending=File(folder,"EP01_01.json.pending.json")
            GeminiStoryFiles.write(pending,saved)
            val story=GeminiStoryEngine(folder,{ _,_ -> error("must resume the pending batch without another AI call") }).run(selected)
            val scenes=story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
            assertEquals(4,scenes.length())
            assertEquals(originalAction,scenes.getJSONObject(0).getString("action"))
            assertEquals("silent",scenes.getJSONObject(0).getJSONArray("characters").getJSONObject(0).getJSONObject("performance").getString("voice"))
            assertFalse(pending.exists())
            assertTrue(File(folder,"EP01_01.json").isFile)
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
    @Test fun shortRepliesCanRepeatButLongRepeatedLinesIdentifyBothScenes() {
        val rows=batch("Write exactly scenes 1 to 6 for EP 1").getJSONArray("scenes")
        for(i in 0 until rows.length()) rows.getJSONObject(i).put("speaker","เมย์").put("dialogue",if(i%2==0) "ค่ะ" else "ทำไม")
        val seen=linkedMapOf<String,String>()
        GeminiStoryEngine.checkDialogue(rows,1,seen)
        GeminiStoryEngine.checkDialogue(rows,2,seen)
        assertTrue(seen.isEmpty())
        val line="ฉันเก็บเอกสารฉบับนี้ไว้เพราะมันเป็นหลักฐานสำคัญของครอบครัวเรา"
        rows.getJSONObject(0).put("dialogue",line)
        GeminiStoryEngine.checkDialogue(rows,1,seen)
        val next=batch("Write exactly scenes 7 to 12 for EP 2").getJSONArray("scenes")
        next.getJSONObject(0).put("dialogue",line+" !")
        val error=runCatching { GeminiStoryEngine.checkDialogue(next,2,seen) }.exceptionOrNull()!!
        assertTrue(error.message!!.contains("EP 2 ซีน 7")); assertTrue(error.message!!.contains("EP 1 ซีน 1"))
    }
    @Test fun repeatedShortRepliesFinishWholeStoryAndRecoverOldZeroProgress() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"gemini-replies-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var calls=0
            val make: (String,JSONObject)->JSONObject = { prompt,_ ->
                calls++
                if(calls==6) error(GeminiClient.errorFor(503))
                if(prompt.contains("Plan a complete story")) outline() else batch(prompt).apply {
                    val rows=getJSONArray("scenes")
                    for(i in 0 until rows.length()) rows.getJSONObject(i).put("speaker","เมย์").put("dialogue","ค่ะ")
                }
            }
            assertTrue(runCatching { GeminiStoryEngine(folder,make).run(spec()) }.isFailure)
            assertEquals(33,GeminiStoryEngine.checkpointPercent(folder))
            GeminiStoryFiles.write(File(folder,"status.json"),JSONObject().put("percent",0))
            assertEquals(33,GeminiStoryEngine.checkpointPercent(folder))
            val story=GeminiStoryEngine(folder,make).run(spec())
            assertEquals(72,story.getInt("scene_count")); assertTrue(FlowProductionPack.issues(story).isEmpty())
            assertEquals(story.toString(),GeminiStoryEngine(folder,{ _,_ -> error("cache only") }).run(spec()).toString())
        } finally { folder.deleteRecursively() }
    }
    @Test fun groqResumesLegacySixSceneCheckpointsUsingTwoSceneRequests() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"groq-resume-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var calls=0
            assertTrue(runCatching { GeminiStoryEngine(folder,{ prompt,_ ->
                calls++; if(calls==4) error("offline")
                if(prompt.contains("Plan a complete story")) outline() else batch(prompt)
            }).run(spec()) }.isFailure)
            val saved=File(folder,"EP01_01.json").readBytes()
            var resumed=0
            val story=GeminiStoryEngine(folder,{ prompt,_ ->
                resumed++; val range=Regex("Write exactly scenes ([0-9]+) to ([0-9]+)").find(prompt)!!
                assertEquals(1,range.groupValues[2].toInt()-range.groupValues[1].toInt())
                batch(prompt)
            }).run(spec().put("provider","groq"))
            assertEquals(30,resumed); assertEquals(72,story.getInt("scene_count"))
            assertArrayEquals(saved,File(folder,"EP01_01.json").readBytes())
            assertTrue(FlowProductionPack.issues(story).isEmpty())
            assertEquals(99,GeminiStoryEngine.checkpointPercent(folder))
            assertEquals(story.toString(),GeminiStoryEngine(folder,{ _,_ -> error("must use cache") }).run(spec().put("provider","groq")).toString())
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
