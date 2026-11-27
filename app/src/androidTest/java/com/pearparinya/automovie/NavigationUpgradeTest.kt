package com.pearparinya.automovie

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class NavigationUpgradeTest {
    @Test fun retainsProjectAndMasterBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val upgradeBuild = args.getString("build")
        val id = UUID.fromString(args.getString("fixture") ?: UUID.randomUUID().toString()).toString()
        val approvedOutfit = CastOutfit(id, "เสื้อลายไทย")
        val project = File(context.filesDir, "studio/$id")
        val files = mapOf(
            approvedOutfit.file(project) to android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",0),
            File(context.filesDir, "studio/$id/story.json") to """{"title":"$id","episodes":[{"ep":1,"title":"$id","scenes":[{"dialogue":"$id"}]}]}""".toByteArray(),
            File(context.filesDir, "character_masters/$id.master") to id.toByteArray(),
            File(context.filesDir, "studio/$id/images/fixture.png") to android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",0)
        )
        if (upgradeBuild != null) {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            assertEquals(upgradeBuild.toLong(), info.longVersionCode)
        }
        if (args.getString("phase") == "seed" || (args.getString("phase") == null && upgradeBuild != "1246")) {
            assertTrue(context.getSharedPreferences("studio",0).edit().putString("upgrade_fixture_$id",id).commit())
            files.forEach { (file, bytes) ->
                file.parentFile!!.mkdirs()
                assertTrue(file.createNewFile())
                file.writeBytes(bytes)
            }
        }
        assertEquals(id,context.getSharedPreferences("studio",0).getString("upgrade_fixture_$id",null))
        files.forEach { (file, expected) -> assertArrayEquals(expected, file.readBytes()) }
        assertEquals(approvedOutfit.file(project).canonicalPath, CastOutfit(id, "เสื้อลายไทย").attachment(project)?.second?.canonicalPath)
        if (args.getString("phase") == "verify" || (args.getString("phase") == null && upgradeBuild != "1245")) {
            files.keys.forEach { it.delete() }
            File(context.filesDir, "studio/$id").deleteRecursively()
            context.getSharedPreferences("studio",0).edit().remove("upgrade_fixture_$id").commit()
        }
    }
}
