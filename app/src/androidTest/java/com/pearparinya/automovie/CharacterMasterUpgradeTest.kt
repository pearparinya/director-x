package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterMasterUpgradeTest {
    @Test fun mayOnlyRevisionPreservesRinladaAfter189AndRunsOnlyOnce() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val globals=File(root,"character_masters").apply { mkdirs() }
            val project=File(root,"studio/original").apply { mkdirs() }
            listOf(CharacterMasterUpgrade.REVISION,CharacterMasterUpgrade.FEMALE_REVISION,CharacterMasterUpgrade.LATEST_FEMALE_REVISION,CharacterMasterUpgrade.FEMALE_2159_REVISION,CharacterMasterUpgrade.CURRENT_REVISION).forEach {
                File(globals,"$it.installed").writeText("installed"); File(project,"$it.installed").writeText("installed")
            }
            CharacterMasterUpgrade.ids.forEach { id ->
                File(globals,"$id.master").writeText("previous $id")
                File(project,"original_masters/$id.master").apply { parentFile!!.mkdirs(); writeText("snapshot $id") }
            }
            val story=File(project,"story.json").apply { writeText("""{"character_mode":"original","episodes":[{"scenes":[{"characters":[{"name":"รินรดา"}],"flow_image_revision":5,"user_review":"approved"},{"characters":[{"name":"เมย์"}],"flow_image_revision":5,"user_review":"approved"}]}]}""") }
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertEquals("previous rinlada",File(globals,"rinlada.master").readText())
            assertEquals("snapshot rinlada",File(project,"original_masters/rinlada.master").readText())
            val may=File(project,"original_masters/mind.master")
            assertEquals("character_masters/MIND_MASTER.png",may.readText())
            assertTrue(File(project,"versions/${CharacterMasterUpgrade.MAY_REVISION}").listFiles()!!.any { it.readText()=="snapshot mind" })
            val scenes=JSONObject(story.readText()).getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
            assertEquals("approved",scenes.getJSONObject(0).getString("user_review"))
            assertTrue(scenes.getJSONObject(0).has("flow_image_revision"))
            assertEquals("needs_revision",scenes.getJSONObject(1).getString("user_review"))
            assertFalse(scenes.getJSONObject(1).has("flow_image_revision"))
            may.writeText("later custom May")
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertEquals("later custom May",may.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun latestFemaleRevisionUpdates190WithoutReplacingGawinOrReviewingHisSoloScene() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val globals=File(root,"character_masters").apply { mkdirs() }
            File(globals,"${CharacterMasterUpgrade.REVISION}.installed").writeText("installed")
            File(globals,"${CharacterMasterUpgrade.CURRENT_REVISION}.installed").writeText("installed")
            File(globals,"${CharacterMasterUpgrade.FEMALE_REVISION}.installed").writeText("installed")
            File(globals,"${CharacterMasterUpgrade.LATEST_FEMALE_REVISION}.installed").writeText("installed")
            File(globals,"${CharacterMasterUpgrade.MAY_REVISION}.installed").writeText("installed")
            CharacterMasterUpgrade.ids.forEach { File(globals,"$it.master").writeText("previous $it") }
            val project=File(root,"studio/original").apply { mkdirs() }
            File(project,"${CharacterMasterUpgrade.REVISION}.installed").writeText("installed")
            File(project,"${CharacterMasterUpgrade.CURRENT_REVISION}.installed").writeText("installed")
            File(project,"${CharacterMasterUpgrade.FEMALE_REVISION}.installed").writeText("installed")
            File(project,"${CharacterMasterUpgrade.LATEST_FEMALE_REVISION}.installed").writeText("installed")
            File(project,"${CharacterMasterUpgrade.MAY_REVISION}.installed").writeText("installed")
            val story=File(project,"story.json").apply { writeText("""{"character_mode":"original","episodes":[{"scenes":[{"characters":[{"name":"กวิน"}],"flow_image_revision":5,"user_review":"approved"},{"characters":[{"name":"เมย์"}],"flow_image_revision":5,"user_review":"approved"}]}]}""") }
            CharacterMasterUpgrade.ids.forEach { File(project,"original_masters/$it.master").apply { parentFile!!.mkdirs(); writeText("snapshot $it") } }
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertTrue(File(globals,"${CharacterMasterUpgrade.FEMALE_2159_REVISION}.installed").exists())
            assertTrue(File(globals,"versions/${CharacterMasterUpgrade.FEMALE_2159_REVISION}").listFiles()!!.any { it.readText()=="previous mind" })
            assertEquals("previous gawin",File(globals,"gawin.master").readText())
            assertEquals("snapshot gawin",File(project,"original_masters/gawin.master").readText())
            assertEquals("character_masters/MIND_MASTER.png",File(globals,"mind.master").readText())
            assertEquals("character_masters/RINLADA_MASTER.png",File(project,"original_masters/rinlada.master").readText())
            val scenes=JSONObject(story.readText()).getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes")
            assertEquals("approved",scenes.getJSONObject(0).getString("user_review"))
            assertTrue(scenes.getJSONObject(0).has("flow_image_revision"))
            assertEquals("needs_revision",scenes.getJSONObject(1).getString("user_review"))
            assertFalse(scenes.getJSONObject(1).has("flow_image_revision"))
        } finally { root.deleteRecursively() }
    }
    @Test fun currentThreeMastersUpdateExistingAndPendingStoriesOnceWithOriginalsBackedUp() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val globals=File(root,"character_masters").apply { mkdirs() }
            listOf(CharacterMasterUpgrade.REVISION,CharacterMasterUpgrade.FEMALE_REVISION,CharacterMasterUpgrade.LATEST_FEMALE_REVISION,
                CharacterMasterUpgrade.MAY_REVISION,CharacterMasterUpgrade.FEMALE_2159_REVISION).forEach { File(globals,"$it.installed").writeText("installed") }
            val project=File(root,"studio/active").apply { mkdirs() }
            val pending=File(root,"studio/pending").apply { mkdirs() }
            File(pending,"gemini_job/spec.json").apply { parentFile!!.mkdirs(); writeText("{}") }
            val story=File(project,"story.json").apply { writeText("""{"character_mode":"original","episodes":[{"scenes":[{"characters":[{"name":"กวิน"},{"name":"รินรดา"},{"name":"เมย์"}],"flow_image_revision":5,"user_review":"approved"}]}]}""") }
            CharacterMasterUpgrade.ids.forEach { id ->
                File(globals,"$id.master").writeText("global old $id")
                listOf(project,pending).forEach { folder -> File(folder,"original_masters/$id.master").apply { parentFile!!.mkdirs(); writeText("old $id") } }
            }
            val media=File(project,"images/scene.jpg").apply { parentFile!!.mkdirs(); writeText("existing media") }
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            CharacterMasterUpgrade.ids.forEach { id ->
                val expected="character_masters/${id.uppercase()}_MASTER.png"
                assertEquals(expected,File(globals,"$id.master").readText())
                listOf(project,pending).forEach { folder ->
                    assertEquals(expected,File(folder,"original_masters/$id.master").readText())
                    assertTrue(File(folder,"versions/${CharacterMasterUpgrade.CURRENT_REVISION}").listFiles()!!.any { it.readText()=="old $id" })
                }
            }
            val scene=JSONObject(story.readText()).getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
            assertEquals("needs_revision",scene.getString("user_review")); assertFalse(scene.has("flow_image_revision"))
            assertEquals("existing media",media.readText())
            File(pending,"original_masters/mind.master").writeText("later user replacement")
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertEquals("later user replacement",File(pending,"original_masters/mind.master").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun selectedCastMigratesOnceWithBackupsAndLeavesMediaAndNewCharactersIntact() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val old=File(root,"character_masters/gawin.master").apply { parentFile!!.mkdirs(); writeText("old") }
            val project=File(root,"studio/original").apply { mkdirs() }
            val story=File(project,"story.json").apply { writeText("""{"character_mode":"original","episodes":[{"scenes":[{"characters":[{"name":"รินรดา"}],"flow_image_revision":5,"user_review":"approved"}]}]}""") }
            val snapshot=File(project,"original_masters/rinlada.master").apply { parentFile!!.mkdirs(); writeText("old snapshot") }
            val media=File(project,"images/EP01_SC001.png").apply { parentFile!!.mkdirs(); writeText("existing scene") }
            val fresh=File(root,"studio/new/story.json").apply { parentFile!!.mkdirs(); writeText("""{"character_mode":"new","episodes":[]}""") }
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertEquals("character_masters/GAWIN_MASTER.png",old.readText())
            assertEquals("character_masters/RINLADA_MASTER.png",snapshot.readText())
            val scene=JSONObject(story.readText()).getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0)
            assertEquals("needs_revision",scene.getString("user_review"))
            assertFalse(scene.has("flow_image_revision"))
            assertEquals("existing scene",media.readText())
            assertFalse(File(fresh.parentFile,"original_masters").exists())
            assertTrue(File(root,"character_masters/versions/${CharacterMasterUpgrade.REVISION}").listFiles()!!.any { it.readText()=="old" })
            assertTrue(File(project,"versions/${CharacterMasterUpgrade.REVISION}").listFiles()!!.any { it.readText()=="old snapshot" })
            old.writeText("later custom reference"); snapshot.writeText("later project reference")
            CharacterMasterUpgrade.install(root) { it.toByteArray() }
            assertEquals("later custom reference",old.readText())
            assertEquals("later project reference",snapshot.readText())
        } finally { root.deleteRecursively() }
    }
}
