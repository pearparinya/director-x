package com.pearparinya.automovie

import org.json.JSONObject

/** Copies the current saved metadata; never manufactures episode titles or plot details. */
internal object SocialPostText {
    private fun value(obj: JSONObject?, key: String): String = obj?.optString(key, "")?.trim()?.takeUnless { it.equals("null",true) }.orEmpty()
    private fun tag(value: String): String = fitTag(value,100).replace(Regex("[^\\p{L}\\p{M}\\p{N}_]"), "").trimStart { Character.getType(it) in listOf(6,7,8) }
    private fun fitTag(value: String, limit: Int): String {
        if(value.length <= limit) return value
        val prefix=fit(value,limit)
        val space=prefix.lastIndexOf(' ')
        return if(space>0 && value.getOrNull(prefix.length)?.isWhitespace()!=true) prefix.substring(0,space) else prefix
    }
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
        val graphemes = java.text.BreakIterator.getCharacterInstance(java.util.Locale("th"))
        graphemes.setText(s)
        var end = iterator.preceding(limit + 1)
        while(end > 0 && !graphemes.isBoundary(end)) end = iterator.preceding(end)
        return if (end > 0) s.substring(0,end).trim() else ""
    }
    private val spoiler = Regex("สุดท้าย|ตอนจบ|เฉลย|จบลง|แท้จริง|หักมุม|คนร้ายคือ|เสียชีวิต|ฆาตกรคือ|เปิดเผยว่า")
    private fun safe(s: String) = clean(s).takeUnless { spoiler.containsMatchIn(it) || it.equals("undefined",true) || it.equals("null",true) }.orEmpty()
    private fun unique(values: List<String>): List<String> {
        val out=mutableListOf<String>()
        for(v in values.filter { it.isNotBlank() }) {
            val k=tag(v).lowercase(java.util.Locale.ROOT)
            if(k.isNotBlank() && out.none { val old=tag(it).lowercase(java.util.Locale.ROOT); old==k || (minOf(old.length,k.length)>=8 && (old.contains(k)||k.contains(old))) }) out.add(v)
        }
        return out
    }
    private fun tone(category: String): Pair<String,String> = when {
        Regex("เตือนภัย|สวัสดิการ|สิทธิ|ความรู้|ไอที|เทคโนโลยี|สารคดี|รีวิว").containsMatchIn(category) -> "ติดตามรายละเอียด" to "ทำความเข้าใจข้อมูลไปด้วยกัน"
        Regex("ผี|สยอง|ตำนาน|ลี้ลับ|ปริศนา|สืบสวน").containsMatchIn(category) -> "ร่วมติดตามปริศนา" to "ค้นหาคำตอบไปด้วยกัน"
        Regex("ตลก|ฮา|คอมเมดี้|ซิตคอม").containsMatchIn(category) -> "ติดตามเรื่องราวชวนยิ้ม" to "มาชมเรื่องราวไปด้วยกัน"
        Regex("เพลง|MV",RegexOption.IGNORE_CASE).containsMatchIn(category) -> "ติดตามผลงานเพลง" to "รับชมและรับฟังไปด้วยกัน"
        Regex("ชีวิต|ครอบครัว|สังคม|อบอุ่น|ดราม่า|รัก").containsMatchIn(category) -> "ร่วมติดตามเรื่องราวและมุมมองชีวิต" to "ชวนติดตามเรื่องราวและข้อคิด"
        else -> "ติดตามเรื่องราว" to "ร่วมรับชมไปด้วยกัน"
    }
    fun platform(story: JSONObject, index: Int, field: Field, variant: Int = 0): String {
        val episodes=story.optJSONArray("episodes")
        val ep=if(index<0) null else episodes?.optJSONObject(index)
        if(index < -1 || (index>=0 && ep==null && (episodes?.length() ?: 0)>0)) return ""
        val title=clean(value(story,"title"))
        val channel=ChannelCatalog.currentChannel(value(story,"channel"))
        val epTitle=clean(value(ep,"title"))
        val category=value(story,"main_category").ifBlank { value(story,"category").substringBefore("(").trim() }
        val catalog=ChannelCatalog.allCategories.firstOrNull { it.id==category || it.name==category }
        val categoryName=catalog?.name ?: category
        val styles=value(story,"category_style").split("•").map(::safe)
        val topic=safe(value(ep,"topic").ifBlank { if(ep==null) value(story,"topic") else "" })
        val source=ep ?: story
        val promo=safe(value(source,if(ep!=null) "social_description" else "social_caption"))
        val synopsis=safe(value(source,"synopsis"))
        val hook=safe(value(source,"tagline"))
        val scenes=source.optJSONArray("scenes")
        val opening=if((scenes?.length() ?: 0)>1) scenes?.optJSONObject(0) else null
        val action=safe(value(opening,"start_state")).ifBlank { safe(value(opening,"action")) }
        val location=safe(value(opening,"location"))
        val speaker=safe(value(opening,"speaker"))
        val dialogue=safe(value(opening,"dialogue"))
        val mood=safe(value(source,"mood"))
        val context=unique(listOf(promo,synopsis,hook,action)).filter { it!=title && it!=epTitle }
        val keywords=unique(listOf(channel,title,categoryName,epTitle,topic,location,speaker,mood)+styles).filter { !spoiler.containsMatchIn(it) }
        val tags=keywords.map(::tag).filter { it.isNotBlank() }.distinct()
        if(field==Field.TAGS) {
            val selected=mutableListOf<String>()
            for(k in keywords.filter { it.length<=100 }.take(10)) if((selected+k).joinToString(", ").length<=field.limit) selected.add(k)
            return selected.joinToString(", ")
        }
        val suffix=if(ep!=null) " | EP."+ep.optInt("ep",index+1) else ""
        val tail=if(epTitle.isNotBlank()) " ตอน "+epTitle else ""
        val heading=if(title.length+suffix.length+tail.length<=field.limit) title+suffix+tail else {
            val room=(field.limit-suffix.length).coerceAtLeast(0)
            val shortTail=if(tail.isNotBlank()) fit(tail,room/2) else ""
            fit(title,room-shortTail.length)+suffix+shortTail
        }
        if(field==Field.TITLE) {
            val extra=unique(listOf(hook,promo,topic)).firstOrNull { it!=title && it!=epTitle }.orEmpty()
            return listOf(heading,extra).filter { it.isNotBlank() }.joinToString(" | ").let { if(it.length<=field.limit) it else heading }
        }
        val (ctaA,ctaB)=tone(categoryName+" "+styles.joinToString(" "))
        val alternate=variant%2!=0
        val cta=if(context.isEmpty()) "" else if(alternate) ctaB else ctaA
        val ordered=when(field) {
            Field.TIKTOK -> unique(listOf(hook,promo,action,synopsis))
            Field.INSTAGRAM -> unique(listOf(hook,action,promo,synopsis))
            Field.DESCRIPTION -> unique(listOf(promo,synopsis,action,hook))
            else -> context
        }.filter { it!=title && it!=epTitle }
        val body=if(alternate) ordered.reversed() else ordered
        val extra=if(body.isNotEmpty() && dialogue.isNotBlank() && speaker.isNotBlank()) listOf(speaker+" : "+dialogue) else emptyList()
        val selected=mutableListOf<String>()
        val tagBudget=minOf(field.limit/3,(field.limit-heading.length-4).coerceAtLeast(0))
        for(t in tags.take(if(field==Field.DESCRIPTION) 3 else 5)) {
            if((selected+"#$t").joinToString(" ").length<=tagBudget) selected.add("#$t")
        }
        if(selected.isEmpty() && tags.isNotEmpty()) {
            val first=tags.first()
            if(first.length+3+heading.length<=field.limit) selected.add("#$first")
        }
        val tagLine=selected.joinToString(" ")
        val bodyLimit=field.limit-(if(tagLine.isBlank()) 0 else tagLine.length+2)
        var result=heading
        val ctaReserve=if(cta.isNotBlank() && bodyLimit-heading.length>cta.length+15) cta.length+2 else 0
        for(line in body+extra) {
            val remaining=bodyLimit-result.length-ctaReserve-(if(result.isEmpty()) 0 else 2)
            if(remaining<=0) break
            val part=fit(line,remaining)
            if(part.isNotBlank()) result=listOf(result,part).filter { it.isNotBlank() }.joinToString("\n\n")
        }
        if(ctaReserve>0) result=listOf(result,cta).filter { it.isNotBlank() }.joinToString("\n\n")
        return listOf(result,tagLine).filter { it.isNotBlank() }.joinToString("\n\n")
    }
}
