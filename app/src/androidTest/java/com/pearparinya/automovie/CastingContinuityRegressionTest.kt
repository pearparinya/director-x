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

    @Test fun ensemblePosterUsesOnlySceneMastersAndHonorsSavedChoice() {
        val cast = listOf("A", "B", "C").map { JSONObject().put("master", "$it.png") }
        assertEquals(listOf(cast[2], cast[0]), SimpleProductionCommands.selectCoverCast(cast, setOf("C"), true))
        assertTrue(SimpleProductionCommands.selectCoverCast(cast.take(1), null, true).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            SimpleProductionCommands.selectCoverCast(cast, setOf("A", "unknown"), true)
        }
        assertEquals(cast.take(1), SimpleProductionCommands.selectCoverCast(cast, setOf("A"), false))
    }

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
    @Test fun castingDialogShowsApprovedAndMissingActorsAfterReopen() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val ctx = ins.targetContext
        val prefs = ctx.getSharedPreferences("studio", 0)
        val original = prefs.getString("project", "draft")
        val id = java.util.UUID.randomUUID().toString()
        val folder = File(ctx.filesDir, "studio/$id")
        val master = "CHARACTER_TEST.png"
        val actor = JSONObject().put("name", "นักแสดงทดสอบ").put("master", master).put("outfit", "เสื้อลายไทย")
        val missing = JSONObject().put("name", "นักแสดงรอ MASTER")
        val story = JSONObject().put("title", "Casting UI").put("episodes", org.json.JSONArray().put(
            JSONObject().put("ep", 1).put("scenes", org.json.JSONArray().put(
                JSONObject().put("scene", 1).put("characters", org.json.JSONArray().put(actor).put(missing))))))
        fun views(v: android.view.View): List<android.view.View> = listOf(v) +
            if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
        try {
            folder.mkdirs()
            File(folder, "story.json").writeText(story.toString())
            val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
            File(folder, "masters/$master").apply {
                parentFile!!.mkdirs()
                outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            assertTrue(prefs.edit().putString("project", id).commit())
            repeat(2) {
                val activity = ins.startActivitySync(android.content.Intent(ctx, StudioActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as StudioActivity
                try {
                    ins.runOnMainSync {
                        val outfitMethod = StudioActivity::class.java.getDeclaredMethod("castOutfit", JSONObject::class.java)
                        outfitMethod.isAccessible = true
                        val outfit = outfitMethod.invoke(activity, actor) as CastOutfit
                        if (it == 0) outfit.file(folder).apply {
                            parentFile!!.mkdirs()
                            outputStream().use { stream -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream) }
                        }
                        val method = StudioActivity::class.java.getDeclaredMethod("castOutfitMenu", JSONObject::class.java)
                        method.isAccessible = true
                        val dialog = method.invoke(activity, story) as androidx.appcompat.app.AlertDialog
                        try {
                            val all = views(dialog.window!!.decorView)
                            val labels = all.filterIsInstance<android.widget.TextView>().map { v -> v.text.toString() }
                            assertTrue(labels.any { v -> v.contains("นักแสดงทดสอบ") && v.contains("ยืนยันชุดแล้ว") })
                            assertTrue(labels.any { v -> v.contains("นักแสดงรอ MASTER") && v.contains("ขาด MASTER") })
                            val images = all.filterIsInstance<android.widget.ImageView>()
                            assertTrue(images.any { v -> v.contentDescription == "MASTER" && v.drawable != null })
                            assertTrue(images.any { v -> v.contentDescription == "ชุดที่ยืนยัน" && v.drawable != null })
                        } finally { dialog.dismiss() }
                    }
                } finally { ins.runOnMainSync { activity.finish() }; ins.waitForIdleSync() }
            }
        } finally {
            prefs.edit().putString("project", original).commit()
            folder.deleteRecursively()
        }
    }
    @Test fun numericStoryDaysCannotReverseUnlessFlashback() {
        val first = CharacterContinuity.Beat("SC001", "Actor", "MASTER", "3", "18:30",
            "Home", "Outfit", "", "", "group", false, false)
        val second = first.copy(scene = "SC002", day = "2")
        assertTrue(CharacterContinuity.issues(listOf(first, second)).any { it.contains("flashback") })
        assertTrue(CharacterContinuity.issues(listOf(first, second.copy(flashback = true))).isEmpty())
        assertTrue(CharacterContinuity.issues(listOf(first, second.copy(day = "4"))).isEmpty())
    }
    @Test fun locationChangesRequireExplicitTransitionInContinuousGroup() {
        val first = CharacterContinuity.Beat("SC001", "Actor", "MASTER", "1", "18:30",
            "Home", "Outfit", "", "", "group", false, false)
        val second = first.copy(scene = "SC002", location = "Market", time = "18:31")
        assertTrue(CharacterContinuity.issues(listOf(first, second)).any { it.contains("location_change") })
        assertTrue(CharacterContinuity.issues(listOf(first, second.copy(locationChange = true))).isEmpty())
        assertTrue(CharacterContinuity.issues(listOf(first, second.copy(group = "new"))).isEmpty())
    }
    @Test fun stateTransitionsAndFlashbackLightingAcrossStorySizes() {
        for (count in listOf(8, 20, 50, 100)) {
            val first = CharacterContinuity.Beat("SC001", "Actor", "MASTER", "1", "18:30",
                "Home", "Outfit", "dry", "dry", "group", false, false)
            val beats = List(count) { first.copy(scene = "SC"+(it+1)) }
            assertTrue(CharacterContinuity.issues(beats).isEmpty())
            val changed = beats.last().copy(state = "wet")
            assertTrue(CharacterContinuity.issues(beats.dropLast(1)+changed).any { it.contains("สถานะต้นฉาก") })
            assertTrue(CharacterContinuity.issues(beats.dropLast(1)+changed.copy(stateChange = true)).isEmpty())
        }
        val prior = scene("dusk").put("flashback", true)
        assertEquals("daylight", CharacterContinuity.imageLighting(prior, scene("daylight", "18:31")))
    }
}
