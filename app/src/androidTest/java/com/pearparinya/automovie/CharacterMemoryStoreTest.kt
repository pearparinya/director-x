package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CharacterMemoryStoreTest {
    @Test fun upgradedBundleAddsMissingFieldsWithoutOverwritingEditedValues() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val master=File(root,"reference").apply { writeText("unchanged master") }
            val folder=File(root,"memory")
            CharacterMemoryStore(folder,JSONObject()).save("mind",master,JSONObject().put("face","user face").put("body",""))
            val profile=JSONObject().put("character_id","mind").put("sha256",EpisodeMedia.digest(master)).put("face","bundled face").put("body","bundled body").put("reference_limits","hidden legs")
            val store=CharacterMemoryStore(folder,JSONObject().put("profiles",org.json.JSONArray().put(profile)))
            assertEquals("user face",store.read("mind",master).getString("face"))
            assertEquals("",store.read("mind",master).getString("body"))
            assertEquals("hidden legs",store.read("mind",master).getString("reference_limits"))
        } finally { root.deleteRecursively() }
    }
    @Test fun savedProfilesAreBoundToReferenceAndCharacterAcrossRestarts() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val master=File(root,"reference").apply { writeText("source A") }
            val folder=File(root,"memory")
            CharacterMemoryStore(folder,JSONObject()).save("rinlada",master,JSONObject().put("face","ใบหน้าอ้างอิง A").put("chest_waist","ข้อมูลสัดส่วน A").put("reference_limits","ไม่เห็นขาครบ"))
            val reopened=CharacterMemoryStore(folder,JSONObject())
            assertEquals("ใบหน้าอ้างอิง A",reopened.read("rinlada",master).optString("face"))
            assertTrue(reopened.brief("rinlada",master).contains("ข้อมูลสัดส่วน A"))
            assertTrue(reopened.brief("rinlada",master).contains("ไม่เห็นขาครบ"))
            assertEquals("",reopened.read("mind",master).optString("face"))
            master.writeText("source B")
            assertEquals("",reopened.read("rinlada",master).optString("face"))
        } finally { root.deleteRecursively() }
    }
    @Test fun emptyLibraryHasNoStaleBundledProfiles() {
        val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets
        val bundle=JSONObject(assets.open("character_profiles.json").bufferedReader().use { it.readText() }).getJSONArray("profiles")
        assertEquals(0,bundle.length())
        assertTrue(assets.list("character_masters").orEmpty().isEmpty())
    }
}
