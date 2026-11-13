package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FantasyMasterCatalogTest {
    @Test fun everyMasterHasUniqueIdAndUnchangedImageBytes() {
        val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets
        val rows=JSONObject(assets.open("fantasy_masters/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
        assertEquals(59,rows.length())
        val ids=mutableSetOf<String>()
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            assertTrue(ids.add(row.getString("id")))
            assertTrue(CharacterAge.valid(row.getInt("character_age")))
            val bytes=assets.open("fantasy_masters/"+row.getString("file")).use { it.readBytes() }
            assertEquals(row.getString("sha256"),MasterLibrary.digest(bytes))
        }
        assertEquals("1000033209.png",rows.getJSONObject(0).getString("original_filename"))
        assertEquals("1000033234.png",rows.getJSONObject(25).getString("original_filename"))
        assertEquals("1000033248.png",rows.getJSONObject(39).getString("original_filename"))
    }
}
