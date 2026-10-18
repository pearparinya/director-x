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

@RunWith(AndroidJUnit4::class)
class FlowProductionPackTest {
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
