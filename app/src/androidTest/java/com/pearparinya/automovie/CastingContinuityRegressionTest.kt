package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CastingContinuityRegressionTest {
    private fun scene(light: String, time: String = "18:30") = JSONObject()
        .put("story_day", "1").put("story_time", time)
        .put("location", "หน้าบ้าน").put("continuity_group", "dusk")
        .put("lighting", light)

    @Test fun immediateDuskAndExplicitChange() {
        val first = scene("dusk")
        val second = scene("daylight", "18:31")
        assertEquals("dusk", CharacterContinuity.imageLighting(first, second))
        second.put("lighting_change", true)
        assertEquals("daylight", CharacterContinuity.imageLighting(first, second))
        second.put("lighting_change", false).put("location", "ตลาด")
        assertEquals("daylight", CharacterContinuity.imageLighting(first, second))
    }

    @Test fun wardrobeReferenceSurvivesReopenAcrossStorySizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (count in listOf(8, 20, 50, 100)) {
            val folder = File(context.cacheDir, "casting-regression-$count")
            try {
                val approved = CastOutfit("ACTOR_MASTER", "เสื้อลายไทย")
                approved.file(folder).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
                repeat(count) { index ->
                    val reopened = File(folder.absolutePath)
                    val actor = CastOutfit("ACTOR_MASTER", if (index == count - 1) "เสื้อใหม่" else "เสื้อลายไทย")
                    if (index == count - 1) assertNull(actor.attachment(reopened))
                    else {
                        assertEquals(approved.attachment(folder)?.first, actor.attachment(reopened)?.first)
                        assertArrayEquals(byteArrayOf(1, 2, 3), actor.attachment(reopened)!!.second.readBytes())
                    }
                }
                assertNull(CastOutfit("OTHER_MASTER", "เสื้อลายไทย").attachment(folder))
            } finally { folder.deleteRecursively() }
        }
    }
}
