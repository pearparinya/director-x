package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

@RunWith(AndroidJUnit4::class)
class FlowProductionPackTest {
    @Test fun primaryHomeOffersTextPlanningAndPackExportWithoutAnImageGate() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("studio",0)
        val previousProject=prefs.getString("project",null)
        prefs.edit().putString("project",UUID.randomUUID().toString()).commit()
        try {
            ActivityScenario.launch(StudioActivity::class.java).use { scenario -> scenario.onActivity { activity ->
                fun text(view: View): String = (if(view is TextView) view.text.toString() else "") +
                    (if(view is ViewGroup) (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) } else "")
                val home=text(activity.window.decorView)
                assertTrue(home.contains("DIRECTOR X"))
                assertTrue(home.contains("สร้างบทในแอป"))
                assertTrue(home.contains("ตรวจชุดตัวละคร"))
                assertTrue(home.contains("ตรวจสิ่งของในบท"))
                assertTrue(home.contains("สร้างภาพ + Flow"))
                assertTrue(home.contains("เครื่องมือเพิ่มเติม"))
                assertTrue(home.contains("รอสร้างบท"))
                assertFalse(home.contains("รับ MP4 / รวม EP / ดาวน์โหลด"))
            } }
        } finally { prefs.edit().putString("project",previousProject).commit() }
    }
    @Test fun productionStagesUnlockAfterImportAndExportStatusExpiresWhenStoryChanges() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("studio",0)
        val previous=prefs.getString("project",null)
        val id=UUID.randomUUID().toString()
        val folder=File(context.filesDir,"studio/$id").apply { mkdirs() }
        val story=File(folder,"story.json").apply { writeText(plan(4,23).toString()) }
        prefs.edit().putString("project",id).commit()
        try {
            ActivityScenario.launch(StudioActivity::class.java).use { scenario ->
                fun home(activity: StudioActivity): String {
                    fun text(view: View): String = (if(view is TextView) view.text.toString() else "") +
                        (if(view is ViewGroup) (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) } else "")
                    return text(activity.window.decorView)
                }
                scenario.onActivity { a ->
                    assertTrue(home(a).contains("ตรวจชุดตัวละคร"))
                    assertTrue(home(a).contains("ตรวจสิ่งของในบท"))
                    assertTrue(home(a).contains("เปิดใช้งานได้"))
                    assertTrue(home(a).contains("ตรวจบทแล้ว 0/"))
                    assertFalse(home(a).contains("เคยส่งออกบทนี้แล้ว"))
                }
                File(folder,"flow_pack_exported.sha256").writeText(EpisodeMedia.digest(story))
                scenario.recreate()
                scenario.onActivity { assertTrue(home(it).contains("เคยส่งออกบทนี้แล้ว")) }
                story.writeText(JSONObject(story.readText()).put("title","ชื่อเรื่องแก้ไขแล้ว").toString())
                scenario.recreate()
                scenario.onActivity {
                    assertFalse(home(it).contains("เคยส่งออกบทนี้แล้ว"))
                    assertTrue(home(it).contains("ชื่อเรื่องแก้ไขแล้ว"))
                    assertTrue(home(it).contains("เครื่องมือเพิ่มเติม"))
                }
                for(channel in listOf("นายเปีย บ้านคลองขุด","ร้อยฉาก")) {
                    story.writeText(JSONObject(story.readText()).put("channel",channel).toString())
                    scenario.recreate()
                    scenario.onActivity { assertTrue(home(it).contains(channel)) }
                    assertEquals(channel,JSONObject(story.readText()).getString("channel"))
                }
            }
        } finally { prefs.edit().putString("project",previous).commit(); folder.deleteRecursively() }
    }
    @Test fun savedWardrobeProgressSurvivesReopenAndInvalidatesOnOutfitChange() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("studio",0)
        val previous=prefs.getString("project",null)
        val id=UUID.randomUUID().toString()
        val folder=File(context.filesDir,"studio/$id").apply { mkdirs() }
        val data=plan(1,23)
        val ep=data.getJSONArray("episodes").getJSONObject(0)
        val arr=ep.getJSONArray("scenes")
        val scenes=(0 until arr.length()).map { arr.getJSONObject(it).put("ep",ep.getInt("ep")) }
        val file=File(folder,"story.json").apply { writeText(data.toString()) }
        val stamp=ReviewFingerprints.wardrobe(scenes)
        prefs.edit().putString("project",id).putString("wardrobe_selected_stamp_$id",stamp).commit()
        try {
            ActivityScenario.launch(StudioActivity::class.java).use { scenario ->
                fun home(activity: StudioActivity): String {
                    fun text(view: View): String = (if(view is TextView) view.text.toString() else "") +
                        (if(view is ViewGroup) (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) } else "")
                    return text(activity.window.decorView)
                }
                scenario.onActivity { assertTrue(home(it).contains("บันทึกชุดที่เลือกแล้ว")) }
                scenario.recreate()
                scenario.onActivity { assertTrue(home(it).contains("บันทึกชุดที่เลือกแล้ว")) }
                val changed=JSONObject(file.readText())
                changed.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
                    .getJSONArray("characters").getJSONObject(0).put("outfit","changed outfit")
                file.writeText(changed.toString())
                scenario.recreate()
                scenario.onActivity { assertTrue(home(it).contains("ข้อมูลชุดเปลี่ยน")) }
            }
        } finally { prefs.edit().putString("project",previous).remove("wardrobe_selected_stamp_$id").commit(); folder.deleteRecursively() }
    }
    @Test fun referenceLabelsLeadCommandsWithoutImageFilenames() {
        val single=FlowTestPrompt.build(listOf("mind"),"home","ห้องครัว","ถือแก้วน้ำ","")
        assertTrue(single.contains(FlowVisualDirection.referencePriority))
        assertTrue(single.contains(FlowVisualDirection.referenceReview))
        assertTrue(single.startsWith("แนบภาพตัวละคร: เมย์"))
        assertTrue(single.indexOf("CHARACTER_MIND")<single.indexOf("VIDEO PROMPT"))
        assertFalse(single.contains("CHARACTER_GAWIN")); assertFalse(single.contains("CHARACTER_RINLADA"))
        assertFalse(single.contains("IMAGE PROMPT")); assertTrue(single.contains("reference images directly"))
        val pair=FlowTestPrompt.build(listOf("gawin","rinlada"),"meeting","ห้องประชุม","ถือแฟ้ม","เสื้อคอสูงสีฟ้า")
        assertTrue(pair.startsWith("แนบภาพตัวละคร: กวิน + รินรดา"))
        assertTrue(pair.contains("เสื้อคอสูงสีฟ้า")); assertTrue(pair.contains("CHARACTER_GAWIN")); assertTrue(pair.contains("CHARACTER_RINLADA"))
        assertFalse(pair.contains("CHARACTER_MIND")); assertTrue(pair.contains("No dialogue or narration"))
        assertFalse(Regex("(?i)[.](png|jpg|jpeg|webp)").containsMatchIn(pair))
        assertTrue(runCatching { FlowTestPrompt.build(emptyList(),"home","ห้องครัว","ยืน","") }.isFailure)
        val old="MASTERS/GAWIN_MASTER.jpg RINLADA_MASTER.PNG MIND_MASTER.webp GAWIN_MASTER"
        val migrated=FlowReferencePrompt.normalize(old)
        assertFalse(migrated.contains("MASTER")); assertTrue(migrated.contains("CHARACTER_MIND"))
    }
    @Test fun registeredSupportingCharactersHaveOwnReferencesAndCannotBorrowALeadIdentity() {
        val extra=JSONObject().put("name","หมอณัฐ").put("master","SUPPORT_AB12CD34_MASTER.png").put("visual_description","adult fictional man, short gray hair")
        val story=plan(1,23).put("supporting_characters",JSONArray().put(extra))
        val rows=story.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
        for(i in 0 until rows.length()) rows.getJSONObject(i).getJSONArray("characters").put(JSONObject(extra.toString()).put("outfit","white medical coat").put("screen_position","right"))
        assertTrue(FlowProductionPack.issues(story).isEmpty())
        val command=FlowReferencePrompt.scene(rows.getJSONObject(0),1,"image")
        assertTrue(command.startsWith("แนบภาพตัวละคร: เมย์ + หมอณัฐ"))
        assertTrue(command.contains("CHARACTER_SUPPORT_AB12CD34")); assertTrue(command.contains("white medical coat"))
        assertFalse(command.contains("SUPPORT_AB12CD34_MASTER.png"))
        val unknown=JSONObject(story.toString()).put("supporting_characters",JSONArray())
        assertTrue(FlowProductionPack.issues(unknown).any { it.contains("MASTER ไม่ตรงคน") })
        val borrowed=JSONObject(extra.toString()).put("master","GAWIN_MASTER.png")
        assertFalse(FlowCast.matches(story,borrowed))
        assertTrue(StoryFiles.isMaster("SUPPORT_AB12CD34_MASTER.jpg"))
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val masters=FlowCast.definitions(story).associate { p -> val id=FlowCast.id(p)!!; id to File(root,id).apply { writeBytes(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0x01)) } }
            val output=ByteArrayOutputStream(); FlowProductionPack.write(output,story,masters,JSONObject(),"หมวด","ช่อง")
            val files=mutableMapOf<String,ByteArray>(); ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { z -> while(true) { val e=z.nextEntry ?: break; files[e.name]=z.readBytes() } }
            assertArrayEquals(masters.getValue("support_ab12cd34").readBytes(),files.getValue("DIRECTOR_FLOW/MASTERS/SUPPORT_AB12CD34_MASTER.jpg"))
            val backup=JSONObject(String(files.getValue("DIRECTOR_FLOW/BACKUP/story.json"),Charsets.UTF_8))
            assertEquals("หมอณัฐ",backup.getJSONArray("supporting_characters").getJSONObject(0).getString("name"))
            val six=JSONArray(); repeat(6) { i -> val scene=JSONObject(rows.getJSONObject(i).toString()).put("scene",i+1).put("speaker","").put("dialogue","").put("narration",""); listOf("story_day","story_time","activity","wardrobe_event","continuity_group").forEach { scene.put(it,"DAY01_HOME") }; six.put(scene) }
            GeminiStoryEngine.validateBatch(JSONObject().put("scenes",six),1,1,6,story.getJSONArray("supporting_characters"))
        } finally { root.deleteRecursively() }
    }
    private fun plan(count: Int, scenes: Int): JSONObject {
        val eps=JSONArray()
        repeat(count) { e ->
            val rows=JSONArray()
            repeat(scenes) { n -> rows.put(JSONObject().put("scene",n+1).put("duration_seconds",8).put("action","เมย์ยืนถือซอง")
                .put("flow_action","May stands holding an envelope")
                .put("image_prompt","May holds a sealed envelope, medium shot, photorealistic 9:16.")
                .put("flow_prompt","8 seconds. May looks down at the envelope. No speech.")
                .put("location","ห้องนั่งเล่น").put("start_state","ซองปิด").put("end_state","มองซอง")
                .put("characters",JSONArray().put(JSONObject().put("name","เมย์").put("master","MIND_MASTER.png").put("outfit","เสื้อสีครีม กางเกงขายาว")
                    .put("visual_description","short auburn bob").put("screen_position","frame-left")))) }
            eps.put(JSONObject().put("ep",e+1).put("title","ตอน ${e+1}").put("scenes",rows))
        }
        return JSONObject().put("aspect_ratio","9:16").put("title","ซองบนโต๊ะ").put("tagline","ความลับในซอง").put("episodes",eps)
    }
    @Test fun minimumDurationAndSpeakerValidationUseActualScenes() {
        assertTrue(FlowProductionPack.issues(plan(4,22)).any { it.contains("176") })
        assertTrue(FlowProductionPack.issues(plan(7,23)).isEmpty())
        val p=plan(1,23); val s=p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        s.put("dialogue","หยุดก่อน").put("speaker","กวิน")
        assertTrue(FlowProductionPack.issues(p).any { it.contains("ผู้พูด") })
    }
    @Test fun posterDirectionKeepsExactTextAndUsesAspectSpecificSafeZones() {
        val title="หลักฐาน 100% บนโต๊ะ"
        assertTrue(runCatching { FlowVisualDirection.cover("1x1","ref","ช่อง",title,2,"ชื่อ EP","คำโปรย","ซองบนโต๊ะ") }.isFailure)
        val vertical=FlowVisualDirection.cover("9x16","เมย์ → MIND_MASTER.jpg","ช่อง",title,2,"ชื่อ EP","คำโปรย","ซองบนโต๊ะ")
        assertTrue(vertical.contains(title)); assertTrue(vertical.contains("ตอนที่ 2 ชื่อ EP"))
        assertTrue(vertical.contains("one compact group")); assertTrue(vertical.contains("chest-to-waist")); assertTrue(vertical.contains("without revealing the ending"))
        assertTrue(vertical.contains("1080 x 1920")); assertTrue(vertical.contains("native portrait key art"))
        assertFalse(vertical.contains("1920 x 1080"))
        assertTrue(runCatching { FlowVisualDirection.layout("1x1") }.isFailure)
    }
    @Test fun compactZipContainsSelfContainedEpisodeBlocksAndExactMasters() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val masters=FlowProductionPack.names.keys.associateWith { id -> File(root,"$id.png").apply { TestMasterAssets.open("character_masters/${id.uppercase()}_MASTER.png").use { writeBytes(it.readBytes()) } } }
            val output=ByteArrayOutputStream()
            val story=plan(7,23)
            story.getJSONArray("episodes").getJSONObject(6).getJSONArray("scenes").getJSONObject(1).getJSONArray("characters").put(
                JSONObject().put("name","รินรดา").put("master","RINLADA_MASTER.png").put("outfit","ชุดทำงานสีขาว")
                    .put("visual_description","long dark wavy hair").put("screen_position","frame-right"))
            FlowProductionPack.write(output,story,masters,JSONObject().put("mind",JSONObject().put("face","custom face")),"หมวด","ช่อง")
            val files=mutableMapOf<String,ByteArray>()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip -> while(true) { val entry=zip.nextEntry ?: break; assertFalse(entry.name.contains("..")); assertNull(files.put(entry.name,zip.readBytes())) } }
            assertEquals(7,files.keys.count { Regex("DIRECTOR_FLOW/EP[0-9]+_IMAGES[.]txt").matches(it) })
            assertEquals(34,files.size)
            val groupedImages=String(files.getValue("DIRECTOR_FLOW/COMMANDS/EP07_IMAGES.txt"),Charsets.UTF_8)
            assertTrue(groupedImages.contains("BEGIN EXACT COVER TEXT\nช่อง\nซองบนโต๊ะ")); assertFalse(groupedImages.contains("STOP —"))
            assertTrue(String(files.getValue("DIRECTOR_FLOW/COMMANDS/EP07_VIDEOS.txt"),Charsets.UTF_8).contains("<duration>8</duration>"))
            assertFalse(files.keys.any { it.contains("COVERS/") })
            val memory=JSONObject(String(files.getValue("DIRECTOR_FLOW/BACKUP/story.json"),Charsets.UTF_8)).getJSONObject("character_memory")
            masters.forEach { (id,file) -> assertArrayEquals(file.readBytes(),files.getValue("DIRECTOR_FLOW/"+memory.getJSONObject(id).getString("file"))) }
            assertEquals("custom face",memory.getJSONObject("mind").getJSONObject("profile").getString("face"))
            val ep=String(files.getValue("DIRECTOR_FLOW/EP07_IMAGES.txt"),Charsets.UTF_8)
            assertTrue(ep.contains("184 seconds")); assertTrue(ep.contains("EP07_SC023")); assertTrue(ep.contains("COVER 16x9")); assertTrue(ep.contains("COVER 9x16"))
            assertTrue(ep.contains("BEGIN EXACT COVER TEXT\nช่อง\nซองบนโต๊ะ\nตอนที่ 7 ตอน 7\nEND EXACT COVER TEXT"))
            assertTrue(ep.contains("Rinrada:")); assertTrue(ep.contains("ชุดทำงานสีขาว"))
            assertTrue(ep.startsWith("แนบภาพตัวละคร")); assertTrue(ep.contains("one compact group"))
            assertTrue(ep.contains("No yellow/orange cast")); assertTrue(ep.contains("FINAL COLOR DIRECTION"))
            assertFalse(Regex("(?i)[.](png|jpg|jpeg|webp)").containsMatchIn(ep))
            assertFalse(ep.contains("VIDEO PROMPT"))
            val video=String(files.getValue("DIRECTOR_FLOW/EP07_VIDEOS_FLOW.txt"),Charsets.UTF_8)
            assertTrue(video.contains("EP07_SC023")); assertTrue(video.contains("<scene")); assertFalse(video.contains("IMAGE PROMPT"))
            val cover=String(files.getValue("DIRECTOR_FLOW/COVER_STORY.txt"),Charsets.UTF_8)
            assertTrue(cover.startsWith("แนบภาพตัวละคร: กวิน + รินรดา + เมย์"))
            assertTrue(cover.contains("1920 x 1080")); assertTrue(cover.contains("1080 x 1920"))
            assertTrue(cover.contains("BEGIN EXACT COVER TEXT\nช่อง\nซองบนโต๊ะ\nความลับในซอง\nEND EXACT COVER TEXT"))
            assertFalse(cover.contains("EP.00")); assertTrue(cover.contains(story.getString("title")))
            val scene=FlowReferencePrompt.scene(story.getJSONArray("episodes").getJSONObject(6).getJSONArray("scenes").getJSONObject(1),7,"video")
            assertTrue(scene.startsWith("<flow><scene id=\"EP07_SC002\">"))
            assertTrue(scene.contains("Use the attached first frame"))
            assertTrue(scene.contains("<duration>8</duration>"))
            assertTrue(ep.contains(FlowVisualDirection.referencePriority))
            assertTrue(files.containsKey("DIRECTOR_FLOW/START.txt"))
            assertFalse(files.keys.any { it.endsWith(".mp4") })
            val starter=ByteArrayOutputStream(); FlowProductionPack.write(starter,null,masters,JSONObject(),"หมวด","ช่อง")
            assertTrue(starter.size()>0)
        } finally { root.deleteRecursively() }
    }
}
