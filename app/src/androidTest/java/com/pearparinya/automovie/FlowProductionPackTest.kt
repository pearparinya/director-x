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
                assertTrue(home.contains("สร้างเรื่องใหม่"))
                assertTrue(home.contains("ตรวจชุดตัวละคร"))
                assertTrue(home.contains("ตรวจอุปกรณ์ประกอบฉาก"))
                assertTrue(home.contains("สร้างภาพ"))
                assertTrue(home.contains("คำสั่ง Flow"))
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
                    assertTrue(home(a).contains("ตรวจอุปกรณ์ประกอบฉาก"))
                    assertTrue(home(a).contains("รอตรวจ"))
                    assertTrue(home(a).contains("ตรวจผ่าน 0/"))
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
    // Window transitions can briefly leave UiAutomation without an active root.
    // Re-query fresh nodes until the requested control is actually usable.
    private fun awaitControl(label: String, click: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000L
        do {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                // Custom menu tiles expose their labels as content descriptions.
                // Traverse both text and descriptions, including scroll containers.
                val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                val scrolls = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                fun collect(node: android.view.accessibility.AccessibilityNodeInfo) {
                    if (node.text?.toString()?.contains(label) == true ||
                        node.contentDescription?.toString()?.contains(label) == true) nodes.add(node)
                    if (node.isScrollable && node.isVisibleToUser) scrolls.add(node)
                    for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it) }
                }
                collect(root)
                for (node in nodes) {
                    if (!node.isVisibleToUser) continue
                    if (!click) return
                    var target: android.view.accessibility.AccessibilityNodeInfo? = node
                    while (target != null) {
                        if (target.isClickable && target.isEnabled &&
                            target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return
                        target = target.parent
                    }
                }
                // Home tiles may be below the fold; scroll the real UI, not a hidden view.
                if (scrolls.none { it.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }) {
                    scrolls.any { it.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
                }
            }
            android.os.SystemClock.sleep(250L)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
        fail("Timed out waiting for ${if (click) "enabled control" else "visible text"}: $label")
    }

    @Test fun leavingDeferredSceneProposalPreservesTheSceneAndRestoresItsParentOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scene = JSONObject().put("ep", 3).put("scene", 7).put("dialogue", "บทเดิม")
        val snapshot = scene.toString()
        val restored = java.util.concurrent.atomic.AtomicInteger()
        ActivityScenario.launch(StudioActivity::class.java).use { scenario ->
            repeat(2) { pass ->
                scenario.onActivity { activity ->
                    val method = StudioActivity::class.java.getDeclaredMethod("showSpeechProposal",
                        JSONObject::class.java, String::class.java, String::class.java, String::class.java,
                        String::class.java, String::class.java, Function0::class.java)
                    method.isAccessible = true
                    val back: () -> Unit = { restored.incrementAndGet(); Unit }
                    activity.window.decorView.post {
                        method.invoke(activity, scene, "dialogue", "บทเดิม", "ข้อเสนอ", "fixture", snapshot, back)
                    }
                }
                awaitControl("ตรวจข้อเสนอบท", false)
                if (pass == 0) awaitControl("ย้อนกลับ", true)
                else instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                instrumentation.uiAutomation.waitForIdle(300, 5000)
                val deadline=android.os.SystemClock.uptimeMillis()+5000L
                while(restored.get()<pass+1 && android.os.SystemClock.uptimeMillis()<deadline) {
                    instrumentation.waitForIdleSync()
                    android.os.SystemClock.sleep(50)
                }
                assertEquals(pass + 1, restored.get())
                assertEquals(snapshot, scene.toString())
            }
        }
    }

    @Test fun providerProbeReturnsToItsSettingsAfterBothBackControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(StudioActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val menu = StudioActivity::class.java.getDeclaredMethod("geminiSettings", Function0::class.java)
                menu.isAccessible = true
                menu.invoke(activity, null)
            }
            repeat(2) { pass ->
                awaitControl("ตรวจ API ที่เลือก", true)
                awaitControl("ทดสอบ API •", false)
                if (pass == 0) awaitControl("ย้อนกลับ", true)
                else instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                awaitControl("ตั้งค่าคีย์ API • ใช้อัตโนมัติ", false)
            }
        }
    }

    @Test fun productionMenusOpenFromHomeAndEmptyFlowSceneOffersAnalysis() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("studio",0)
        val oldProject=prefs.getString("project",null)
        val id=UUID.randomUUID().toString()
        val folder=File(context.filesDir,"studio/$id").apply { mkdirs() }
        val story=plan(12,1).put("channel","นายเปีย บ้านคลองขุด").put("category","ละครสั้นสะท้อนสังคม")
        for (index in 0 until story.getJSONArray("episodes").length()) {
            val episode=story.getJSONArray("episodes").getJSONObject(index)
            val scenes=episode.getJSONArray("scenes")
            for (n in 0 until scenes.length()) {
                scenes.getJSONObject(n)
                    .put("ep", episode.getInt("ep"))
                    .put("story_day", "DAY01")
            }
        }
        assertTrue(SimpleProductionCommands.imageTasks(story,12).isNotEmpty())
        File(folder,"story.json").writeText(story.toString())
        prefs.edit().putString("project",id).commit()
        fun tap(label: String) = awaitControl(label, true)
        fun episodeRowTop(): Int {
            val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            fun find(node: android.view.accessibility.AccessibilityNodeInfo): Int? {
                if (node.isVisibleToUser && node.contentDescription?.contains("EP.12 • ตอน 12") == true) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    return bounds.top
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    find(child)?.let { return it }
                }
                return null
            }
            return requireNotNull(find(root)) { "EP.12 must still be visible without scrolling" }
        }
        try {
            ActivityScenario.launch(StudioActivity::class.java).use {
                tap("สร้างภาพ")
                tap("เลือกโหมด AI + Kotlin")
                assertTrue(prefs.getBoolean("image_api_assist_$id",false))
                tap("เลือกโหมด Kotlin")
                assertFalse(prefs.getBoolean("image_api_assist_$id",true))
                tap("ดูคำสั่งสร้างภาพ")
                awaitControl("EP.12 • ตอน 12", false)
                instrumentation.uiAutomation.waitForIdle(300, 5000)
                val rowTop = episodeRowTop()
                repeat(2) { pass ->
                    tap("EP.12 • ตอน 12")
                    awaitControl("เลือกชุดภาพ", false)
                    awaitControl("ภาพฉาก", false)
                    if (pass == 0) tap("ย้อนกลับ")
                    else instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    instrumentation.uiAutomation.waitForIdle(300, 5000)
                    assertEquals(rowTop, episodeRowTop())
                }
                tap("ย้อนกลับ")
                tap("ยกเลิก")
                tap("คำสั่ง Flow")
                tap("เลือกโหมด AI + Kotlin")
                assertTrue(prefs.getBoolean("flow_dashboard_mode_$id",false))
                tap("📄 ดูคำสั่ง Flow สร้างวิดีโอ")
                tap("EP.1")
                tap("วิเคราะห์ฉากนี้")
                tap("ตั้งค่า API")
                awaitControl("จัดการ API Key", false)
            }
        } finally {
            prefs.edit().putString("project",oldProject).commit()
            folder.deleteRecursively()
        }
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
            // Keep the complete export contract enabled; expose the precise missing
            // entry or malformed content in CI rather than a bare AssertionError.
            fun requireEntry(path: String): String {
                val bytes=files[path] ?: throw AssertionError(
                    "Missing ZIP entry $path; actual entries: ${files.keys.sorted().joinToString()}"
                )
                return String(bytes,Charsets.UTF_8)
            }
            val required=listOf("DIRECTOR_FLOW/START.txt","DIRECTOR_FLOW/COVER_STORY.txt",
                "DIRECTOR_FLOW/BACKUP/story.json","DIRECTOR_FLOW/COMMANDS/EP07_IMAGES.txt",
                "DIRECTOR_FLOW/COMMANDS/EP07_VIDEOS.txt","DIRECTOR_FLOW/EP07_IMAGES.txt",
                "DIRECTOR_FLOW/EP07_VIDEOS_FLOW.txt")
            required.forEach(::requireEntry)
            val exportSummary=files.keys.sorted().joinToString()
            fun checkExport(label: String, condition: Boolean) {
                assertTrue("$label | ZIP entries: $exportSummary",condition)
            }
            assertEquals(7,files.keys.count { Regex("DIRECTOR_FLOW/EP[0-9]+_IMAGES[.]txt").matches(it) })
            assertEquals(34,files.size)
            val groupedImages=String(files.getValue("DIRECTOR_FLOW/COMMANDS/EP07_IMAGES.txt"),Charsets.UTF_8)
            checkExport("export assertion 2",groupedImages.contains("BEGIN EXACT COVER TEXT\nช่อง\nซองบนโต๊ะ")); assertFalse(groupedImages.contains("STOP —"))
            checkExport("export assertion 3",String(files.getValue("DIRECTOR_FLOW/COMMANDS/EP07_VIDEOS.txt"),Charsets.UTF_8).contains("<duration>8</duration>"))
            assertFalse(files.keys.any { it.contains("COVERS/") })
            val memory=JSONObject(String(files.getValue("DIRECTOR_FLOW/BACKUP/story.json"),Charsets.UTF_8)).getJSONObject("character_memory")
            masters.forEach { (id,file) -> assertArrayEquals(file.readBytes(),files.getValue("DIRECTOR_FLOW/"+memory.getJSONObject(id).getString("file"))) }
            assertEquals("custom face",memory.getJSONObject("mind").getJSONObject("profile").getString("face"))
            val ep=String(files.getValue("DIRECTOR_FLOW/EP07_IMAGES.txt"),Charsets.UTF_8)
            checkExport("export assertion 4",ep.contains("184 seconds")); checkExport("export assertion 5",ep.contains("EP07_SC023")); checkExport("export assertion 6",ep.contains("COVER 16x9")); checkExport("export assertion 7",ep.contains("COVER 9x16"))
            checkExport("export assertion 8",ep.contains("BEGIN EXACT COVER TEXT\nช่อง\nซองบนโต๊ะ\nตอนที่ 7 ตอน 7\nEND EXACT COVER TEXT"))
            checkExport("export assertion 9",ep.contains("Rinrada:")); checkExport("export assertion 10",ep.contains("ชุดทำงานสีขาว"))
            checkExport("export assertion 11",ep.startsWith("แนบภาพตัวละคร")); checkExport("export assertion 12",ep.contains("one compact group"))
            checkExport("export assertion 13",ep.contains("No yellow/orange cast")); checkExport("export assertion 14",ep.contains("FINAL COLOR DIRECTION"))
            assertFalse(Regex("(?i)[.](png|jpg|jpeg|webp)").containsMatchIn(ep))
            assertFalse(ep.contains("VIDEO PROMPT"))
            val video=String(files.getValue("DIRECTOR_FLOW/EP07_VIDEOS_FLOW.txt"),Charsets.UTF_8)
            checkExport("export assertion 15",video.contains("EP07_SC023")); checkExport("export assertion 16",video.contains("<scene")); assertFalse(video.contains("IMAGE PROMPT"))
            val cover=String(files.getValue("DIRECTOR_FLOW/COVER_STORY.txt"),Charsets.UTF_8)
            checkExport("export assertion 17",cover.startsWith("แนบภาพตัวละคร: กวิน + รินรดา + เมย์"))
            checkExport("export assertion 18",cover.contains("1920 x 1080")); checkExport("export assertion 19",cover.contains("1080 x 1920"))
            checkExport("export assertion 20",cover.contains("BEGIN EXACT COVER TEXT\nช่อง\nซองบนโต๊ะ\nความลับในซอง\nEND EXACT COVER TEXT"))
            assertFalse(cover.contains("EP.00")); checkExport("export assertion 21",cover.contains(story.getString("title")))
            val scene=FlowReferencePrompt.scene(story.getJSONArray("episodes").getJSONObject(6).getJSONArray("scenes").getJSONObject(1),7,"video")
            checkExport("export assertion 22: Flow XML scene identity",scene.trimStart().startsWith("<flow><scene id=\"EP07_SC002\">"))
            checkExport("export assertion 23",scene.contains("Use the attached first frame"))
            checkExport("export assertion 24",scene.contains("<duration>8</duration>"))
            checkExport("export assertion 25",ep.contains(FlowVisualDirection.referencePriority))
            checkExport("export assertion 26",files.containsKey("DIRECTOR_FLOW/START.txt"))
            assertFalse(files.keys.any { it.endsWith(".mp4") })
            val starter=ByteArrayOutputStream(); FlowProductionPack.write(starter,null,masters,JSONObject(),"หมวด","ช่อง")
            checkExport("export assertion 27",starter.size()>0)
        } finally { root.deleteRecursively() }
    }
}
