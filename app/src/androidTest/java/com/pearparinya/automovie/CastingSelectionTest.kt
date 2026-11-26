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
}
