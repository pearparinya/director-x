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
        val previousLegacy=prefs.getBoolean("legacy_image_workflow",false)
        prefs.edit().putString("project",UUID.randomUUID().toString()).putBoolean("legacy_image_workflow",true).commit()
        try {
            ActivityScenario.launch(StudioActivity::class.java).use { scenario -> scenario.onActivity { activity ->
                fun text(view: View): String = (if(view is TextView) view.text.toString() else "") +
                    (if(view is ViewGroup) (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) } else "")
                val home=text(activity.window.decorView)
                assertTrue(home.contains("FLOW STUDIO"))
                assertTrue(home.contains("ตอนละอย่างน้อย 3 นาที"))
                assertTrue(home.contains("ส่งออก ZIP สำหรับ Flow"))
                assertFalse(home.contains("ChatGPT"))
                                val stages=listOf("วางบทและเขียนคำสั่ง","นำเข้าบท / ZIP","ส่งออก ZIP สำหรับ Flow","รับ MP4 / รวม EP / ดาวน์โหลด")
                stages.forEach { assertTrue(home.contains(it)) }
                assertTrue(home.contains("รอนำเข้าบท"))
                assertTrue(home.contains("รอบทสำหรับจับคู่คลิป"))
                assertTrue(home.indexOf("รับ MP4 / รวม EP / ดาวน์โหลด") < home.indexOf("ทดสอบสร้างวิดีโอ"))
                assertFalse(home.contains("เครื่องมือภาพเดิม"))
                assertTrue(home.contains("ทดสอบสร้างวิดีโอ"))
            } }
        } finally {
            prefs.edit().putString("project",previousProject).putBoolean("legacy_image_workflow",previousLegacy).commit()
        }
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
                    assertTrue(home(a).contains("นำเข้าบทแล้ว"))
                    assertTrue(home(a).contains("พร้อมส่งออกคำสั่ง"))
                    assertTrue(home(a).contains("รับคลิป / ทำต่อได้"))
                    assertFalse(home(a).contains("รอนำเข้าบท"))
                }
                File(folder,"flow_pack_exported.sha256").writeText(EpisodeMedia.digest(story))
                scenario.recreate()
                scenario.onActivity { assertTrue(home(it).contains("ส่งออกบทฉบับนี้แล้ว")) }
                story.writeText(JSONObject(story.readText()).put("title","ชื่อเรื่องแก้ไขแล้ว").toString())
                scenario.recreate()
                scenario.onActivity {
                    assertFalse(home(it).contains("ส่งออกบทฉบับนี้แล้ว"))
                    assertTrue(home(it).contains("พร้อมส่งออกคำสั่ง"))
                    assertTrue(home(it).contains("ทดสอบสร้างวิดีโอ"))
                }
            }
        } finally { prefs.edit().putString("project",previous).commit(); folder.deleteRecursively() }
    }
    @Test fun testCommandsOnlyUseSelectedMastersAndHonorSceneSettings() {
        val refs=mapOf("gawin" to "GAWIN_MASTER.jpg","rinlada" to "RINLADA_MASTER.png","mind" to "MIND_MASTER.jpg")
        val single=FlowTestPrompt.build(listOf("mind"),refs,"home","ห้องครัว","ถือแก้วน้ำ","")
        assertTrue(single.contains("MIND_MASTER.jpg"))
        assertFalse(single.contains("GAWIN_MASTER"))
        assertFalse(single.contains("RINLADA_MASTER"))
        assertFalse(single.contains("IMAGE PROMPT")); assertTrue(single.contains("VIDEO PROMPT"))
        assertTrue(single.startsWith("แนบภาพ MASTER: เมย์ = MIND_MASTER.jpg"))
        assertFalse(single.contains("as the first frame"))
        assertTrue(single.contains("reference images directly"))
        val pair=FlowTestPrompt.build(listOf("gawin","rinlada"),refs,"meeting","ห้องประชุม","ถือแฟ้ม","เสื้อคอสูงสีฟ้า")
        assertTrue(pair.contains("เสื้อคอสูงสีฟ้า")); assertTrue(pair.contains("GAWIN_MASTER.jpg")); assertTrue(pair.contains("RINLADA_MASTER.png"))
        assertFalse(pair.contains("MIND_MASTER")); assertFalse(pair.contains("ChatGPT"))
        assertTrue(pair.contains("No dialogue or narration"))
        assertTrue(runCatching { FlowTestPrompt.build(emptyList(),refs,"home","ห้องครัว","ยืน","") }.isFailure)
        assertTrue(runCatching { FlowTestPrompt.build(listOf("mind"),emptyMap(),"home","ห้องครัว","ยืน","") }.isFailure)
    }
    @Test fun attachmentFilenamesMatchUsersFlowAssetsAndRejectAmbiguousNames() {
        val refs=FlowTestPrompt.attachmentDefaults
        assertEquals("1000026557.png",refs["gawin"])
        assertEquals("1000026586.png",refs["mind"])
        assertEquals("1000026584.png",refs["rinlada"])
        val code=FlowTestPrompt.build(listOf("rinlada"),refs,"home","ห้องนั่งเล่น","ถือแก้วน้ำ","")
        assertTrue(code.startsWith("แนบภาพ MASTER: รินรดา = 1000026584.png"))
        assertFalse(code.contains("1000026557")); assertFalse(code.contains("1000026586"))
        assertTrue(code.contains("chest-to-waist ratio"))
        assertTrue(runCatching { FlowTestPrompt.build(listOf("mind","rinlada"),mapOf("mind" to "same.png","rinlada" to "same.png"),"home","room","stand","") }.isFailure)
        assertTrue(runCatching { FlowTestPrompt.build(listOf("mind"),mapOf("mind" to "../photo.png"),"home","room","stand","") }.isFailure)
        val custom=FlowTestPrompt.build(listOf("mind"),mapOf("mind" to "my_may.jpg"),"home","room","stand","")
        assertTrue(custom.startsWith("แนบภาพ MASTER: เมย์ = my_may.jpg"))
    }
    private fun plan(count: Int, scenes: Int): JSONObject {
        val eps=JSONArray()
        repeat(count) { e ->
            val rows=JSONArray()
            repeat(scenes) { n -> rows.put(JSONObject().put("scene",n+1).put("duration_seconds",8).put("action","เมย์ยืนถือซอง")
                .put("image_prompt","May holds a sealed envelope, medium shot, photorealistic 9:16.")
                .put("flow_prompt","8 seconds. May looks down at the envelope. No speech.")
                .put("location","ห้องนั่งเล่น").put("start_state","ซองปิด").put("end_state","มองซอง")
                .put("characters",JSONArray().put(JSONObject().put("name","เมย์").put("master","MIND_MASTER.png").put("outfit","เสื้อสีครีม กางเกงขายาว")
                    .put("visual_description","short auburn bob").put("screen_position","frame-left")))) }
            eps.put(JSONObject().put("ep",e+1).put("title","ตอน ${e+1}").put("scenes",rows))
        }
        return JSONObject().put("title","ซองบนโต๊ะ").put("episodes",eps)
    }
    @Test fun minimumDurationAndSpeakerValidationUseActualScenes() {
        assertTrue(FlowProductionPack.issues(plan(4,22)).any { it.contains("176") })
        assertTrue(FlowProductionPack.issues(plan(7,23)).isEmpty())
        val p=plan(1,23); val s=p.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
        s.put("dialogue","หยุดก่อน").put("speaker","กวิน")
        assertTrue(FlowProductionPack.issues(p).any { it.contains("ผู้พูด") })
    }
    @Test fun zipContainsSeparateEpisodesCoversAndExactMastersWithoutGeneratedMedia() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val masters=FlowProductionPack.names.keys.associateWith { id -> File(root,"$id.png").apply { context.assets.open("character_masters/${id.uppercase()}_MASTER.png").use { writeBytes(it.readBytes()) } } }
            val output=ByteArrayOutputStream()
            FlowProductionPack.write(output,plan(7,23),masters,JSONObject().put("mind",JSONObject().put("face","custom face")),"หมวด","ช่อง")
            val files=mutableMapOf<String,ByteArray>()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip -> while(true) { val entry=zip.nextEntry ?: break; assertFalse(entry.name.contains("..")); assertNull(files.put(entry.name,zip.readBytes())) } }
            assertEquals(7,files.keys.count { it.startsWith("DIRECTOR_FLOW/EP/") })
            assertEquals(14,files.keys.count { it.startsWith("DIRECTOR_FLOW/COVERS/") })
            val memory=JSONObject(String(files.getValue("DIRECTOR_FLOW/CHARACTER_MEMORY.json"),Charsets.UTF_8))
            masters.forEach { (id,file) -> assertArrayEquals(file.readBytes(),files.getValue("DIRECTOR_FLOW/"+memory.getJSONObject(id).getString("file"))) }
            assertEquals("custom face",memory.getJSONObject("mind").getJSONObject("profile").getString("face"))
            val ep=String(files.getValue("DIRECTOR_FLOW/EP/EP07_FLOW.txt"),Charsets.UTF_8)
            assertTrue(ep.contains("184 seconds")); assertTrue(ep.contains("EP07_SC023")); assertFalse(ep.contains("COVER"))
            assertTrue(String(files.getValue("DIRECTOR_FLOW/COVERS/EP07_COVER_16x9.txt"),Charsets.UTF_8).contains("May:"))
            assertFalse(files.keys.any { it.endsWith(".mp4") })
            val starter=ByteArrayOutputStream(); FlowProductionPack.write(starter,null,masters,JSONObject(),"หมวด","ช่อง")
            assertTrue(starter.size()>0)
        } finally { root.deleteRecursively() }
    }
}
