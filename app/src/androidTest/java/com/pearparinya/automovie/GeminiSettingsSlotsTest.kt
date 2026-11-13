package com.pearparinya.automovie

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiSettingsSlotsTest {
    @Test fun additionalSlotsPreserveOriginalAndEncryptKeys() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=base.cacheDir.resolve("gemini-slots-test").apply { deleteRecursively(); mkdirs() }
        val context=object: ContextWrapper(base) {
            override fun getNoBackupFilesDir(): java.io.File = root
        }
        try {
            val one=GeminiSettings(context,StoryProvider.GEMINI,0)
            val two=GeminiSettings(context,StoryProvider.GEMINI,1)
            val five=GeminiSettings(context,StoryProvider.GEMINI,4)
            val key1="test-key-slot-one-0123456789"
            val key2="test-key-slot-two-0123456789"
            val key5="test-key-slot-five-0123456789"
            one.save(key1,"gemini-test-model")
            two.save(key2,"gemini-test-model")
            five.save(key5,"gemini-test-model")
            assertEquals(key1,one.key())
            assertEquals(listOf(1,2,5),GeminiSettings.configured(context).map { it.first })
            assertFalse(root.resolve("gemini/settings.json").readText().contains(key1))
            assertFalse(root.resolve("gemini/settings_2.json").readText().contains(key2))
            two.clear()
            assertEquals(key1,one.key())
            assertEquals(key5,five.key())
            assertEquals(listOf(1,5),GeminiSettings.configured(context).map { it.first })
        } finally { root.deleteRecursively() }
    }
}
