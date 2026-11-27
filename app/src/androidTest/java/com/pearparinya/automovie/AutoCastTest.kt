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
    @Test fun storyRegistryCarriesReviewedRolesWithoutInventingLegacyData() {
        val legacy = JSONObject().put("name", "เปีย").put("master", "PIA_MASTER.png")
        val actor = JSONObject().put("name", "อร").put("master", "SUPPORT_00000001_MASTER.png")
            .put("library_id", "CHAR_002").put("role", "พยาน")
        val rows = FlowCast.storyRegistry(listOf(legacy, actor))
        assertFalse(rows.getJSONObject(0).has("role"))
        assertFalse(rows.getJSONObject(0).has("library_id"))
        assertEquals("CHAR_002", rows.getJSONObject(1).getString("library_id"))
        assertEquals("พยาน", rows.getJSONObject(1).getString("role"))
        assertEquals(CharacterAliasRegistry.identityId("อร", actor.getString("master")), rows.getJSONObject(1).getString("character_id"))
    }

    @Test fun scaledDramaRepairsOnlyInvalidProposalAndRetainsPinnedMaster() {
        for (scenes in listOf(8, 20, 50, 100)) {
            val size = StoryCastPolicy.recommended(scenes).last
            val catalog = JSONArray()
            val cast = JSONArray()
            for (index in 1..size) {
                val id = "id_$index"
                catalog.put(JSONObject().put("id", id).put("display_name", "นักแสดง $index").put("character_type", "human"))
                cast.put(JSONObject().put("library_id", id).put("name", "คนที่ $index").put("role", "ผู้พบหลักฐาน $index"))
            }
            val frozen = JSONArray()
            for (index in 1..size) {
                frozen.put(JSONObject().put("auto_cast", true).put("name", "คนที่ $index")
                    .put("master", "SUPPORT_%08X_MASTER.png".format(index)).put("visual_description", "ภาพที่ $index"))
            }
            assertTrue(FlowCast.issues(JSONObject().put("supporting_characters", frozen)).isEmpty())
            val pinned = setOf("id_1")
            val premise = "นักแสดง 1 พบ นักแสดง 2"
            val local = AutoCast.kotlinProposal(catalog, premise, pinned, ensemble = true)
            assertEquals(2, local.getJSONArray("cast").length())
            var attempts = 0
            val result = AutoCast.propose(StoryCastPolicy.sizingGuide(scenes), catalog, "", { false }, pinned, premise, true) { request ->
                attempts++
                if (attempts == 2) assertTrue(request.contains("id_1"))
                JSONObject().put("story_concept", premise).put("cast", if (attempts == 1) JSONArray().put(cast.getJSONObject(0)) else cast)
            }
            assertEquals(2, attempts)
            assertEquals(size, result.getJSONArray("cast").length())
        }
    }

    @Test fun castingBriefPreservesPinnedActorsAndAvoidsAppearanceBasedPersonality() {
        val brief=AutoCast.castingBrief("ละครสั้นสะท้อนสังคม","CHAR_001",setOf("CHAR_002","CHAR_001"))
        assertTrue(brief.contains("central protagonist"))
        assertTrue(brief.contains("CHAR_001, CHAR_002"))
        assertTrue(brief.contains("FIRST identify the essential story roles"))
        assertTrue(brief.contains("Do not infer personality"))
        assertTrue(brief.contains("smallest credible cast"))
    }
    @Test fun romanceCastingBriefRetainsAgeGapAndRoleConstraints() {
        val younger=AutoCast.castingBrief("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่","",emptySet())
        val older=AutoCast.castingBrief("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง","",emptySet())
        assertTrue(younger.contains("younger adult man"))
        assertTrue(younger.contains("15 years"))
        assertTrue(older.contains("older adult man"))
        assertTrue(older.contains("15 years"))
    }
    @Test fun ageGapCastingRejectsChildrenAndNonhumanEvenWhenChronologicallyOld() {
        for(type in listOf("child", "animal", "creature", "deity")) {
            assertFalse(AutoCast.adultRomanceEligible(JSONObject().put("character_type",type).put("character_age",1000)))
        }
        assertFalse(AutoCast.adultRomanceEligible(JSONObject().put("character_type","human").put("character_age",17)))
        assertTrue(AutoCast.adultRomanceEligible(JSONObject().put("character_type","human").put("character_age",30)))
        assertTrue(AutoCast.adultRomanceEligible(JSONObject().put("character_age",30)))
    }
    @Test fun promptCatalogKeepsIdentityAndCastingFieldsWithoutFileMetadata() {
        val original=JSONArray().put(JSONObject().put("id","CHAR_101").put("display_name","รินรดา")
            .put("character_age",30).put("character_gender","woman").put("casting_description","long hair")
            .put("file","original.png").put("sha256","private-hash").put("width",937))
            .put(JSONObject().put("id","CHAR_102").put("display_name","เมย์").put("character_age",29).put("character_gender","woman"))
        val before=original.toString()
        val compact=AutoCast.promptCatalog(original)
        assertEquals(2,compact.length()); assertEquals("CHAR_101",compact.getJSONObject(0).getString("id"))
        assertEquals("CHAR_102",compact.getJSONObject(1).getString("id"))
        assertEquals(30,compact.getJSONObject(0).getInt("character_age"))
        assertEquals("long hair",compact.getJSONObject(0).getString("casting_description"))
        assertFalse(compact.getJSONObject(0).has("file")); assertFalse(compact.getJSONObject(0).has("sha256"))
        assertEquals(before,original.toString())
    }
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
    @Test fun conflictingOriginalIsPreservedBeforeAnyNewMasterIsWritten() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val source=File(root,"master_candidates/a.png").apply { parentFile!!.mkdirs(); writeText("registered") }
            val catalog=JSONArray()
            val cast=JSONArray()
            for(id in listOf("CHAR_001","CHAR_002")) {
                catalog.put(JSONObject().put("id",id).put("file","a.png").put("sha256",MasterLibrary.digest(source.readBytes())))
                cast.put(JSONObject().put("library_id",id).put("name",id).put("role","ตัวละคร"))
            }
            val original=File(root,"original_masters/support_00000002.master").apply { parentFile!!.mkdirs(); writeText("existing") }
            assertTrue(runCatching { AutoCast.apply(JSONObject().put("story_concept","เรื่อง").put("cast",cast),catalog,root) }.isFailure)
            assertEquals("existing",original.readText())
            assertFalse(File(root,"original_masters/support_00000001.master").exists())
        } finally { root.deleteRecursively() }
    }
}
