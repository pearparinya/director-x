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
        val coverTagline=value(story,"tagline")
        val tagline=value(episode,"tagline")
        val description=value(episode,"social_description").ifBlank { value(story,"social_caption") }.ifBlank { value(episode,"synopsis") }.ifBlank { value(story,"synopsis") }
        val tags=listOf(channel,title,epTitle.ifBlank { category }).map { tag(it) }.filter { it.isNotBlank() }.distinct().joinToString(" ") { "#$it" }
        return listOfNotNull(
            channel.takeIf { it.isNotBlank() }?.let { "ช่อง: $it" },
            title.takeIf { it.isNotBlank() }?.let { "เรื่อง: $it" },
            coverTagline.takeIf { it.isNotBlank() }?.take(180)?.let { "คำโปรยหน้าปก: $it" },
            category.takeIf { it.isNotBlank() }?.let { "หมวดหมู่: $it" },
            epTitle.takeIf { it.isNotBlank() }?.let { "ตอนที่ ${episode?.optInt("ep",episodeIndex+1)}: $it" },
            tagline.takeIf { it.isNotBlank() && it != coverTagline }?.take(180)?.let { "คำโปรย: $it" },
            description.takeIf { it.isNotBlank() && it != tagline && it != coverTagline }?.take(180)?.let { "คำอธิบาย: $it" },
            tags.takeIf { it.isNotBlank() }
        ).joinToString("\n\n")
    }

    enum class Field(val label: String, val limit: Int) {
        FACEBOOK("FACEBOOK POST",350), TIKTOK("TIKTOK CAPTION",200),
        TITLE("SEO TITLE",100), DESCRIPTION("DESCRIPTION",350),
        TAGS("SEO TAGS",500), INSTAGRAM("INSTAGRAM CAPTION",250)
    }
    private val hashtag = Regex("#[\\p{L}\\p{M}\\p{N}_]+")
    private fun clean(s: String) = hashtag.replace(s,"").replace(Regex("\\s+")," ").trim()
    private fun fit(s: String, limit: Int): String {
        if (s.length <= limit) return s
        if (limit <= 0) return ""
        val iterator = java.text.BreakIterator.getWordInstance(java.util.Locale("th"))
        iterator.setText(s)
        val end = iterator.preceding(limit + 1)
        return if (end > 0) s.substring(0,end).trim() else ""
    }
    fun platform(story: JSONObject, index: Int, field: Field): String {
        val ep = if(index < 0) null else story.optJSONArray("episodes")?.optJSONObject(index)
        val title = clean(value(story,"title"))
        val channel = ChannelCatalog.currentChannel(value(story,"channel"))
        val epTitle = clean(value(ep,"title"))
        val suffix = if(ep != null) " | EP."+ep.optInt("ep",index+1) else ""
        val heading = fit(title, (field.limit-suffix.length).coerceAtLeast(0)) + suffix
        val hook = clean(value(ep,"tagline").ifBlank { epTitle }.ifBlank { value(story,"tagline") })
            .takeUnless { it == title || title.contains(it) }.orEmpty()
        val raw = if(ep != null) value(ep,"social_description") else value(story,"social_caption")
        val desc = clean(raw)
        val category = value(story,"category").substringBefore("(").trim()
        val keywords = listOf(channel,title,category,epTitle).map(::clean).filter { it.isNotBlank() }.distinct()
        val tags = (keywords.map(::tag) + hashtag.findAll(raw).map { it.value.removePrefix("#") }.toList())
            .filter { it.isNotBlank() }.distinct()
        if(field == Field.TAGS) return keywords.filter { it.length <= 100 }.take(10).joinToString(", ").let { fit(it,field.limit) }
        if(field == Field.TITLE) return listOf(heading,hook).filter { it.isNotBlank() }.distinct().let {
            if(it.joinToString(" | ").length <= field.limit) it.joinToString(" | ") else heading
        }
        val lines = when(field) {
            Field.DESCRIPTION -> listOf(desc.ifBlank { hook }.ifBlank { heading })
            Field.TIKTOK, Field.INSTAGRAM -> listOf(heading,hook.ifBlank { desc })
            else -> listOf(heading,hook,desc)
        }.filter { it.isNotBlank() }.distinct()
        var result = ""
        for(line in lines) {
            val remaining = field.limit-result.length-(if(result.isEmpty()) 0 else 2)
            if(remaining <= 0) break
            val part = fit(line,remaining)
            if(part.isNotBlank()) result = listOf(result,part).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        val selected = mutableListOf<String>()
        for(t in tags.take(if(field==Field.DESCRIPTION) 3 else 5)) {
            val candidate = (selected + "#$t").joinToString(" ")
            if(result.length+2+candidate.length <= field.limit) selected.add("#$t")
        }
        return listOf(result,selected.joinToString(" ")).filter { it.isNotBlank() }.joinToString("\n\n")
    }
}
