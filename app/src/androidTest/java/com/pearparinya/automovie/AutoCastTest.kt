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
    @Test fun autoCastCanUseLegacyNamesWithTheirOwnCatalogMasters() {
        val catalog=JSONArray().put(JSONObject().put("id","CHAR_101")).put(JSONObject().put("id","CHAR_102")).put(JSONObject().put("id","CHAR_103"))
        val cast=JSONArray().put(JSONObject().put("library_id","CHAR_101").put("name","รินรดา").put("role","ผู้จัดการ"))
            .put(JSONObject().put("library_id","CHAR_102").put("name","เมย์").put("role","เพื่อน"))
            .put(JSONObject().put("library_id","CHAR_103").put("name","กวิน").put("role","เพื่อน"))
        AutoCast.validate(JSONObject().put("story_concept","เรื่องใหม่").put("cast",cast),catalog)
        fun person(name: String, master: String)=JSONObject().put("name",name).put("master",master).put("auto_cast",true).put("visual_description","independent reference")
        val rin=person("รินรดา","SUPPORT_00000065_MASTER.png")
        val may=person("เมย์","SUPPORT_00000066_MASTER.png")
        val gawin=person("กวิน","SUPPORT_00000067_MASTER.png")
        val plan=JSONObject().put("supporting_characters",JSONArray().put(rin).put(may).put(gawin))
        assertTrue(FlowCast.issues(plan).toString(),FlowCast.issues(plan).isEmpty())
        assertTrue(FlowCast.matches(plan,JSONObject(rin.toString()).removeAutoCast()))
        assertTrue(FlowCast.matches(plan,JSONObject(may.toString()).removeAutoCast()))
        assertTrue(FlowCast.matches(plan,JSONObject(gawin.toString()).removeAutoCast()))
        assertFalse(FlowCast.matches(plan,JSONObject(gawin.toString()).put("master","GAWIN_MASTER.png")))
        assertFalse(FlowCast.matches(plan,JSONObject(rin.toString()).put("master",may.getString("master"))))
        assertFalse(FlowCast.matches(plan,JSONObject(rin.toString()).put("master","RINLADA_MASTER.png")))
    }
    private fun JSONObject.removeAutoCast(): JSONObject { remove("auto_cast"); return this }
    @Test fun oldPairsAreRejectedButFreshCastingIsAllowed() {
        val catalog=JSONArray((1..35).map { JSONObject().put("id","CHAR_%03d".format(it)) })
        fun selection(vararg ids: Int)=JSONObject().put("story_concept","เรื่องใหม่").put("cast",JSONArray(ids.map { id -> JSONObject().put("library_id","CHAR_%03d".format(id)).put("name","บทที่$id").put("role","บทบาท$id") }))
        val recent=JSONArray().put(JSONArray(listOf("CHAR_005","CHAR_006","CHAR_023")))
        assertTrue(runCatching { AutoCast.validateVariety(selection(1,2,22),catalog,recent) }.isSuccess)
        assertTrue(runCatching { AutoCast.validateVariety(selection(5,6,30),catalog,recent) }.isSuccess)
        AutoCast.validateVariety(selection(7,12,30),catalog,recent)
        AutoCast.validateVariety(selection(1,7,30),catalog,recent)
    }
    @Test fun recentActorsAreRotatedWhenTheCatalogHasEnoughChoices() {
        val catalog=JSONArray((1..12).map { i -> JSONObject().put("id","CHAR_%03d".format(i)).put("character_gender",if(i%2==0) "man" else "woman").put("character_age",if(i%2==0) 28 else 48) })
        val recent=JSONArray().put(JSONArray(listOf("CHAR_001","CHAR_002")))
        val fresh=AutoCast.candidatePool(catalog,recent,"ทั่วไป")
        assertEquals(10,fresh.length())
        assertFalse((0 until fresh.length()).any { fresh.getJSONObject(it).getString("id")=="CHAR_001" })
        assertEquals(catalog.length(),AutoCast.candidatePool(catalog,JSONArray().put(JSONArray((1..6).map { "CHAR_%03d".format(it) })),"ทั่วไป").length())
        val noOlder=JSONArray((1..10).map { i -> JSONObject().put("id","CHAR_%03d".format(i)).put("character_gender",if(i%2==0) "man" else "woman").put("character_age",if(i==1) 48 else 28) })
        assertEquals(noOlder.length(),AutoCast.candidatePool(noOlder,JSONArray().put(JSONArray(listOf("CHAR_001"))),"รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่").length())
    }
    @Test fun rotationWorksWithFewerThanEightFreshActorsAndProtectsAgeRules() {
        val catalog=JSONArray((1..6).map { i -> JSONObject().put("id","CHAR_%03d".format(i)).put("character_gender",if(i%2==0) "man" else "woman").put("character_age",if(i%2==0) 28 else 48) })
        val recent=JSONArray().put(JSONArray(listOf("CHAR_001","CHAR_002"))).put(JSONArray(listOf("CHAR_003")))
        val fresh=AutoCast.rotationPool(catalog,recent,"ทั่วไป","seed")
        assertEquals(4,fresh.length())
        assertFalse((0 until fresh.length()).any { fresh.getJSONObject(it).getString("id") in listOf("CHAR_001","CHAR_002") })
        assertEquals("CHAR_003",fresh.getJSONObject(fresh.length()-1).getString("id"))
        val small=JSONArray((1..3).map { catalog.getJSONObject(it-1) })
        val fallback=AutoCast.rotationPool(small,recent,"ทั่วไป","seed")
        assertEquals(3,fallback.length())
        val ageCatalog=JSONArray().put(JSONObject().put("id","CHAR_001").put("character_gender","woman").put("character_age",48)).put(JSONObject().put("id","CHAR_002").put("character_gender","man").put("character_age",28)).put(JSONObject().put("id","CHAR_003").put("character_gender","woman").put("character_age",30)).put(JSONObject().put("id","CHAR_004").put("character_gender","man").put("character_age",28))
        assertEquals(4,AutoCast.rotationPool(ageCatalog,recent,"รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่","seed").length())
    }
    @Test fun recentCastsUseFileTimesAndFallbackWhenCachedCastIsBroken() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val a=File(root,"a").apply { mkdirs() }; val b=File(root,"b").apply { mkdirs() }; val current=File(root,"current").apply { mkdirs() }
            File(a,"gemini_job").mkdirs(); File(a,"gemini_job/casting.json").writeText("broken")
            val first=File(a,"story.json").apply { writeText(JSONObject().put("supporting_characters",JSONArray().put(JSONObject().put("library_id","CHAR_001"))).toString()); setLastModified(10000) }
            File(b,"gemini_job").mkdirs(); File(b,"gemini_job/casting.json").apply { writeText(JSONObject().put("cast",JSONArray().put(JSONObject().put("library_id","CHAR_002"))).toString()); setLastModified(20000) }
            a.setLastModified(30000); b.setLastModified(1000)
            val history=AutoCast.recentCasts(root,current)
            assertEquals("CHAR_002",history.getJSONArray(0).getString(0)); assertEquals("CHAR_001",history.getJSONArray(1).getString(0))
            assertTrue(first.isFile)
        } finally { root.deleteRecursively() }
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
