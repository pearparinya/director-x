package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PiaMasterUpdateTest {
    @Test fun replacesIdentityAndRetainsOriginalBackupAndProjectSnapshot() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.cacheDir,"pia-update-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val target=File(dir,"pia_identity_master.image").apply { writeText("old identity") }
            val snapshot=File(dir,"project/identity.image").apply { parentFile!!.mkdirs(); writeText("saved project") }
            val image=context.assets.open("pia_identity_431.png").use { it.readBytes() }
            PiaMasterUpdate.install(target,image)
            assertArrayEquals(image,target.readBytes())
            assertEquals("old identity",File(dir,"pia_identity_before_431.image").readText())
            assertEquals("saved project",snapshot.readText())
            PiaMasterUpdate.install(target,image)
            assertEquals("old identity",File(dir,"pia_identity_before_431.image").readText())
        } finally { dir.deleteRecursively() }
    }
}
