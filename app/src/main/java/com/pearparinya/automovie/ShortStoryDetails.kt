package com.pearparinya.automovie

import org.json.JSONObject

/** Display and copy the stored short-story information without inventing missing text. */
internal object ShortStoryDetails {
    fun text(story: JSONObject): String {
        fun value(obj: JSONObject?, key: String): String =
            obj?.optString(key, "")?.trim()?.takeUnless { it.equals("null", true) }.orEmpty()
        val firstEpisode = story.optJSONArray("episodes")?.optJSONObject(0)
        val title = value(story, "title").ifBlank { "ไม่มีชื่อเรื่อง" }
        val synopsis = value(story, "synopsis").ifBlank { value(firstEpisode, "synopsis") }
            .ifBlank { "ไม่มีเรื่องย่อในข้อมูลเรื่องนี้" }
        val caption = value(story, "social_caption").ifBlank { "ไม่มีแคปชั่นในข้อมูลเรื่องนี้" }
        return "ชื่อเรื่อง\n$title\n\nเรื่องย่อ\n$synopsis\n\nแคปชั่นคลิปสั้น\n$caption"
    }
}
