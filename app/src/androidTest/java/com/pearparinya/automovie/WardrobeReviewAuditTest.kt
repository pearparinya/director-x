package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WardrobeReviewAuditTest {
    private fun scene(day: String,shoes: String,top: String="white shirt")=JSONObject().put("story_day",day).put("characters",JSONArray().put(JSONObject().put("name","กวิน").put("outfit","Top: $top; Bottom: black trousers; Shoes: $shoes; Accessories: none")))
    @Test fun warnsAboutBareFeetAndShortClothingWithoutChangingStory() {
        val row=scene("DAY01","none"); val before=row.toString()
        val warnings=WardrobeReviewAudit.warnings(listOf(row))
        assertTrue(warnings.any { it.contains("เท้าเปล่า") }); assertTrue(warnings.any { it.contains("รายละเอียด") }); assertEquals(before,row.toString())
    }
    @Test fun detectsSameDayConflictButDoesNotCompareDifferentDays() {
        val a=scene("DAY01","black shoes"); val b=scene("DAY01","brown shoes")
        assertTrue(WardrobeReviewAudit.warnings(listOf(a,b)).any { it.contains("ต่างกันในวันเดียวกัน") })
        b.put("story_day","DAY02")
        assertFalse(WardrobeReviewAudit.warnings(listOf(a,b)).any { it.contains("ต่างกันในวันเดียวกัน") })
    }
}
