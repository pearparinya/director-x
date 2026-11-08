package com.pearparinya.automovie

import org.json.JSONObject

/** Copies the current saved metadata; never manufactures episode titles or plot details. */
internal object SocialPostText {
    private fun value(obj: JSONObject?, key: String): String = obj?.optString(key, "")?.trim()?.takeUnless { it.equals("null",true) }.orEmpty()
    private fun tag(value: String): String = value.replace(Regex("[^\\p{L}\\p{M}\\p{N}_]"), "").take(100)
    fun build(story: JSONObject, episodeIndex: Int): String {
        val episode=story.optJSONArray("episodes")?.optJSONObject(episodeIndex)
        val channel=ChannelCatalog.currentChannel(value(story,"channel"))
        val title=value(story,"title")
        val category=value(story,"category")
        val epTitle=value(episode,"title")
        val tagline=value(episode,"tagline").ifBlank { value(story,"tagline") }
        val description=value(episode,"social_description").ifBlank { value(story,"social_caption") }.ifBlank { value(episode,"synopsis") }.ifBlank { value(story,"synopsis") }
        val tags=listOf(channel,title,epTitle.ifBlank { category }).map { tag(it) }.filter { it.isNotBlank() }.distinct().joinToString(" ") { "#$it" }
        return listOfNotNull(
            channel.takeIf { it.isNotBlank() }?.let { "ช่อง: $it" },
            title.takeIf { it.isNotBlank() }?.let { "เรื่อง: $it" },
            category.takeIf { it.isNotBlank() }?.let { "หมวดหมู่: $it" },
            epTitle.takeIf { it.isNotBlank() }?.let { "ตอนที่ ${episode?.optInt("ep",episodeIndex+1)}: $it" },
            tagline.takeIf { it.isNotBlank() }?.take(180)?.let { "คำโปรย: $it" },
            description.takeIf { it.isNotBlank() && it != tagline }?.take(180)?.let { "คำอธิบาย: $it" },
            tags.takeIf { it.isNotBlank() }
        ).joinToString("\n\n")
    }
}
