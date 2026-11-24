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
        val files = mapOf(
            File(context.filesDir, "studio/$id/story.json") to """{"title":"$id","episodes":[]}""".toByteArray(),
            File(context.filesDir, "character_masters/$id.master") to id.toByteArray()
        )
        if (upgradeBuild != null) {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            assertEquals(upgradeBuild.toLong(), info.longVersionCode)
        }
        if (upgradeBuild != "1246") {
            files.forEach { (file, bytes) ->
                file.parentFile!!.mkdirs()
                assertTrue(file.createNewFile())
                file.writeBytes(bytes)
            }
        }
        files.forEach { (file, expected) -> assertArrayEquals(expected, file.readBytes()) }
        if (upgradeBuild != "1245") {
            files.keys.forEach { it.delete() }
            File(context.filesDir, "studio/$id").delete()
        }
    }
}
