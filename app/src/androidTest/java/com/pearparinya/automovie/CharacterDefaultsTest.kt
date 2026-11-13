package com.pearparinya.automovie

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CharacterDefaultsTest {
    @Test fun installsCorrectDefaultsWithoutReplacingExistingMasters() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.filesDir,"character_masters").apply { mkdirs() }
        val ids=listOf("rinlada","mind")
        val files=ids.map { File(dir,"$it.master") }
        val before=files.map { if(it.exists()) it.readBytes() else null }
        try {
            files.forEach { it.delete() }
            ActivityScenario.launch(StudioActivity::class.java).use { }
            ids.forEachIndexed { i,id ->
                assertArrayEquals(context.assets.open("character_defaults/$id.jpg").use { it.readBytes() },files[i].readBytes())
            }
            val custom="existing-user-master".toByteArray()
            files[0].writeBytes(custom)
            ActivityScenario.launch(StudioActivity::class.java).use { }
            assertArrayEquals(custom,files[0].readBytes())
        } finally {
            files.forEachIndexed { i,file -> if(before[i]==null) file.delete() else file.writeBytes(before[i]!!) }
        }
    }
}
