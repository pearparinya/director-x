package com.pearparinya.automovie

import org.json.JSONObject

internal object StoryTitleGuard {
    private fun compact(value: String) = value.lowercase().replace(Regex("[\\s\\p{P}\\p{Cf}]+"), "")

    fun branded(title: String, channel: String): Boolean {
        val text=compact(title)
        val brand=compact(channel)
        if(brand.isBlank()) return false
        if(text.contains(brand)) return true
        if(brand=="นายเปียบ้านคลองขุด") {
            return listOf("นายเปีย","คลองขุด","naipia","khlongkhud","khlongkhut")
                .any { text.contains(compact(it)) }
        }
        return channel.split(Regex("[\\s,|/]+")).map(::compact).any {
            it.length>=5 && it !in setOf("เรื่องเล่า","ละครสั้น","สะท้อนสังคม") && text.contains(it)
        }
    }

    fun validate(title: String, channel: String) {
        require(title.isNotBlank()) { "ชื่อเรื่องว่างเปล่า • กรุณาตั้งชื่อใหม่จากเหตุการณ์ในบท" }
        require(!branded(title,channel)) { "ชื่อเรื่องมีชื่อช่องหรือคำเฉพาะของช่อง • ตั้งชื่อจากเนื้อเรื่อง" }
    }

    fun cleanCategory(category: String, channel: String): String {
        val renamed=category.replace("นายเปียพาช้อป","รีวิวและแนะนำสินค้า")
            .replace("ชีวิตและเรื่องราวนายเปีย","ชีวิตและชุมชน").replace("วิถีชีวิตบ้านคลองขุด","วิถีชีวิตชุมชน")
        return if(branded(renamed,channel)) "เรื่องทั่วไป" else renamed
    }

    fun cleanMetadata(story: JSONObject, channel: String) {
        if(story.has("category")) story.put("category",cleanCategory(story.optString("category"),channel))
        if(branded(story.optString("title"),channel)) {
            val tagline=story.optString("tagline").trim()
            story.put("title",tagline.takeIf { it.isNotEmpty() && it.length<=65 && !branded(it,channel) } ?: "เรื่องราวของเรา")
        }
        val episodes=story.optJSONArray("episodes") ?: return
        for(i in 0 until episodes.length()) {
            val ep=episodes.optJSONObject(i) ?: continue
            if(!branded(ep.optString("title"),channel)) continue
            val number=ep.optInt("ep",i+1)
            val title=sequenceOf(ep.optString("tagline"),story.optString("title")).map { it.trim() }
                .firstOrNull { it.isNotEmpty() && it.length<=65 && !branded(it,channel) }
            ep.put("title",if(title==null) "ตอนที่ $number" else if(episodes.length()==1) title else "$title • ตอนที่ $number")
        }
    }
}
