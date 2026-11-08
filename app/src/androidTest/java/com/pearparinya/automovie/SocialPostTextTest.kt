package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialPostTextTest {
    @Test fun selectsEpisodeAndUsesItsActualChannelAndTitleForTags() {
        val story=JSONObject().put("channel","นายเปียบ้านคลองขุด").put("category","ชีวิตการทำงาน").put("title","สัญญาแห่งแรงกดดัน").put("tagline","ความจริงต้องปรากฏ")
            .put("episodes",JSONArray().put(JSONObject().put("ep",1).put("title","จุดเริ่มต้น").put("tagline","ความลับเริ่มเปิดเผย").put("synopsis","การประชุมครั้งสำคัญ"))
                .put(JSONObject().put("ep",2).put("title","ความจริง")))
        val first=SocialPostText.build(story,0)
        assertTrue(first.contains("ช่อง: นายเปียบ้านคลองขุด")); assertTrue(first.contains("ตอนที่ 1: จุดเริ่มต้น"))
        assertTrue(first.contains("หมวดหมู่: ชีวิตการทำงาน")); assertTrue(first.contains("คำโปรย: ความลับเริ่มเปิดเผย")); assertTrue(first.contains("คำอธิบาย: การประชุมครั้งสำคัญ")); assertTrue(first.contains("#นายเปียบ้านคลองขุด")); assertTrue(first.contains("#สัญญาแห่งแรงกดดัน"))
        val second=SocialPostText.build(story,1)
        assertTrue(second.contains("ตอนที่ 2: ความจริง")); assertTrue(second.contains("ความจริงต้องปรากฏ")); assertFalse(second.contains("จุดเริ่มต้น"))
    }
    @Test fun missingDataDoesNotInventContent() {
        assertEquals("เรื่อง: เรื่องเก่า\n\n#เรื่องเก่า",SocialPostText.build(JSONObject().put("title","เรื่องเก่า"),0))
    }
}
