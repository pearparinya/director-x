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
        val providers=ExtraApiProvider.activeProviders
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
            val router=ExtraApiSettings(context,ExtraApiProvider.OPENROUTER)
            val oldKey=router.key()
            router.save(oldKey,"example/model:free")
            assertEquals(oldKey,router.key())
            assertEquals("example/model:free",router.model())
            assertTrue(runCatching { router.save(oldKey,"example/paid") }.isFailure)
            assertEquals(oldKey,router.key())
            assertTrue(runCatching { router.save("short") }.isFailure)
            assertTrue(router.hasKey())
            for (provider in listOf(ExtraApiProvider.CEREBRAS,ExtraApiProvider.DEEPSEEK,ExtraApiProvider.SAMBANOVA,ExtraApiProvider.MISTRAL)) {
                assertTrue(runCatching { ExtraApiSettings(context,provider) }.isFailure)
            }
        } finally {
            files.forEach { (provider,file) ->
                val old=previous[provider]
                if(old!=null) { file.parentFile!!.mkdirs(); file.writeBytes(old) }
                else ExtraApiSettings(context,provider).clear()
            }
        }
    }
}
