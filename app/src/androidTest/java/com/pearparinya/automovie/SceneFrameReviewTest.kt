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
class SceneFrameReviewTest {
    @Test fun approvalExpiresWithBytesSceneOrReferenceChangesAndSurvivesReopen() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val frame=File(root,"frame").apply { writeText("original frame") }
            val master=File(root,"master").apply { writeText("original master") }
            val scene=JSONObject().put("scene",1).put("action","listen").put("dialogue","")
                .put("characters",JSONArray().put(JSONObject().put("name","เมย์").put("outfit","cream").put("screen_position","left")))
            assertFalse(SceneFrameReview.current(scene,frame,listOf(master)))
            SceneFrameReview.approve(scene,frame,listOf(master))
            val reopened=JSONObject(scene.toString())
            assertTrue(SceneFrameReview.current(reopened,frame,listOf(master)))
            val time=frame.lastModified(); frame.writeText("changed frame!"); frame.setLastModified(time)
            assertFalse(SceneFrameReview.current(reopened,frame,listOf(master)))
            frame.writeText("original frame")
            assertTrue(SceneFrameReview.current(reopened,frame,listOf(master)))
            master.writeText("changed master")
            assertFalse(SceneFrameReview.current(reopened,frame,listOf(master)))
            master.writeText("original master")
            for(field in listOf("action","dialogue","speaker","image_prompt","flow_prompt","lighting")) {
                val changed=JSONObject(reopened.toString()).put(field,"changed")
                assertFalse(field,SceneFrameReview.current(changed,frame,listOf(master)))
            }
            reopened.getJSONArray("characters").getJSONObject(0).put("outfit","navy")
            assertFalse(SceneFrameReview.current(reopened,frame,listOf(master)))
            master.delete()
            assertFalse(SceneFrameReview.current(scene,frame,listOf(master)))
            assertTrue(runCatching { SceneFrameReview.approve(scene,frame,listOf(master)) }.isFailure)
        } finally { root.deleteRecursively() }
    }
}
