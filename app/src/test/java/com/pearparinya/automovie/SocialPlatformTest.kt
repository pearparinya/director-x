package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SocialPlatformTest {
    private fun story() = JSONObject().put("title","เรื่องทดสอบ").put("channel","ช่องทดสอบ")
        .put("category","ความรู้").put("tagline","ข้อมูลใกล้ตัว")
        .put("episodes",JSONArray()
            .put(JSONObject().put("ep",1).put("title","ตอนแรก").put("social_description","ข้อมูลแรก #เรื่องทดสอบ #เรื่องทดสอบ #ไม่เกี่ยวข้อง"))
            .put(JSONObject().put("ep",2).put("title","ตอนสอง").put("social_description","ข้อมูลสอง").put("synopsis","ความลับตอนจบ")))
    @Test fun allSixFields() {
        for(field in SocialPostText.Field.values()) {
            val result=SocialPostText.platform(story(),0,field)
            assertTrue(field.name,result.isNotBlank())
            assertTrue(field.name,result.length<=field.limit)
            assertFalse(result.contains("ตอนสอง"))
        }
    }
    @Test fun episodeIsolation() {
        val result=SocialPostText.platform(story(),1,SocialPostText.Field.FACEBOOK)
        assertTrue(result.contains("EP.2"))
        assertTrue(result.contains("ข้อมูลสอง"))
        assertFalse(result.contains("ข้อมูลแรก"))
        assertFalse(result.contains("ความลับตอนจบ"))
    }
    @Test fun invalidEpisode() {
        assertEquals("",SocialPostText.platform(story(),2,SocialPostText.Field.FACEBOOK))
    }
    @Test fun wholeStory() {
        val result=SocialPostText.platform(story(),-1,SocialPostText.Field.FACEBOOK)
        assertFalse(result.contains("EP."))
        assertFalse(result.contains("ข้อมูลสอง"))
    }
    @Test fun deduplicatedRelevantTags() {
        val result=SocialPostText.platform(story(),0,SocialPostText.Field.FACEBOOK)
        assertEquals(1,Regex("#เรื่องทดสอบ").findAll(result).count())
        assertFalse(result.contains("#ไม่เกี่ยวข้อง"))
    }
    @Test fun legacyAndEmpty() {
        assertTrue(SocialPostText.platform(JSONObject().put("title","เรื่องเก่า"),0,SocialPostText.Field.TITLE).contains("เรื่องเก่า"))
        assertEquals("",SocialPostText.platform(JSONObject(),-1,SocialPostText.Field.TITLE))
    }
    @Test fun spoilerMarker() {
        val s=story()
        s.getJSONArray("episodes").getJSONObject(0).put("social_description","เฉลยว่าเป็นคนร้าย")
        assertFalse(SocialPostText.platform(s,0,SocialPostText.Field.FACEBOOK).contains("คนร้าย"))
    }
    @Test fun longThaiText() {
        val s=story().put("title","เรื่องราวภาษาไทย ".repeat(100))
        for(field in SocialPostText.Field.values()) {
            val result=SocialPostText.platform(s,0,field)
            assertTrue(result.length<=field.limit)
            assertFalse(result.endsWith("\u0e49"))
        }
    }
}
