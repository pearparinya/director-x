package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortStoryDetailsTest {
    @Test fun displaysStoryTitleSynopsisAndCaption() {
        val story = JSONObject().put("title", "เรื่องสนุก")
            .put("episodes", JSONArray().put(JSONObject().put("synopsis", "พบเพื่อนบ้าน")))
            .put("social_caption", "ยิ้มด้วยกัน #สนุก")
        assertEquals("ชื่อเรื่อง\nเรื่องสนุก\n\nเรื่องย่อ\nพบเพื่อนบ้าน\n\nแคปชั่นคลิปสั้น\nยิ้มด้วยกัน #สนุก", ShortStoryDetails.text(story))
    }

    @Test fun missingLegacyFieldsAreClearlyMarked() {
        val story = JSONObject().put("title", "เรื่องเก่า")
        assertEquals("ชื่อเรื่อง\nเรื่องเก่า\n\nเรื่องย่อ\nไม่มีเรื่องย่อในข้อมูลเรื่องนี้\n\nแคปชั่นคลิปสั้น\nไม่มีแคปชั่นในข้อมูลเรื่องนี้", ShortStoryDetails.text(story))
    }
}
