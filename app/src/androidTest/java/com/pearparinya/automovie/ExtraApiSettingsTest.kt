package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExtraApiSettingsTest {
    @Test fun keysAreEncryptedIndependentAndDeletable() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val providers=ExtraApiProvider.entries
        val files=providers.associateWith { File(context.noBackupFilesDir,"${it.id}/settings.json") }
        val previous=files.mapValues { (_,file) -> file.takeIf { it.exists() }?.readBytes() }
        try {
            providers.forEachIndexed { i, provider ->
                val key="test-only-fake-key-for-provider-$i"
                val settings=ExtraApiSettings(context,provider)
                settings.save(key)
                assertTrue(settings.hasKey())
                assertEquals(key,settings.key())
                assertEquals(provider.service.defaultModel,settings.model())
                assertFalse(files.getValue(provider).readText().contains(key))
            }
            ExtraApiSettings(context,ExtraApiProvider.DEEPSEEK).clear()
            assertFalse(ExtraApiSettings(context,ExtraApiProvider.DEEPSEEK).hasKey())
            assertTrue(ExtraApiSettings(context,ExtraApiProvider.CEREBRAS).hasKey())
            assertTrue(ExtraApiSettings(context,ExtraApiProvider.OPENROUTER).hasKey())
            val router=ExtraApiSettings(context,ExtraApiProvider.OPENROUTER)
            val oldKey=router.key()
            router.save(oldKey,"example/model:free")
            assertEquals(oldKey,router.key())
            assertEquals("example/model:free",router.model())
            assertTrue(runCatching { router.save(oldKey,"example/paid") }.isFailure)
            assertEquals(oldKey,router.key())
            assertTrue(runCatching { ExtraApiSettings(context,ExtraApiProvider.CEREBRAS).save("short") }.isFailure)
            assertTrue(ExtraApiSettings(context,ExtraApiProvider.CEREBRAS).hasKey())
        } finally {
            files.forEach { (provider,file) -> previous[provider]?.let { file.parentFile!!.mkdirs(); file.writeBytes(it) }
                ?: ExtraApiSettings(context,provider).clear() }
        }
    }
}
