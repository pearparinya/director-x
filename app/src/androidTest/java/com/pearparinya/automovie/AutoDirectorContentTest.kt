package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class AutoDirectorContentTest {
    @Test fun imageAndVideoCommandsUseReferenceIdsWithoutAssumingFileType() {
        val project=story()
        val poster=SimpleProductionCommands.imageTasks(project,1).first().command
        assertTrue(poster.contains("MASTER ID: GAWIN_MASTER"))
        assertFalse(poster.contains("GAWIN_MASTER.png"))
        val video=SimpleProductionCommands.videoTasks(project,1).first().command
        assertTrue(video.contains("รหัส EP01_SC001"))
        assertFalse(video.contains("EP01_SC001.png"))
    }
    @Test fun legacyChannelIsUpdatedForOldProjectCoversAndCustomNamesRemain() {
        val old="สตอรี่ทรงโต - ซีรีส์ลับฉบับผู้ใหญ่"
        val project=story().put("channel",old)
        val cover=SimpleProductionCommands.imageTasks(project,1).first().command
        assertTrue(cover.contains(ChannelCatalog.channel))
        assertFalse(cover.contains(old))
        assertTrue(CoverTypography.prompt(old,"ชื่อเรื่อง",tagline="คำโปรย").contains(ChannelCatalog.channel))
        assertEquals(ChannelCatalog.channel,ChannelCatalog.updateLegacyChannel(project).getString("channel"))
        assertEquals("ช่องของฉัน",ChannelCatalog.currentChannel("ช่องของฉัน"))
    }
    private fun story(): JSONObject {
        val scenes=JSONArray()
        repeat(24) { i ->
            val cast=JSONArray()
            FlowProductionPack.names.entries.forEachIndexed { n, (id,name) -> cast.put(JSONObject().put("name",name)
                .put("master","${id.uppercase()}_MASTER.png").put("visual_description","adult $name with distinct reference hair $n")
                .put("outfit","Top: white shirt; Bottom: black trousers; Shoes: black shoes; Accessories: none")
                .put("screen_position",listOf("left","center","right")[n])) }
            scenes.put(JSONObject().put("scene",i+1).put("duration_seconds",8).put("characters",cast)
                .put("story_day","DAY01").put("location","ห้องสีขาว").put("action","กวินยื่นซองให้รินรดา").put("start_state","นั่ง").put("end_state","มองซอง")
                .put("image_prompt","Kawin and Rinrada may look at May; MIND_MASTER.png, bright room")
                .put("flow_prompt","STALE AUDIO THAT MUST NOT BE EXPORTED")
                .put("speaker",if(i%3==0) "รินรดา" else "").put("dialogue",if(i%3==0) "กวิน บอกว่า A & B < C ใช่ไหม\"" else "")
                .put("narration",if(i%3==1) "คืนนั้น & ความจริง" else ""))
        }
        return JSONObject().put("content_profile",AutoDirectorContent.profile).put("character_mode","original")
            .put("channel","ช่องทดสอบ").put("category","Twisted Truth")
            .put("title","ความจริงหลังซอง").put("tagline","ใครกันที่ปิดบัง").put("aspect_ratio","9:16").put("duration_seconds",8)
            .put("episodes",JSONArray().put(JSONObject().put("ep",1).put("title","ซองลับ").put("tagline","ใครส่งมา").put("synopsis","ทั้งสามต้องเผชิญความจริง").put("scenes",scenes)))
    }
    @Test fun selectedShortClipHasMatchingPosterSceneDialogueAndXmlCounts() {
        val p=ClipPlan.apply(story().put("channel",ChannelCatalog.channel).put("category",ChannelCatalog.category(ChannelCatalog.topics[24])),"short",30)
        val ep=p.getJSONArray("episodes").getJSONObject(0)
        val original=ep.getJSONArray("scenes")
        ep.put("scenes",JSONArray().apply { repeat(4) { put(original.getJSONObject(it)) } })
        val images=AutoDirectorContent.images(p,1)
        assertTrue(images.contains("8 คำสั่ง"))
        assertTrue(images.contains(ChannelCatalog.comedy))
        assertTrue(images.contains("playful sitcom ensemble poses"))
        assertFalse(images.contains("dynamic dramatic editorial poses"))
        assertEquals(8,AutoDirectorContent.imageTasks(p,1).size)
        assertEquals(4,Regex("\\[ฉากที่ [0-9]+]").findAll(images).count())
        assertTrue(images.contains(ChannelCatalog.channel))
        assertEquals(4,Regex("\\[ฉากที่ [0-9]+]").findAll(AutoDirectorContent.dialogue(p,1)).count())
        val xml=AutoDirectorContent.flowXml(p,1)
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(4,doc.getElementsByTagName("scene").length)
        assertTrue(doc.getElementsByTagName("motion_prompt").item(0).textContent.contains(ChannelCatalog.comedy))
    }
    @Test fun commandCardsGroupBothPosterSizesThenPortraitScenesWithSoundInVideo() {
        val p=story().put("audio_version",1)
        val scenes=p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        for(i in 0 until scenes.length()) scenes.getJSONObject(i).put("sound_design",JSONObject().put("ambience","quiet kitchen room tone").put("sfx","soft paper rustle; brief non-vocal pluck at the written payoff").put("music","none"))
        val images=SceneCommandCards.images(p,1)
        assertEquals(28,images.size)
        assertEquals(listOf("ปกรวม • แนวตั้ง 9:16","ปกรวม • แนวนอน 16:9","ปก EP.1 • แนวตั้ง 9:16","ปก EP.1 • แนวนอน 16:9"),images.take(4).map { it.title })
        assertEquals("ซีน 1 • ภาพแนวตั้ง 9:16",images[4].title)
        assertEquals("ซีน 24 • ภาพแนวตั้ง 9:16",images.last().title)
        assertTrue(images[1].command.contains("1920 x 1080")); assertFalse(images[1].command.contains("1080 x 1920"))
        val videos=SceneCommandCards.videos(p,1)
        assertEquals(24,videos.size)
        assertTrue(videos.first().title.startsWith("ซีน 1 • วิดีโอแนวตั้ง 9:16"))
        val command=videos.first().command
        assertTrue(command.contains("9:16")); assertTrue(command.contains("1080 x 1920")); assertFalse(command.contains("16:9"))
        assertTrue(command.contains("SOUND:")); assertTrue(command.contains("soft paper rustle")); assertTrue(command.contains("below speech"))
        assertEquals(1,Regex(Regex.escape(scenes.getJSONObject(0).getString("dialogue"))).findAll(command).count())
        val xml=AutoDirectorContent.flowXml(p,1)
        assertTrue(xml.contains("<sound_design>")); assertTrue(xml.contains("soft paper rustle"))
        assertTrue(SceneCommandCards.join(videos).contains("=== ซีน 24"))
        SoundDesign.validate(scenes.getJSONObject(0))
        scenes.getJSONObject(0).getJSONObject("sound_design").remove("sfx")
        assertTrue(runCatching { SoundDesign.validate(scenes.getJSONObject(0)) }.isFailure)
    }
    @Test fun compactExportHasTwoFilesPerEpisodeClearIdsAndSingleOverallCoverPair() {
        for(auto in listOf(true,false)) {
            val p=story()
            if(!auto) p.remove("content_profile")
            val eps=p.getJSONArray("episodes")
            eps.put(JSONObject(eps.getJSONObject(0).toString()).put("ep",2))
            assertTrue(ProductionCommandCheck.issues(p).toString(),ProductionCommandCheck.issues(p).isEmpty())
            val before=p.toString()
            val bytes=ByteArrayOutputStream(); SimpleProductionCommands.write(bytes,p)
            val entries=linkedMapOf<String,String>()
            ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip -> while(true) { val entry=zip.nextEntry ?: break; entries[entry.name]=String(zip.readBytes(),Charsets.UTF_8) } }
            assertEquals(setOf("EP01_IMAGES.txt","EP01_FLOW.txt","EP02_IMAGES.txt","EP02_FLOW.txt"),entries.keys)
            assertEquals(28,SimpleProductionCommands.imageTasks(p,1).size)
            assertEquals(26,SimpleProductionCommands.imageTasks(p,2).size)
            assertFalse(entries.getValue("EP02_IMAGES.txt").contains("STORY_COVER"))
            for(ep in 1..2) {
                val images=entries.getValue(SimpleProductionCommands.imageName(ep))
                val videos=entries.getValue(SimpleProductionCommands.flowName(ep))
                assertEquals(24,SimpleProductionCommands.videoTasks(p,ep).size)
                for(n in 1..24) {
                    val id="EP%02d_SC%03d".format(ep,n)
                    assertTrue(images.contains("ชื่อไฟล์ภาพ: $id.png"))
                    assertTrue(videos.contains("ภาพเริ่มต้นที่ต้องแนบ: รหัส $id"))
                }
                assertFalse(images.contains("=== EP")); assertTrue(images.contains("สารบัญ"))
                assertTrue(images.contains("แก้ภาพ <รหัส>")); assertTrue(images.contains("PENDING"))
                assertTrue(videos.contains("SOUND:")); assertTrue(videos.contains("กวิน บอกว่า A & B < C ใช่ไหม"))
                assertFalse(videos.contains("STALE AUDIO"))
                assertTrue(images.length<110000)
            }
            val cover=SimpleProductionCommands.imageTasks(p,1).first().command
            assertTrue(cover.contains("x=15–85%, y=52–75%")); assertTrue(cover.contains("25% bottom"))
            assertTrue(cover.contains("position: left")); assertTrue(cover.contains("position: center")); assertTrue(cover.contains("position: right"))
            assertEquals(before,p.toString())
        }
        val one=story(); val tasks=SimpleProductionCommands.imageTasks(one,1)
        assertEquals(26,tasks.size); assertEquals(2,tasks.count { it.id.contains("COVER") })
        val first=tasks.first(); one.put("production_note_${first.id}","Keep the envelope visible")
        assertTrue(SimpleProductionCommands.imageTasks(one,1).first().command.contains("Keep the envelope visible"))
        assertFalse(SimpleProductionCommands.imageTasks(one,1)[1].command.contains("Keep the envelope visible"))
    }
    @Test fun productionChecksReportSceneCastAudioPositionAndCrossEpisodeWardrobeConflicts() {
        val p=story(); val ep=p.getJSONArray("episodes").getJSONObject(0); val scene=ep.getJSONArray("scenes").getJSONObject(0)
        val people=scene.getJSONArray("characters")
        people.getJSONObject(1).put("screen_position","left")
        scene.put("flow_action","Kawin laughs heartily")
        var problems=ProductionCommandCheck.issues(p)
        assertTrue(problems.any { it.contains("EP01_SC001") && it.contains("ตำแหน่ง") })
        assertTrue(problems.any { it.contains("EP01_SC001") && it.contains("หัวเราะ") })
        scene.put("characters",JSONArray().put(people.getJSONObject(0))).put("flow_action","Kawin steps between them")
        assertTrue(ProductionCommandCheck.issues(p).any { it.contains("ครบ 3 คน") })
        scene.put("flow_action","Kawin hands the envelope to May")
        assertTrue(ProductionCommandCheck.issues(p).any { it.contains("เมย์") && it.contains("ไม่มีในรายการ") })
        scene.put("flow_action","Kawin may pause before speaking")
        assertFalse(ProductionCommandCheck.namedIssues(scene,FlowCast.definitions(p)).any { it.contains("เมย์") })
        val other=JSONObject(ep.toString()).put("ep",2)
        other.getJSONArray("scenes").getJSONObject(0).getJSONArray("characters").getJSONObject(0).put("outfit","Top: blue shirt; Bottom: black trousers; Shoes: black shoes; Accessories: none")
        p.getJSONArray("episodes").put(other)
        assertTrue(ProductionCommandCheck.issues(p).any { it.contains("EP2 SC1") && it.contains("ไม่ต่อเนื่อง") })
        val bytes=ByteArrayOutputStream()
        assertTrue(runCatching { SimpleProductionCommands.write(bytes,p) }.isFailure)
        assertEquals(0,bytes.size())
    }
    @Test fun imageAndCoverCommandsBindAliasesAndKeepEveryScene() {
        val p=story(); val images=AutoDirectorContent.images(p,1); val covers=AutoDirectorContent.covers(p)
        assertEquals(24,Regex("\\[ฉากที่ [0-9]+]").findAll(images).count())
        assertTrue(images.contains("The Man and Woman 1 may look at Woman 2"))
        listOf("กวิน","รินรดา","เมย์","MASTER.png").forEach { assertFalse(images.contains(it)) }
        assertTrue(images.contains("[ฉากที่ 1] [แนบรูป: The Man, Woman 1, Woman 2]"))
        assertTrue(images.contains("Top: white shirt; Bottom: black trousers; Shoes: black shoes; Accessories: none"))
        assertTrue(images.contains("mouth closed, lips sealed"))
        assertTrue(images.contains("16:9")); assertFalse(AutoDirectorContent.sceneImage(p,p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)).contains("16:9"))
        assertTrue(images.contains("Generate a 9:16 vertical image, 1080 x 1920 pixels."))
        assertTrue(images.contains("[ฉากที่ 24]"))
        assertTrue(covers.contains("16:9")); assertTrue(covers.contains("[หน้าปกรวม 9:16]"))
        assertTrue(covers.contains("lower-middle safe zone")); assertFalse(covers.contains("EP.0"))
        assertEquals(24,Regex("\\[ฉากที่ [0-9]+]").findAll(AutoDirectorContent.dialogue(p,1)).count())
        AutoDirectorContent.validateWardrobes(p.getJSONArray("episodes").getJSONObject(0))
        p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0).getJSONArray("characters").getJSONObject(0).put("outfit","white shirt")
        assertTrue(runCatching { AutoDirectorContent.validateWardrobes(p.getJSONArray("episodes").getJSONObject(0)) }.isFailure)
    }
    @Test fun everyCoverPathIncludesExactTextWhileScenesStayTextFree() {
        val p=story()
        val storyText="BEGIN EXACT COVER TEXT\n${p.getString("channel")}\nความจริงหลังซอง\nใครกันที่ปิดบัง\nEND EXACT COVER TEXT"
        val epText="BEGIN EXACT COVER TEXT\n${p.getString("channel")}\nความจริงหลังซอง\nตอนที่ 1 ซองลับ\nEND EXACT COVER TEXT"
        assertTrue(AutoDirectorContent.covers(p).contains(storyText))
        assertTrue(AutoDirectorContent.images(p,1).contains(epText))
        val tasks=AutoDirectorContent.imageTasks(p,1)
        assertTrue(tasks.first().prompt.contains(storyText)); assertTrue(tasks[2].prompt.contains(epText))
        assertFalse(tasks[4].prompt.contains("BEGIN EXACT COVER TEXT"))
        val queue=AutoDirectorContent.geminiNext(p,1)
        assertTrue(queue.contains(storyText)); assertTrue(queue.contains(epText)); assertTrue(queue.contains("ไม่รับ next เพื่อข้ามปก"))
        assertTrue(FlowVisualDirection.storyCover("9x16","refs",p.getString("channel"),p.getString("title"),p.getString("tagline"),"cue").contains(storyText))
        assertTrue(FlowVisualDirection.cover("9x16","refs",p.getString("channel"),p.getString("title"),1,"ซองลับ","ไม่แสดงเป็นบรรทัดที่สี่","cue").contains(epText))
    }
    @Test fun v45WritesTwentySixTextCommandsAndStopsWithThaiDialogueAndEnglishMotionNames() {
        val p=story().put("autodirector_version",45)
        val outfit="Top: fitted blouse, shade=deep ruby red #9B111E; sleeves=long-sleeve; Bottom: fitted skirt, shade=ink black #101010; Shoes: heels, shade=jet black #0A0A0A; Accessories: none"
        val scenes=p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        for(i in 0 until scenes.length()) {
            val people=scenes.getJSONObject(i).getJSONArray("characters")
            for(j in 0 until people.length()) people.getJSONObject(j).put("outfit",outfit)
        }
        val code=AutoDirectorContent.images(p,1)
        assertEquals(28,Regex("Generate a (?:9:16|16:9) image exactly as described, RAW photo:").findAll(code).count())
        assertTrue(code.contains("TEXT COMMANDS ONLY")); assertTrue(code.contains("HARD STOP"))
        assertTrue(code.contains(BodyReferencePolicy.preciseIdentity)); assertTrue(code.contains("glowing 3D")); assertTrue(code.contains("dark localized gradient"))
        assertTrue(code.contains("✅ โค้ดคำสั่งภาพเสร็จสิ้น! พิมพ์ 'สร้างบท EP.1'"))
        assertTrue(AutoDirectorContent.outline(p).contains("✅ โครงเรื่องพร้อม พิมพ์ 'สร้างภาพ EP.1'"))
        val speech=AutoDirectorContent.dialogue(p,1)
        assertTrue("Thai speaker must follow the scene speaker",speech.contains("รินรดา:")); assertFalse(speech.contains("Woman 1:"))
        assertTrue(speech.contains("✅ บทเสร็จสิ้น! พิมพ์ 'สร้าง XML EP.1'"))
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(AutoDirectorContent.flowXml(p,1).toByteArray(Charsets.UTF_8)))
        val motion=doc.getElementsByTagName("motion_prompt")
        for(i in 0 until motion.length) listOf("กวิน","รินรดา","เมย์").forEach { assertFalse(motion.item(i).textContent.contains(it)) }
    }
    @Test fun individualActingTravelsIntoImageDialogueAndXmlWithoutChangingSpeech() {
        val p=story().put("performance_version",1)
        val scenes=p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        for(i in 0 until scenes.length()) {
            val scene=scenes.getJSONObject(i); val people=scene.getJSONArray("characters")
            for(j in 0 until people.length()) {
                val person=people.getJSONObject(j)
                val performance=JSONObject(); PerformanceDirection.fields.forEach { performance.put(it,"controlled individual choice $j") }
                performance.put("gaze","meet รินรดา eyes")
                performance.put("voice",if(person.getString("name")==scene.optString("speaker") && scene.optString("dialogue").isNotBlank()) "low measured Thai delivery" else "silent")
                performance.put("thai_direction","เก็บความกลัวไว้ สบตาอีกฝ่ายแล้วค่อยคลายมือ")
                person.put("performance",performance)
            }
            PerformanceDirection.validate(scene)
        }
        val images=AutoDirectorContent.images(p,1)
        assertTrue(images.contains("emotion: controlled individual choice 0")); assertTrue(images.contains("emotion: controlled individual choice 2"))
        assertTrue(images.contains("gaze: meet Woman 1 eyes")); assertFalse(images.contains("meet รินรดา eyes"))
        val script=AutoDirectorContent.dialogue(p,1)
        listOf("กวิน","รินรดา","เมย์").forEach { assertTrue(script.contains("กำกับ $it: เก็บความกลัวไว้")) }
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(AutoDirectorContent.flowXml(p,1).toByteArray(Charsets.UTF_8)))
        assertTrue(doc.getElementsByTagName("motion_prompt").item(0).textContent.contains("INDIVIDUAL PERFORMANCE — Woman 1"))
        assertTrue(doc.getElementsByTagName("motion_prompt").item(0).textContent.contains("gaze: meet Woman 1 eyes"))
        assertEquals(scenes.getJSONObject(0).getString("dialogue"),doc.getElementsByTagName("dialogue").item(0).textContent)
    }
    @Test fun xmlIsParseablePreservesThaiSpeechAndNeverUsesStaleAudio() {
        val p=story(); val text=AutoDirectorContent.flowXml(p,1)
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        assertEquals("flow",doc.documentElement.tagName)
        assertEquals("template_requires_frame_review",doc.documentElement.getAttribute("status"))
        assertEquals(24,doc.getElementsByTagName("scene").length)
        for(i in 0 until 24) assertEquals("9:16",doc.getElementsByTagName("scene").item(i).attributes.getNamedItem("aspect_ratio").nodeValue)
        assertFalse(text.contains("16:9"))
        assertEquals(24,doc.getElementsByTagName("motion_prompt").length)
        assertEquals(24,doc.getElementsByTagName("lighting").length)
        assertEquals(24,doc.getElementsByTagName("typography_safe_zone").length)
        val speech=doc.getElementsByTagName("dialogue").item(0)
        assertEquals("Woman 1",speech.attributes.getNamedItem("speaker").nodeValue)
        assertEquals("กวิน บอกว่า A & B < C ใช่ไหม\"",speech.textContent)
        assertEquals("VO",doc.getElementsByTagName("dialogue").item(1).attributes.getNamedItem("speaker").nodeValue)
        assertEquals("",doc.getElementsByTagName("dialogue").item(2).textContent)
        assertFalse(text.contains("STALE AUDIO")); assertTrue(text.contains("เสร็จสิ้น"))
    }
    @Test fun exportIncludesGeminiDialogueAndXmlWithOriginalReferenceBytes() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val refs=FlowProductionPack.names.keys.associateWith { id -> File(folder,id).apply { writeBytes(byteArrayOf(-1,-40,-1,1)) } }
            val bytes=ByteArrayOutputStream(); FlowProductionPack.write(bytes,story(),refs,JSONObject(),"หมวด","ช่อง")
            val entries=linkedMapOf<String,ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip -> while(true) { val entry=zip.nextEntry ?: break; entries[entry.name]=zip.readBytes() } }
            listOf("01_OUTLINE.txt","02_COVER_STORY_GEMINI.txt","EP01_IMAGES_GEMINI.txt","EP01_DIALOGUE.txt","EP01_FLOW.xml","ALL_GEMINI_NEXT.txt","EP01_GEMINI_NEXT.txt","WARDROBE_LOCKS.txt").forEach { assertTrue(entries.containsKey("DIRECTOR_FLOW/AUTODIRECTOR/$it")) }
            refs.forEach { (id,file) -> assertArrayEquals(file.readBytes(),entries.getValue("DIRECTOR_FLOW/MASTERS/${id.uppercase()}_MASTER.jpg")) }
            assertTrue(String(entries.getValue("DIRECTOR_FLOW/START.txt"),Charsets.UTF_8).contains("AUTODIRECTOR"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun outlineAutomaticallyContinuesAndResumesFromCache() {
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val eps=JSONArray(); repeat(4) { i -> eps.put(JSONObject().put("ep",i+1).put("title","ชื่อตอน").put("tagline","คำโปรย").put("synopsis","เรื่องย่อ").put("cover_incident","ปมเปิด").put("ending","จุดส่งต่อ")) }
            val outline=JSONObject().put("title","ชื่อเรื่อง").put("tagline","คำโปรย").put("episodes",eps)
            val spec=JSONObject().put("content_profile",AutoDirectorContent.profile).put("category","Twisted Truth").put("channel","ช่อง").put("episodes",4)
            var calls=0
            val first=runCatching { GeminiStoryEngine(folder,{ prompt,_ -> calls++; assertTrue(prompt.contains("four labeled parts")); if(prompt.contains("Plan a complete story")) outline else error("reached scene generation") }).run(spec) }
            assertEquals("reached scene generation",first.exceptionOrNull()?.message); assertEquals(2,calls)
            val next=runCatching { GeminiStoryEngine(folder,{ _,_ -> calls++; error("reached scene generation") }).run(spec.put("outline_approved",true)) }
            assertEquals("reached scene generation",next.exceptionOrNull()?.message); assertEquals(3,calls)
        } finally { folder.deleteRecursively() }
    }
    @Test fun nextQueueContainsEachCoverAndSceneOnceAndCanSelectOneEpisode() {
        val p=story(); val eps=p.getJSONArray("episodes")
        val second=JSONObject(eps.getJSONObject(0).toString()).put("ep",2)
        val support=JSONObject().put("name","หมอณัฐ").put("master","SUPPORT_AB12CD34_MASTER.png")
            .put("visual_description","adult fictional doctor with gray hair")
            .put("outfit","Top: white coat; Bottom: trousers; Shoes: loafers; Accessories: none").put("screen_position","far right")
        p.put("supporting_characters",JSONArray().put(support))
        second.getJSONArray("scenes").getJSONObject(23).getJSONArray("characters").put(support)
        eps.put(second)
        val tasks=AutoDirectorContent.imageTasks(p)
        assertEquals(54,tasks.size); assertEquals(tasks.size,tasks.map { it.id }.distinct().size)
        assertEquals(listOf("STORY_COVER_9x16","STORY_COVER_16x9","EP01_COVER_9x16","EP01_COVER_16x9","EP01_SC001"),tasks.take(5).map { it.id })
        assertEquals("EP01_SC024",tasks[27].id); assertEquals("EP02_COVER_9x16",tasks[28].id)
        assertEquals("EP02_SC024",tasks.last().id)
        assertTrue(tasks.filterNot { it.id.contains("COVER") }.all { it.ratio=="9:16" }); assertEquals(6,tasks.count { it.id.contains("COVER") })
        val single=AutoDirectorContent.imageTasks(p,2)
        assertEquals(28,single.size); assertFalse(single.any { it.id.startsWith("EP01_") })
        assertTrue(runCatching { AutoDirectorContent.imageTasks(p,9) }.isFailure)
        val command=AutoDirectorContent.geminiNext(p)
        assertEquals(54,Regex("=== TASK [0-9]+/54 ").findAll(command).count())
        assertTrue(command.contains("Supporting AB12CD34 = ภาพต้นฉบับ SUPPORT_AB12CD34_MASTER"))
        assertTrue(command.contains("retry")); assertTrue(command.contains("PENDING")); assertTrue(command.contains("ไปที่ รหัสภาพ"))
        assertTrue(command.contains("ห้ามรายงาน DONE หรือเพิ่มตัวนับ"))
        assertTrue(command.contains("ครบ 54 ภาพแล้ว")); assertTrue(command.contains("ไม่อ้างว่าจำข้ามแชตได้"))
        assertFalse(command.contains("กรุณาพิมพ์ 'สร้างบท")); assertFalse(command.contains("VIDEO PROMPT"))
        assertTrue(command.trimEnd().endsWith("ถ้าไม่มีภาพอ้างอิงให้ขอภาพก่อน"))
        assertTrue(command.contains("WARDROBE LEDGER")); assertTrue(command.contains("WARDROBE / ACCESSORY CONTINUITY LOCK"))
        val last=second.getJSONArray("scenes").getJSONObject(23).getJSONArray("characters").getJSONObject(0)
        last.put("outfit",last.getString("outfit").replace("white shirt","red shirt"))
        assertTrue(runCatching { AutoDirectorContent.geminiNext(p) }.isFailure)
    }
}
