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
class MasterLibraryTest {
    @Test fun bundledCatalogKeepsNewWomenAndMenWithoutOldWomenOrDuplicateImages() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val rows=JSONObject(context.assets.open("master_library/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
        val entries=(0 until rows.length()).map { rows.getJSONObject(it) }
        val women=entries.filter { it.getString("character_gender")=="woman" }
        assertEquals((146..152).map { "CHAR_%03d".format(it) },women.map { it.getString("id") })
        assertEquals(17,entries.count { it.getString("character_gender")=="man" })
        val hashes=entries.map { row ->
            val bytes=context.assets.open("master_library/"+row.getString("file")).use { it.readBytes() }
            assertEquals(row.getString("sha256"),MasterLibrary.digest(bytes))
            MasterLibrary.digest(bytes)
        }
        assertEquals(hashes.size,hashes.distinct().size)
        assertTrue(runCatching { context.assets.open("master_library/CHAR_101__1000030761.jpg").close() }.isFailure)
    }
    @Test fun allBundledReferencesDecodeAndRetainTheirOriginalBytes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,UUID.randomUUID().toString())
        try {
            val library=MasterLibrary(root) { TestMasterAssets.open(it).use { stream -> stream.readBytes() } }
            val entries=library.entries()
            assertEquals((1..35).map { "CHAR_%03d".format(it) },entries.map { it.id })
            entries.forEach { entry ->
                val source=library.source(entry)
                assertEquals(entry.sha256,MasterLibrary.digest(source.readBytes()))
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                android.graphics.BitmapFactory.decodeFile(source.path,bounds)
                assertTrue(bounds.outWidth>0 && bounds.outHeight>0)
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun assignmentBacksUpOldMasterAndDoesNotChangeStorySnapshots() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val library=MasterLibrary(File(root,"library")) { TestMasterAssets.open(it).use { stream -> stream.readBytes() } }
            val entry=library.entries().first()
            val target=File(root,"character_masters/gawin.master").apply { parentFile!!.mkdirs(); writeText("old") }
            val story=File(root,"story/original_masters/gawin.master").apply { parentFile!!.mkdirs(); writeText("story snapshot") }
            library.assign(entry,target)
            assertEquals("old",File(target.parentFile,"gawin.master.previous").readText())
            assertEquals(entry.sha256,MasterLibrary.digest(target.readBytes()))
            assertEquals("story snapshot",story.readText())
            library.source(entry).writeText("corrupt cache")
            assertEquals(entry.sha256,MasterLibrary.digest(library.source(entry).readBytes()))
        } finally { root.deleteRecursively() }
    }
    @Test fun newReferenceDescriptionsReplaceLegacyAppearanceOnlyInSelectedSlots() {
        val selected=JSONObject().put("lead_references",JSONObject().put("gawin",
            JSONObject().put("library_id","CHAR_022").put("visual_description",MasterLibrary.description("CHAR_022"))))
        val cast=FlowCast.definitions(selected)
        assertTrue(cast.first().getString("visual_description").contains("CHAR_022"))
        assertEquals(FlowProductionPack.dna.getValue("rinlada"),cast[1].getString("visual_description"))
        assertEquals(FlowProductionPack.dna.getValue("gawin"),FlowCast.definitions(null).first().getString("visual_description"))
    }
}
