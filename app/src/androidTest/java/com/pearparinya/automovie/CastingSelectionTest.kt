package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CastingSelectionTest {
    @Test fun storyRankingKeepsAllTypesAndPinnedIdentity() {
        val rows=JSONArray().put(JSONObject().put("id","H").put("character_type","human"))
            .put(JSONObject().put("id","G").put("character_type","ghost").put("display_name","วิญญาณหญิงชุดสีอ่อน"))
            .put(JSONObject().put("id","N").put("character_type","animal").put("display_name","พญานาค"))
        val snapshot=rows.toString()
        assertEquals("G",AutoCast.storyCatalog(rows,"ผีสาวผมยาว").getJSONObject(0).getString("id"))
        assertEquals("N",AutoCast.storyCatalog(rows,"ตำนานพญานาค").getJSONObject(0).getString("id"))
        val pinned=AutoCast.storyCatalog(rows,"ผีสาวผมยาว",setOf("H"))
        assertEquals("H",pinned.getJSONObject(0).getString("id"))
        assertEquals("G",pinned.getJSONObject(1).getString("id"))
        assertEquals(3,pinned.length())
        assertEquals(snapshot,rows.toString())
    }
    @Test fun evidenceScoreAndSpeciesValidation() {
        val naga=JSONObject().put("id","N").put("character_type","animal").put("display_name","พญานาค ผู้พิทักษ์")
        val dragon=JSONObject().put("id","D").put("character_type","animal").put("display_name","มังกร")
        val catalog=JSONArray().put(dragon).put(naga)
        assertEquals(55,AutoCast.matchingScore(naga,"พญานาค ผู้พิทักษ์"))
        assertEquals(30,AutoCast.matchingScore(dragon,"พญานาค ผู้พิทักษ์"))
        assertEquals(0,AutoCast.matchingScore(naga,"เรื่องครอบครัว"))
        val actor=JSONObject().put("library_id","D").put("role","พญานาค")
        val proposal=JSONObject().put("cast",JSONArray().put(actor))
        assertTrue(runCatching { AutoCast.validateStoryRoles(proposal,catalog) }.isFailure)
        actor.put("library_id","N")
        AutoCast.validateStoryRoles(proposal,catalog)
        actor.put("library_id","D")
        AutoCast.validateStoryRoles(proposal,catalog,setOf("D"))
    }
    @Test fun offlineCastingUsesActualIdsAndRejectsMissingSpecies() {
        val rows=JSONArray()
            .put(JSONObject().put("id","H").put("character_type","human").put("display_name","ตัวเอก"))
            .put(JSONObject().put("id","G").put("character_type","ghost").put("display_name","วิญญาณหญิงผมยาว"))
            .put(JSONObject().put("id","N").put("character_type","animal").put("display_name","พญานาค"))
        val result=AutoCast.kotlinProposal(rows,"ผีสาวผมยาว พบพญานาค",setOf("H"))
        val cast=result.getJSONArray("cast")
        assertEquals(3,cast.length())
        assertEquals("H",cast.getJSONObject(0).getString("library_id"))
        assertEquals(result.toString(),AutoCast.kotlinProposal(rows,"ผีสาวผมยาว พบพญานาค",setOf("H")).toString())
        assertTrue(runCatching { AutoCast.kotlinProposal(rows,"มังกร",setOf("H")) }.isFailure)
    }
    @Test fun explicitPoolNeverExpandsAndEmptyPreservesAll() {
        val rows=JSONArray().put(JSONObject().put("id","A")).put(JSONObject().put("id","B")).put(JSONObject().put("id","C"))
        assertEquals(3,CastingSelection.filter(rows,emptySet()).length())
        val selected=CastingSelection.filter(rows,setOf("A","C"))
        assertEquals(2,selected.length())
        assertEquals("A",selected.getJSONObject(0).getString("id"))
        assertEquals("C",selected.getJSONObject(1).getString("id"))
        assertTrue(runCatching { CastingSelection.filter(rows,setOf("MISSING")) }.isFailure)
        val cast=JSONArray().put(JSONObject().put("library_id","A"))
        CastingSelection.requireIncluded(cast,setOf("A"))
        assertTrue(runCatching { CastingSelection.requireIncluded(cast,setOf("A","C")) }.isFailure)
    }
    @Test fun offlineFamilyRolesUseRegisteredAgeAndGender() {
        fun row(id:String, gender:String, age:Int)=JSONObject().put("id",id).put("display_name",id).put("character_type","human").put("character_gender",gender).put("character_age",age)
        val catalog=JSONArray().put(row("F","man",40)).put(row("M","woman",38)).put(row("C","woman",10))
        val cast=AutoCast.kotlinProposal(catalog,"พ่อ แม่ และเด็กหญิง",setOf("F")).getJSONArray("cast")
        assertEquals(3,cast.length())
        assertEquals("F",cast.getJSONObject(0).getString("library_id"))
        assertEquals("พ่อ",cast.getJSONObject(0).getString("role"))
        assertTrue(runCatching { AutoCast.kotlinProposal(catalog,"เด็กชาย") }.isFailure)
        assertEquals(1,AutoCast.kotlinProposal(catalog,"กล่าวถึงแม่",setOf("F")).getJSONArray("cast").length())
    }
    @Test fun explicitChildAndGhostRolesDoNotCrossTypes() {
        fun row(id:String,type:String,name:String)=JSONObject().put("id",id).put("character_type",type).put("display_name",name)
        val catalog=JSONArray().put(row("C","child","เด็กหญิง").put("character_age",10).put("character_gender","woman"))
            .put(row("G","ghost","วิญญาณชายชรา")).put(row("K","ghost","ผีกระสือ"))
            .put(row("H","human","กระสือ").put("character_age",70).put("character_gender","man"))
        fun cast(premise:String)=AutoCast.kotlinProposal(catalog,premise).getJSONArray("cast")
        assertEquals("C",cast("เด็กหญิง").getJSONObject(0).getString("library_id"))
        assertEquals(1,cast("ผีชายชรา").length())
        assertEquals("K",cast("ผีกระสือ").getJSONObject(0).getString("library_id"))
        assertTrue(runCatching { AutoCast.kotlinProposal(catalog,"พ่อค้า") }.isFailure)
    }
    @Test fun fatherMentioningAbsentGhostIsTheOnlyVisibleActor() {
        val catalog = JSONArray().put(JSONObject().put("id", "F").put("character_type", "human")
            .put("character_age", 40).put("character_gender", "man"))
        for (premise in listOf("พ่อกล่าวถึงผีสาว", "พ่อไม่พบพญานาค")) {
            val cast = AutoCast.kotlinProposal(catalog, premise).getJSONArray("cast")
            assertEquals(1, cast.length())
            assertEquals("F", cast.getJSONObject(0).getString("library_id"))
        }
    }
    @Test fun scoreBreakdownRequiresEvidenceForEachBucket() {
        val row = JSONObject().put("id", "N").put("character_type", "animal")
            .put("display_name", "พญานาค ผมขาว").put("casting_description", "ตำนาน เมืองเก่า")
        val criteria = AutoCast.matchingEvidence(row, "พญานาคผมขาว", "ตำนาน", "เมืองเก่า", setOf("N"))
        assertEquals(listOf(30, 25, 20, 10, 10, 5), criteria.map { it.points })
        assertEquals(100, criteria.sumOf { it.points })
        assertTrue(AutoCast.matchingEvidence(row, "ไม่พบพญานาค").all { it.points == 0 })
    }
    @Test fun confirmedOfflineCastHonorsManualRemovalAndRegisteredNames() {
        val rows = JSONArray().put(JSONObject().put("id","A").put("display_name","อรุณ"))
            .put(JSONObject().put("id","B").put("display_name","มาลี"))
        val roles = JSONObject().put("A","ผู้พิทักษ์").put("B","ผู้ช่วย")
        val cast = AutoCast.confirmedKotlinCast(rows, setOf("A"), roles, "อรุณเฝ้าประตู", "").getJSONArray("cast")
        assertEquals(1, cast.length())
        assertEquals("อรุณ", cast.getJSONObject(0).getString("name"))
        assertEquals("ผู้พิทักษ์", cast.getJSONObject(0).getString("role"))
    }
    @Test fun explicitFamilyAliasUsesOneMasterWhileSeparatePeopleUseTwo() {
        val rows = JSONArray()
        for (id in listOf("A", "B")) rows.put(JSONObject().put("id",id).put("display_name",id)
            .put("character_type","human").put("character_age",40).put("character_gender","man"))
        fun cast(text: String) = AutoCast.kotlinProposal(rows,text).getJSONArray("cast")
        val linked = cast("พ่อซึ่งเป็นสามีเดินกลับบ้าน")
        assertEquals(1, linked.length())
        assertEquals("พ่อ / สามี", linked.getJSONObject(0).getString("role"))
        assertEquals(2, cast("พ่อพบกับสามีของเพื่อน").length())
        assertEquals(1, cast("พ่อพบพ่อค้า").length())
    }

    @Test fun labelledStorySettingChangesRankingWithoutExplicitParameter() {
        fun actor(id: String, description: String) = JSONObject().put("id",id).put("casting_description",description)
        val rows = JSONArray().put(actor("A","ป่าดิบ")).put(actor("B","เมืองเก่า"))
        val story = "ผู้พิทักษ์กลับบ้าน\nสถานที่: เมืองเก่า"
        assertEquals("B",AutoCast.storyCatalog(rows,story).getJSONObject(0).getString("id"))
        assertEquals(10,AutoCast.matchingEvidence(rows.getJSONObject(1),story)[4].points)
        assertEquals(0,AutoCast.matchingEvidence(rows.getJSONObject(0),story)[4].points)
    }
    @Test fun presenterStoryCanCastGhostFromTheBundledCatalog() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open("fantasy_masters/catalog.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("records")
        }
        val presenter = JSONObject().put("id","P").put("display_name","ผู้ดำเนินเรื่อง")
        catalog.put(JSONObject().put("id","ALT").put("display_name","ผู้ดำเนินเรื่อง"))
        val choices = CastingSelection.withPresenter(presenter,catalog)
        assertEquals(catalog.length(),choices.length())
        val cast = AutoCast.kotlinProposal(choices,"ผู้ดำเนินเรื่องพบผีสาวที่ประตู",setOf("P")).getJSONArray("cast")
        assertEquals(2,cast.length())
        assertEquals("P",cast.getJSONObject(0).getString("library_id"))
        val spiritId = cast.getJSONObject(1).getString("library_id")
        val spirit = (0 until catalog.length()).map { catalog.getJSONObject(it) }.single { it.getString("id")==spiritId }
        assertTrue(spirit.getString("character_type") in setOf("ghost","spirit"))
    }
    @Test fun continuityCreditRequiresPriorAppearanceWithBothNameAndMaster() {
        val actor = JSONObject().put("name","นที").put("master","N_MASTER.png").put("library_id","N")
        val catalog = JSONArray().put(JSONObject().put("id","N"))
        val support = JSONArray().put(actor)
        fun history(person: JSONObject) = JSONArray().put(JSONObject().put("scenes",
            JSONArray().put(JSONObject().put("characters",JSONArray().put(person)))))
        fun result(episodes: JSONArray) = StoryContinuityLedger.returningCast(episodes,support,catalog,"","")
        assertEquals(0,result(JSONArray()).length())
        assertEquals(0,result(history(JSONObject(actor.toString()).put("master","OTHER.png"))).length())
        assertEquals(0,result(history(JSONObject(actor.toString()).put("name","คนอื่น"))).length())
        assertEquals(5,result(history(actor)).getJSONObject(0).getInt("continuity_points"))
    }
    @Test fun familyEvidenceRetainsMultipleRelationshipsForOneIdentity() {
        fun actor(name:String,id:String) = JSONObject().put("name",name).put("library_id",id)
        val cast = JSONArray().put(actor("นนท์","A")).put(actor("นที","B")).put(actor("มาลี","C"))
        val facts = StoryContinuityLedger.relationships("นนท์เป็นพ่อของนที. นนท์เป็นสามีของมาลี. นทีไม่ได้เป็นพี่ของมาลี",JSONArray(),cast)
        assertEquals(2,facts.length())
        assertEquals("A",facts.getJSONObject(0).getString("subject_id"))
        assertEquals("B",facts.getJSONObject(0).getString("object_id"))
        assertEquals("A",facts.getJSONObject(1).getString("subject_id"))
        assertEquals("C",facts.getJSONObject(1).getString("object_id"))
    }
    @Test fun aiCannotOmitAnExplicitlyAppearingGhostOrSubstituteHuman() {
        val human = JSONObject().put("id","H").put("display_name","ผีสาว").put("character_type","human")
        val ghost = JSONObject().put("id","G").put("display_name","วิญญาณหญิงชุดน้ำเงิน").put("character_type","ghost")
        val rows = JSONArray().put(human).put(ghost)
        val cast = JSONArray().put(JSONObject().put("library_id","H"))
        val proposal = JSONObject().put("cast",cast)
        assertTrue(runCatching { AutoCast.requireNarratedAppearances(proposal,rows,"ผีสาวปรากฏตัวที่ประตู") }.isFailure)
        AutoCast.requireNarratedAppearances(proposal,rows,"พ่อเล่าถึงผีสาว")
        assertNotNull(AutoCast.roleConflict(human,"ผีสาว"))
        cast.put(JSONObject().put("library_id","G"))
        AutoCast.requireNarratedAppearances(proposal,rows,"ผีสาวปรากฏตัวที่ประตู")
    }
}
