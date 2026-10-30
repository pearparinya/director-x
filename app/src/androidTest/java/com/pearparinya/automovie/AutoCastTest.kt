package com.pearparinya.automovie
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class AutoCastTest {
    @Test fun oldPairsAreRejectedButFreshCastingIsAllowed() {
        val catalog=JSONArray((1..35).map { JSONObject().put("id","CHAR_%03d".format(it)) })
        fun selection(vararg ids: Int)=JSONObject().put("story_concept","เรื่องใหม่").put("cast",JSONArray(ids.map { id -> JSONObject().put("library_id","CHAR_%03d".format(id)).put("name","บทที่$id").put("role","บทบาท$id") }))
        val recent=JSONArray().put(JSONArray(listOf("CHAR_005","CHAR_006","CHAR_023")))
        assertTrue(runCatching { AutoCast.validateVariety(selection(1,2,22),catalog,recent) }.isFailure)
        assertTrue(runCatching { AutoCast.validateVariety(selection(5,6,30),catalog,recent) }.isFailure)
        AutoCast.validateVariety(selection(7,12,30),catalog,recent)
        AutoCast.validateVariety(selection(1,7,30),catalog,recent)
    }
    @Test fun castingFreezesNamesAndReferencesWithoutLegacyLeads() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val catalog=JSONObject(TestMasterAssets.open("master_library/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
            for(i in listOf(1,21)) {
                val row=catalog.getJSONObject(i); val dest=File(root,"master_candidates/"+row.getString("file"));dest.parentFile!!.mkdirs()
                TestMasterAssets.open("master_library/"+row.getString("file")).use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
            val value=JSONObject().put("story_concept","ความลับในบริษัท").put("cast",JSONArray()
                .put(JSONObject().put("library_id","CHAR_022").put("name","ภาคิน").put("role","เจ้าของบริษัท"))
                .put(JSONObject().put("library_id","CHAR_002").put("name","นลิน").put("role","ผู้ตรวจสอบ")))
            val cast=AutoCast.apply(value,catalog,root)
            val plan=JSONObject().put("supporting_characters",cast)
            assertEquals(28,cast.getJSONObject(0).getInt("character_age"))
            assertTrue(cast.getJSONObject(0).getString("visual_description").contains("28 years"))
            assertEquals(2,FlowCast.definitions(plan).size)
            assertEquals("ภาคิน",FlowCast.definitions(plan).first().getString("name"))
            assertTrue(FlowCast.issues(plan).isEmpty())
            assertTrue(FlowCast.matches(plan,cast.getJSONObject(0)))
            val first=File(root,"original_masters/support_00000016.master").readBytes()
            assertEquals(cast.toString(),AutoCast.apply(value,catalog,root).toString())
            assertArrayEquals(first,File(root,"original_masters/support_00000016.master").readBytes())
            val bad=JSONObject(value.toString());bad.getJSONArray("cast").getJSONObject(1).put("library_id","CHAR_022")
            assertTrue(runCatching { AutoCast.validate(bad,catalog) }.isFailure)
            assertEquals(3,FlowCast.definitions(null).size)
        } finally { root.deleteRecursively() }
    }
}
