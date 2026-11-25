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
            assertFalse(field.name, result.split(Regex("[\\s,]+")).any { token -> token.removePrefix("#").firstOrNull()?.let { Character.getType(it) in listOf(6,7,8) } == true })
        }
    }
    @Test fun thaiToneMarkAtWordEndIsValid() {
        val s=JSONObject().put("title","เรื่องใกล้ตัว").put("channel","ข่าววันนี้")
        val result=SocialPostText.platform(s,-1,SocialPostText.Field.FACEBOOK)
        assertTrue(result.contains("#ข่าววันนี้"))
    }
    @Test fun longHashtagRetainsCompleteWords() {
        val word="เรื่องราวภาษาไทย"
        val s=JSONObject().put("title",("$word ").repeat(100))
        val result=SocialPostText.platform(s,-1,SocialPostText.Field.DESCRIPTION)
        val tags=Regex("#[^\\s]+").findAll(result).map { it.value.removePrefix("#") }.toList()
        assertTrue(tags.isNotEmpty())
        for(tag in tags) {
            assertTrue(tag.length<=100)
            assertEquals(word.repeat(tag.length/word.length),tag)
        }
    }
    @Test fun episodeTitleAndSafeMetadata() {
        val result = SocialPostText.platform(story(),1,SocialPostText.Field.FACEBOOK)
        assertTrue(result.contains("EP.2 ตอน ตอนสอง"))
        assertTrue(result.contains("ข้อมูลสอง"))
        assertFalse(result.contains("ความลับตอนจบ"))
    }
    @Test fun promotionalVariants() {
        val a = SocialPostText.platform(story(),0,SocialPostText.Field.FACEBOOK,0)
        val b = SocialPostText.platform(story(),0,SocialPostText.Field.FACEBOOK,1)
        assertNotEquals(a,b)
        assertTrue(b.contains("ข้อมูลแรก"))
        assertTrue(b.length <= 350)
    }
}
