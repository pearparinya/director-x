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
}
