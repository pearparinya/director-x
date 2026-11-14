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
    @Test fun appearanceLockBindsOnlySelectedMasterAndPreservesCoveredFaces() {
        val lock=SimpleProductionCommands.masterAppearanceLock(listOf("ผู้พิทักษ์" to "KNIGHT_MASTER", "นางฟ้า" to "WOMAN_MASTER"))
        assertTrue(lock.contains("Character: ผู้พิทักษ์ | attached MASTER ID: KNIGHT_MASTER"))
        assertTrue(lock.contains("Character: นางฟ้า | attached MASTER ID: WOMAN_MASTER"))
        assertTrue(lock.contains("keep it closed"))
        assertTrue(lock.contains("overrides generic face-only"))
        assertFalse(lock.contains("VILLAIN_MASTER"))
        assertEquals("",SimpleProductionCommands.masterAppearanceLock(emptyList()))
    }

    @Test fun masterWardrobeOverridesOnlySelectedPersonWithoutConflictingOutfit() {
        val task=SimpleProductionCommands.imageTasks(story(),1).first { it.id=="EP01_SC001" }
        val selected=task.references.first()
        val name=selected.getString("name")
        val id=referenceId(selected.getString("master"))
        val original=selected.getString("outfit")
        val command=SimpleProductionCommands.withWardrobeSources(task.command,task.references,setOf(id))
        assertTrue(command.contains("Character: $name | attached MASTER ID: $id"))
        assertTrue(command.contains("Outfit: Use this character's own attached MASTER outfit"))
        assertEquals(task.references.count { it.getString("outfit")==original }-1,Regex(Regex.escape("Outfit: $original")).findAll(command).count())
        assertFalse(command.contains("Scene Outfit overrides MASTER clothing."))
        assertFalse(command.contains("Use each approved Scene Outfit, not MASTER clothing."))
        if(task.references.size>1) assertTrue(command.contains("Outfit: "+task.references[1].getString("outfit")))
        val faceCommand=SimpleProductionCommands.withWardrobeSources(SimpleProductionCommands.faceOnly(task.command),task.references,setOf(id))
        assertFalse(faceCommand.contains("The scene controls clothes, hairstyle, body, pose and setting; do not copy MASTER clothing."))
        assertFalse(faceCommand.contains("Use each approved Scene Outfit, not MASTER clothing."))
        assertEquals(task.command,SimpleProductionCommands.withWardrobeSources(task.command,task.references,emptySet()))
        assertTrue(runCatching { SimpleProductionCommands.withWardrobeSources(task.command,task.references,setOf("UNKNOWN")) }.isFailure)
    }

    @Test fun faceOnlyModeKeepsSceneAndOutfitWithoutAddingBodyLock() {
        val task=SimpleProductionCommands.imageTasks(story(),1).first { it.id=="EP01_SC001" }
        val changed=SimpleProductionCommands.faceOnly(task.command)
        assertTrue(changed.contains(SimpleProductionCommands.faceOnlyMarker))
        assertTrue(changed.contains("Scene Outfit overrides MASTER clothing."))
        assertTrue(changed.contains("scene controls clothes, hairstyle, body, pose and setting"))
        assertTrue(changed.contains("nonhuman"))
        assertFalse(changed.contains(BodyReferencePolicy.imageIdentityLock))
        assertTrue(task.command.contains(BodyReferencePolicy.imageIdentityLock))
    }
    @Test fun imageCommandsBindEachVisibleMasterToItsOwnSavedCue() {
        val project = story()
        val scene = project.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        val cast = scene.getJSONArray("characters")
        assertTrue(cast.length() >= 2)
        val first = cast.getJSONObject(0).put("visual_description", "unique braid cue")
        val second = cast.getJSONObject(1).put("visual_description", "unique beard cue")
        val tasks = SimpleProductionCommands.imageTasks(project, 1)
        val command = tasks.first { it.id == "EP01_SC001" }.command
        assertTrue(command.contains(first.getString("name") + " | MASTER ID: " + referenceId(first.getString("master")) + " | Identity only."))
        assertTrue(command.contains(second.getString("name") + " | MASTER ID: " + referenceId(second.getString("master")) + " | Identity only."))
        assertTrue(command.contains("If the image-to-name mapping is unclear, ask"))
        assertTrue(command.contains("Scene Outfit overrides MASTER clothing."))
        assertFalse(tasks.first { it.id == "STORY_COVER_9x16" }.command.contains("unique braid cue"))
        second.remove("visual_description")
        assertFalse(SimpleProductionCommands.imageTasks(project, 1).first { it.id == "EP01_SC001" }.command.contains("unique beard cue"))
    }
    @Test fun imageCommandsHaveRealLineBreaksAndOldCategoriesRemainUsable() {
        val project=story()
        val task=SimpleProductionCommands.imageTasks(project,1).first()
        assertTrue(task.command.contains("\nCHARACTERS AND OUTFITS:\n"))
        assertFalse(task.command.contains("\\nCHARACTERS AND OUTFITS:"))
        assertTrue(ChannelCatalog.acceptedCategories.contains("ความลับในรั้วบ้าน"))
        assertTrue(ChannelCatalog.mainCategories.any { it.id=="mystery_twist" })
    }
    @Test fun pairedCoversKeepBothSizesAndReferenceIdentity() {
        val tasks=SimpleProductionCommands.imageTasks(story(),1)
        val portrait=tasks.first { it.id=="STORY_COVER_9x16" }
        val landscape=tasks.first { it.id=="STORY_COVER_16x9" }
        val command=SimpleProductionCommands.coverPair(portrait,landscape)
        assertTrue(command.contains("TWO separate cover images"))
        assertTrue(command.indexOf("IMAGE 1 — PORTRAIT") < command.indexOf("IMAGE 2 — LANDSCAPE"))
        assertTrue(command.contains("STORY_COVER_9x16.png"))
        assertTrue(command.contains("STORY_COVER_16x9.png"))
        assertTrue(command.contains("attached MASTER"))
        assertTrue(geminiImageMessage(command,true).contains("2 ภาพแยกไฟล์"))
        assertTrue(geminiImageMessage(command).contains("1 ภาพ"))
    }
    @Test fun imagePromptsUseProjectDataAndReferenceRolesForBothApps() {
        val project=story().put("title","เรื่องทดสอบ & ความลับ").put("channel","ช่องของฉัน").put("tagline","ข้อความทดสอบ")
        val scene=project.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        val only=scene.getJSONArray("characters").getJSONObject(0)
        scene.put("characters",JSONArray().put(only))
        val tasks=SimpleProductionCommands.imageTasks(project,1)
        for(ratio in listOf("9x16","16x9")) {
            val task=tasks.first { it.id=="STORY_COVER_$ratio" }
            assertTrue(task.command.contains("ช่องของฉัน"))
            assertTrue(task.command.contains("เรื่องทดสอบ & ความลับ"))
            assertTrue(task.command.contains("ข้อความทดสอบ"))
            assertTrue(task.command.contains("REFERENCE ROLE MAPPING"))
            assertTrue(task.command.contains("filenames or upload order alone may not reliably identify"))
            assertTrue(task.command.contains("WARDROBE CONTINUITY"))
            assertTrue(task.command.contains("Show exactly"))
        }
        val solo=tasks.first { it.id=="EP01_SC001" }
        assertEquals(1,solo.references.size)
        assertTrue(solo.command.contains("Show exactly 1 person"))
        assertTrue(solo.command.contains("MASTER ID: "+referenceId(only.getString("master"))))
        assertTrue(SimpleProductionCommands.images(project,1).contains("ChatGPT / Gemini"))
    }
    @Test fun coverCanBindFiveStoryCharactersIncludingNonhuman() {
        val project=story()
        val ep=project.getJSONArray("episodes").getJSONObject(0)
        val scene=ep.getJSONArray("scenes").getJSONObject(0)
        val cast=scene.getJSONArray("characters")
        cast.put(JSONObject().put("name","หมาป่า").put("master","SUPPORT_00000001_MASTER.png").put("outfit","none").put("character_type","animal"))
        cast.put(JSONObject().put("name","อสูร").put("master","SUPPORT_00000002_MASTER.png").put("outfit","armor").put("character_type","creature"))
        val cover=SimpleProductionCommands.imageTasks(project,1).first { it.id=="STORY_COVER_9x16" }
        assertEquals(5,cover.references.size)
        assertTrue(cover.command.contains("Reference 4: หมาป่า"))
        assertTrue(cover.command.contains("Reference 5: อสูร"))
        assertTrue(cover.command.contains("Preserve nonhuman species"))
    }
    @Test fun bothChannelsKeepVisibleCastPositionsAndStoryPropsInSceneCommand() {
        for(channel in listOf("นายเปีย บ้านคลองขุด","ร้อยฉาก")) {
            val project=story().put("channel",channel)
            val scene=project.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
            val people=scene.getJSONArray("characters"); val first=people.getJSONObject(0); val second=people.getJSONObject(1)
            scene.put("characters",JSONArray().put(first).put(second)).put("props","ซองเอกสารอยู่บนโต๊ะ")
            val before=project.toString()
            val task=SimpleProductionCommands.imageTasks(project,1).first { it.id=="EP01_SC001" }
            assertEquals(2,task.references.size)
            for(person in listOf(first,second)) {
                val id=referenceId(person.getString("master"))
                assertTrue(task.command.contains(person.getString("name")+" | MASTER ID: "+id))
                assertTrue(task.command.contains("position: "+person.getString("screen_position")))
            }
            assertTrue(task.command.contains("ซองเอกสารอยู่บนโต๊ะ"))
            assertEquals(before,project.toString())
        }
    }
    @Test fun coverSelectionCanChooseLaterCharacterWithoutChangingStoryOrScenes() {
        val project=story(); val scenes=project.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        val wolf=JSONObject().put("name","หมาป่า").put("master","SUPPORT_00000001_MASTER.png").put("outfit","none").put("character_type","animal")
        scenes.getJSONObject(23).getJSONArray("characters").put(wolf)
        val first=scenes.getJSONObject(0).getJSONArray("characters").getJSONObject(0)
        val selected=setOf(referenceId(first.getString("master")),referenceId(wolf.getString("master")))
        val before=project.toString()
        val tasks=SimpleProductionCommands.imageTasks(project,1,mapOf("STORY" to selected))
        val cover=tasks.first { it.id=="STORY_COVER_9x16" }
        assertEquals(selected,cover.references.map { referenceId(it.getString("master")) }.toSet())
        assertTrue(cover.command.contains("หมาป่า")); assertTrue(cover.command.contains("SUPPORT_00000001_MASTER"))
        assertFalse(tasks.first { it.id=="EP01_SC001" }.references.any { it.optString("name")=="หมาป่า" })
        assertEquals(before,project.toString())
        assertTrue(runCatching { SimpleProductionCommands.imageTasks(project,1,mapOf("STORY" to setOf("MISSING_MASTER"))) }.isFailure)
    }
    @Test fun exportedCoverUsesTheSameSelectionAsTheScreen() {
        val project=story(); val people=SimpleProductionCommands.coverCandidates(project,1,true)
        val selected=people.take(2).map { referenceId(it.getString("master")) }.toSet()
        val selection=mapOf("STORY" to selected); val before=project.toString()
        val bytes=ByteArrayOutputStream(); SimpleProductionCommands.write(bytes,project,selection)
        var images=""
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while(true) { val entry=zip.nextEntry ?: break; if(entry.name=="EP01_IMAGES.txt") images=String(zip.readBytes(),Charsets.UTF_8) }
        }
        val cover=images.substringAfter("รหัส: STORY_COVER_9x16").substringBefore("รหัส: STORY_COVER_16x9")
        val screen=SimpleProductionCommands.imageTasks(project,1,selection).first { it.id=="STORY_COVER_9x16" }
        selected.forEach { assertTrue(cover.contains(it)); assertTrue(screen.command.contains(it)) }
        assertFalse(cover.contains(referenceId(people[2].getString("master"))))
        assertEquals(before,project.toString())
    }
    @Test fun sceneOverviewWarnsOnLongSpeechWithoutChangingIt() {
        val scene=story().getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        val words="คำทดสอบ".repeat(20)
        scene.put("dialogue",words)
        val overview=SceneOverview.detail(scene,"EP01_SC001")
        assertTrue(overview.contains("อาจยาวสำหรับ 8 วินาที"))
        assertEquals(words,scene.getString("dialogue"))
    }
    @Test fun imageCommandsKeepFaceGeometryAndSameDayWardrobe() {
        val tasks=SimpleProductionCommands.imageTasks(story(),1)
        val samples=listOf(tasks.first { it.id=="STORY_COVER_9x16" },tasks.first { it.id=="STORY_COVER_16x9" },tasks.first { it.id=="EP01_SC001" })
        samples.forEach { task ->
            assertTrue(task.command.contains("Do not stretch, elongate, widen, squeeze or warp the face"))
            assertTrue(task.command.contains("Recompose independently for each aspect ratio"))
            assertTrue(task.command.contains("WARDROBE CONTINUITY"))
            assertTrue(task.command.contains("neckline, collar, sleeves, closures"))
            assertTrue(task.command.contains("their own MASTER controls face, hair and body"))
            assertTrue(task.command.contains("three views in each MASTER represent ONE person"))
            assertTrue(task.command.contains("new story day can use the outfit planned for that day"))
        }
    }
    @Test fun sceneImageLocksOnlyVisibleCastEvenWhenOthersAreMentioned() {
        val project=story()
        val scene=project.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        val solo=scene.getJSONArray("characters").getJSONObject(0)
        scene.put("characters",JSONArray().put(solo))
        scene.put("start_state","listens to others off-screen")
        val task=SimpleProductionCommands.imageTasks(project,1).first { it.id=="EP01_SC001" }
        assertEquals(1,task.references.size)
        assertTrue(task.command.contains("Show exactly 1 person in this image: ${solo.getString("name")}"))
        assertTrue(task.command.contains("Anyone else mentioned, addressed, looked at or heard is outside the frame"))
        val multi=FlowReferencePrompt.visibleCastLock(listOf(solo,JSONObject().put("name","อีกคน")))
        assertTrue(multi.contains("Show exactly 2 people"))
    }
    @Test fun videoDocumentPairsEveryCommandWithItsSceneImage() {
        val doc=SimpleProductionCommands.videos(story(),1)
        assertTrue(doc.contains("Video → Frames"))
        assertTrue(doc.contains("Add start frame"))
        assertTrue(doc.contains("ภาพซีนที่ต้องแนบเป็นเฟรมแรก: EP01_SC001"))
        assertTrue(doc.contains("ไฟล์นี้มีคำสั่งข้อความ"))
    }
    @Test fun sceneCameraKeepsCastAndSupportsExplicitModes() {
        val project=story()
        val scene=project.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        fun command()=SimpleProductionCommands.videoTasks(project,1).first().command
        assertTrue(command().contains("Slow, gentle pan"))
        assertTrue(command().contains("Preserve each character&#39;s identity") || command().contains("Preserve each character&apos;s identity"))
        assertTrue(command().contains("<speaker>Woman 1</speaker>"))
        scene.put("camera_movement","pan")
        assertTrue(command().contains("Slow, gentle pan"))
        scene.put("camera_movement","track")
        assertTrue(command().contains("tracking movement"))
        scene.put("camera_movement","locked")
        assertTrue(command().contains("Locked camera"))
        scene.put("camera_movement","push_in")
        assertTrue(command().contains("push-in"))
    }
    @Test fun flowCommandKeepsAssignedSpeechAndFrameWithoutConflictingMurmurs() {
        val project=story()
        val scene=project.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        scene.put("sound_design",JSONObject().put("ambience","subtle indoor murmurs").put("sfx","none").put("music","low strings"))
        val prompt=SimpleProductionCommands.videoTasks(project,1).first().command
        val parsed=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(prompt.toByteArray(Charsets.UTF_8)))
        assertEquals(scene.getString("dialogue"),parsed.getElementsByTagName("text").item(0).textContent)
        assertTrue(prompt.contains("<speaker>Woman 1</speaker>"))
        assertTrue(prompt.contains("quiet room tone"))
        assertFalse(prompt.contains("indoor murmurs"))
        assertFalse(prompt.contains("MASTER ID:"))
        assertFalse(prompt.contains(project.getString("channel")))
        assertFalse(prompt.contains(project.getString("title")))
        assertFalse(prompt.contains("\\n"))
        scene.put("dialogue","").put("speaker","").put("narration","")
        val silent=SimpleProductionCommands.videoTasks(project,1).first().command
        assertFalse(silent.contains("<dialogue>")); assertFalse(silent.contains("<voiceover>"))
        scene.put("narration","คืนนั้นความจริงปรากฏ")
        val narration=SimpleProductionCommands.videoTasks(project,1).first().command
        assertTrue(narration.contains("Off-screen narrator"))
        assertTrue(narration.contains("คืนนั้นความจริงปรากฏ"))
        assertTrue(narration.contains("Other visible characters remain silent"))
        scene.put("narration","").put("speaker","รินรดา").put("dialogue","สวัสดี").put("offscreen_characters",JSONArray().put("รินรดา"))
        scene.put("characters",JSONArray().put(scene.getJSONArray("characters").getJSONObject(0)))
        scene.put("flow_action","กวิน stands quietly")
        val offscreen=SimpleProductionCommands.videoTasks(project,1).first().command
        assertTrue(offscreen.contains("Off-screen speaker"))
        assertTrue(offscreen.contains("Other visible characters remain silent"))
        assertEquals(1,offscreen.split("สวัสดี").size-1)
    }
    @Test fun imageAndVideoCommandsUseReferenceIdsWithoutAssumingFileType() {
        val project=story()
        val poster=SimpleProductionCommands.imageTasks(project,1).first().command
        assertTrue(poster.contains("MASTER ID: GAWIN_MASTER"))
        assertFalse(poster.contains("GAWIN_MASTER.png"))
        val video=SimpleProductionCommands.videoTasks(project,1).first().command
        assertTrue(video.contains("continuous 8-second vertical"))
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
    @Test fun scenePropNotesReachImagePromptAndOverview() {
        val p=story()
        val scene=p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        scene.put("props","กวิน: จอบ")
        val image=SimpleProductionCommands.imageTasks(p,1).first { it.id=="EP01_SC001" }
        assertTrue(image.command.contains("Objects and holders specified in the story: กวิน: จอบ"))
        val overview=SceneOverview.detail(scene,"EP01_SC001")
        assertTrue(overview.contains("สิ่งของและผู้ใช้จากบท: กวิน: จอบ"))
        assertTrue(overview.contains("ตรวจสิ่งของกับภาพเริ่มต้นจริง"))
    }
    @Test fun propSuggestionsRequireEvidenceAndPreserveExistingNotes() {
        val scene=JSONObject().put("action","กวินถือจอบขุดดิน").put("start_state","กวินถือจอบ").put("props","นายเปีย: ถุง")
            .put("characters",JSONArray().put(JSONObject().put("name","กวิน")))
        val valid=JSONObject().put("scene_id","EP01_SC001").put("item","จอบ").put("holder","กวิน").put("details","").put("continuity_id","tool1").put("evidence","ถือจอบ")
        val hallucination=JSONObject(valid.toString()).put("item","ดาบ").put("evidence","ถือดาบ")
        val stranger=JSONObject(valid.toString()).put("holder","บุคคลอื่น")
        val answer=JSONObject().put("items",JSONArray().put(valid).put(hallucination).put(stranger))
        val accepted=PropSuggestionReview.valid(answer,mapOf("EP01_SC001" to scene))
        assertEquals(1,accepted.size)
        PropSuggestionReview.apply(scene,accepted)
        assertTrue(scene.getString("props").contains("นายเปีย: ถุง"))
        assertTrue(scene.getString("props").contains("กวิน: จอบ"))
        assertEquals("tool1",scene.getJSONArray("prop_links").getJSONObject(0).getString("continuity_id"))
        PropSuggestionReview.apply(scene,accepted)
        assertEquals(1,scene.getJSONArray("prop_links").length())
    }
    @Test fun homeReviewStateRequiresActualConfirmationAndWarnsForStaleData() {
        assertEquals("ยังไม่ได้ยืนยัน",HomeReviewState.wardrobe("","abc"))
        assertEquals(HomeReviewState.WARDROBE_CONFIRMED,HomeReviewState.wardrobe("abc","abc"))
        assertEquals("บทเปลี่ยน • ตรวจชุดอีกครั้ง",HomeReviewState.wardrobe("abc","changed"))
        assertTrue(HomeReviewState.shouldWarn("ยังไม่ได้ยืนยัน",0))
        assertTrue(HomeReviewState.shouldWarn(HomeReviewState.WARDROBE_CONFIRMED,1))
        assertFalse(HomeReviewState.shouldWarn(HomeReviewState.WARDROBE_CONFIRMED,0))
    }
    @Test fun menuColorsFollowActionMeaningRatherThanOptionPosition() {
        assertEquals(MenuChoiceStyle.STANDARD,MenuChoiceStyle.role("ดูและยืนยันชุดในบท"))
        assertEquals(MenuChoiceStyle.AI,MenuChoiceStyle.role("ให้ AI เสนอชุดใหม่"))
        assertEquals(MenuChoiceStyle.EDIT,MenuChoiceStyle.role("แก้ชุดเฉพาะฉาก"))
        assertEquals(MenuChoiceStyle.STANDARD,MenuChoiceStyle.role("EP01_SC001"))
        assertEquals(MenuChoiceStyle.AI,MenuChoiceStyle.role("ให้ ai เสนอสิ่งของ"))
    }
    @Test fun wardrobeProgressDistinguishesSavedFromConfirmedAcrossReloads() {
        assertEquals("ยังไม่ได้ยืนยัน",HomeReviewState.wardrobeProgress("","","v1",false))
        assertEquals("บันทึกชุดที่เลือกแล้ว • ยังไม่ยืนยันทั้งหมด",HomeReviewState.wardrobeProgress("old","v1","v1",true))
        assertEquals(HomeReviewState.WARDROBE_CONFIRMED,HomeReviewState.wardrobeProgress("v1","v1","v1",false))
        assertEquals("ข้อมูลชุดเปลี่ยน • ตรวจอีกครั้ง",HomeReviewState.wardrobeProgress("v1","v1","v2",false))
        assertTrue(HomeReviewState.shouldWarn(HomeReviewState.wardrobeProgress("old","v1","v1",true),0))
    }
    @Test fun reviewFingerprintsInvalidateOnlyRelevantFields() {
        val p=JSONObject().put("name","กวิน").put("master","GAWIN_MASTER").put("outfit","shirt").put("screen_position","left")
        val s=JSONObject().put("ep",1).put("scene",1).put("story_day","DAY01").put("action","กวินถือจอบ")
            .put("start_state","ถือจอบ").put("props","จอบ").put("location","สวน").put("characters",JSONArray().put(p))
        val wardrobe=ReviewFingerprints.wardrobe(listOf(s))
        val props=ReviewFingerprints.props(s)
        p.put("screen_position","right")
        assertEquals(wardrobe,ReviewFingerprints.wardrobe(listOf(s)))
        assertEquals(props,ReviewFingerprints.props(s))
        p.put("outfit","coat")
        assertNotEquals(wardrobe,ReviewFingerprints.wardrobe(listOf(s)))
        assertEquals(props,ReviewFingerprints.props(s))
        s.put("props","เสียม")
        assertNotEquals(props,ReviewFingerprints.props(s))
        val changedWardrobe=ReviewFingerprints.wardrobe(listOf(s))
        s.put("action","กวินวางเสียม")
        assertEquals(changedWardrobe,ReviewFingerprints.wardrobe(listOf(s)))
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
                .put("story_day","DAY01").put("location","ห้องสีขาว").put("action","กวินยื่นซองให้รินรดา").put("flow_action","กวิน hands an envelope to รินรดา").put("start_state","นั่ง").put("end_state","มองซอง")
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
        assertEquals(4,doc.getElementsByTagName("prompt").length)
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
        assertTrue(command.contains("9:16")); assertFalse(command.contains("16:9"))
        assertTrue(command.contains("<sound_effects>")); assertTrue(command.contains("soft paper rustle"))
        assertEquals(scenes.getJSONObject(0).getString("dialogue"),DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(command.toByteArray(Charsets.UTF_8))).getElementsByTagName("text").item(0).textContent)
        val xml=AutoDirectorContent.flowXml(p,1)
        assertTrue(xml.contains("<sound_effects>")); assertTrue(xml.contains("soft paper rustle"))
        assertTrue(SceneCommandCards.join(videos).contains("=== ซีน 24"))
        SoundDesign.validate(scenes.getJSONObject(0))
        scenes.getJSONObject(0).getJSONObject("sound_design").remove("music")
        SoundDesign.normalizeOptionalMusic(scenes.getJSONObject(0))
        assertEquals("none",scenes.getJSONObject(0).getJSONObject("sound_design").getString("music"))
        SoundDesign.validate(scenes.getJSONObject(0))
        scenes.getJSONObject(0).getJSONObject("sound_design").remove("sfx")
        assertTrue(runCatching { SoundDesign.validate(scenes.getJSONObject(0)) }.isFailure)
    }
    @Test fun compactImagesKeepBrightWhiteLightingForOlderDarkScenes() {
        val p=story()
        val scene=p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        scene.put("lighting", "underexposed, dark orange shadows")
        val tasks=SimpleProductionCommands.imageTasks(p,1)
        val image=tasks.first { it.id=="EP01_SC001" }.command
        assertTrue(image.contains("Bright neutral-white 5500K"))
        assertTrue(image.contains("night remains night with bright neutral lighting"))
        assertFalse(image.contains("underexposed, dark orange shadows"))
        assertTrue(tasks.first { it.id=="STORY_COVER_9x16" }.command.contains(ImagePromptPolicy.skinTone))
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
            val expected=setOf("EP01_IMAGES.txt","EP01_FLOW.txt","EP02_IMAGES.txt","EP02_FLOW.txt") +
                (1..2).flatMap { ep -> (0..4).map { index -> SimpleProductionCommands.flowGroupPath(ep,index) } }
            assertEquals(expected,entries.keys)
            for(ep in 1..2) {
                val groups=SimpleProductionCommands.groupScenes(SimpleProductionCommands.videoTasks(p,ep))
                assertEquals(listOf(5,5,5,5,4),groups.map { it.size })
                groups.forEachIndexed { index, group ->
                    val content=entries.getValue(SimpleProductionCommands.flowGroupPath(ep,index))
                    assertEquals(group.size,Regex("ภาพซีนที่ต้องแนบเป็นเฟรมแรก:").findAll(content).count())
                    group.forEach { assertTrue(content.contains("รหัส: "+it.id)) }
                }
            }
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
                    assertTrue(videos.contains("รหัส: $id"))
                }
                assertFalse(images.contains("=== EP")); assertTrue(images.contains("สารบัญ"))
                assertTrue(images.contains("แก้ภาพ <รหัส>")); assertTrue(images.contains("PENDING"))
                assertTrue(videos.contains("<duration>8</duration>")); assertTrue(videos.contains("กวิน บอกว่า A &amp; B &lt; C ใช่ไหม"))
                assertFalse(videos.contains("STALE AUDIO"))
                assertTrue("Image export must remain reasonably compact",images.length<135000)
            }
            val cover=SimpleProductionCommands.imageTasks(p,1).first().command
            assertTrue(cover.contains("channel immediately above the title")); assertTrue(cover.contains("no large empty black panel"))
            assertTrue(cover.contains("position: left")); assertTrue(cover.contains("position: center")); assertTrue(cover.contains("position: right"))
            assertEquals(before,p.toString())
        }
        val one=story(); val tasks=SimpleProductionCommands.imageTasks(one,1)
        assertEquals(26,tasks.size); assertEquals(2,tasks.count { it.id.contains("COVER") })
        tasks.forEach { assertTrue(it.command.contains(ImagePromptPolicy.skinTone)); assertTrue(it.command.contains(BodyReferencePolicy.imageIdentityLock)) }
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
        assertTrue(covers.contains("channel immediately above the title")); assertFalse(covers.contains("EP.0"))
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
        assertTrue(code.contains(BodyReferencePolicy.preciseIdentity)); assertTrue(code.contains("warm gold")); assertTrue(code.contains("no large empty black panel"))
        assertTrue(code.contains("✅ โค้ดคำสั่งภาพเสร็จสิ้น! พิมพ์ 'สร้างบท EP.1'"))
        assertTrue(AutoDirectorContent.outline(p).contains("✅ โครงเรื่องพร้อม พิมพ์ 'สร้างภาพ EP.1'"))
        val speech=AutoDirectorContent.dialogue(p,1)
        assertTrue("Thai speaker must follow the scene speaker",speech.contains("รินรดา:")); assertFalse(speech.contains("Woman 1:"))
        assertTrue(speech.contains("✅ บทเสร็จสิ้น! พิมพ์ 'สร้าง XML EP.1'"))
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(AutoDirectorContent.flowXml(p,1).toByteArray(Charsets.UTF_8)))
        assertEquals(24,doc.getElementsByTagName("prompt").length)
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
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains("continuous 8-second"))
        assertTrue(doc.getElementsByTagName("dialogue").item(0).textContent.contains(scenes.getJSONObject(0).getString("dialogue")))
    }
    @Test fun xmlIsParseablePreservesThaiSpeechAndNeverUsesStaleAudio() {
        val p=story(); val text=AutoDirectorContent.flowXml(p,1)
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        assertEquals("flow",doc.documentElement.tagName)
        assertEquals(24,doc.getElementsByTagName("scene").length)
        assertEquals(24,doc.getElementsByTagName("aspect_ratio").length)
        assertEquals(24,doc.getElementsByTagName("prompt").length)
        assertEquals(24,doc.getElementsByTagName("duration").length)
        assertTrue(text.contains("Use the attached first frame"))
        assertTrue(text.contains("กวิน บอกว่า A &amp; B &lt; C ใช่ไหม&quot;"))
        assertTrue(text.contains("Off-screen narrator"))
        assertFalse(text.contains("STALE AUDIO"))
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
        val episodeCover=tasks.first { it.id=="EP02_COVER_9x16" }
        assertTrue(episodeCover.people.any { it.optString("name")=="หมอณัฐ" && it.optString("master")=="SUPPORT_AB12CD34_MASTER.png" })
        assertTrue(tasks.first { it.id=="STORY_COVER_9x16" }.people.any { it.optString("name")=="หมอณัฐ" && it.optString("master")=="SUPPORT_AB12CD34_MASTER.png" })
        assertTrue(tasks.first { it.id=="EP02_SC001" }.people.none { it.optString("name")=="หมอณัฐ" })
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
