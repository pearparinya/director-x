package com.pearparinya.automovie

/** One text contract for all covers; scene-image text restrictions do not apply to posters. */
internal object CoverTypography {
    const val layout = """Keep all three entries together as one compact group centered horizontally in the lower-middle, approximately x=10-90% and y=52-72%. Order top to bottom: channel, large story title, then tagline or episode text. Place the channel immediately above the title, never at the top edge; place the third entry immediately below the title, preferably on one line when legible. Make the title the dominant line, cinematic Thai lettering in genre-appropriate color and restrained cinematic depth; channel and third entry are smaller. Preserve the visible setting and meaningful foreground props below the text through the bottom edge; no large empty black panel, solid black lower half, or heavy full-width black gradient. Use only a subtle local shadow behind letters. Keep faces unobstructed and all letters inside the frame; adjust type size or wrap long text naturally without losing words. Compose separately for each aspect ratio."""
    /** Genre-aware lettering guidance; keep the existing exact Thai text contract. */
    fun genreStyle(category: String): String {
        val genre = category.lowercase()
        return when {
            listOf("ผี", "สยอง", "horror", "ghost").any { it in genre } ->
                "HORROR: distressed but legible Thai display lettering, cold muted silver and subtle eerie shadow; avoid illegible drips."
            listOf("หมอผี", "ไสย", "อาคม", "occult", "ritual").any { it in genre } ->
                "OCCULT: ritual-inspired angular Thai display lettering, aged bronze and restrained ember accents; no fake sacred symbols."
            listOf("สืบสวน", "ฆาต", "thriller", "crime", "mystery").any { it in genre } ->
                "THRILLER: condensed high-contrast Thai lettering, restrained white and deep red, crisp spacing."
            listOf("รัก", "romance").any { it in genre } ->
                "ROMANCE: graceful rounded Thai lettering, warm ivory and soft rose accents, delicate shadow."
            listOf("ตลก", "comedy").any { it in genre } ->
                "COMEDY: bold playful but readable Thai lettering, lively complementary accents, clean outlines."
            listOf("ตำนาน", "ย้อนยุค", "ประวัติ", "legend", "period").any { it in genre } ->
                "LEGEND / PERIOD: refined traditional-inspired Thai lettering, antique gold and textured restraint."
            listOf("ชีวิต", "ครอบครัว", "สะท้อนสังคม", "drama", "family").any { it in genre } ->
                "LIFE DRAMA: elegant contemporary Thai lettering, warm ivory or subdued gold, natural cinematic depth."
            else -> "GENRE ADAPTIVE: choose readable cinematic Thai typography from the story mood; avoid a fixed gold template."
        }
    }
    /** Keep vertical text clear of common Shorts/Reels overlays; actual platform UI can vary. */
    fun safeZone(ratio: String): String =
        if (ratio == "9:16")
            "VERTICAL SOCIAL SAFE AREA: Keep all lettering within x=8%-85% and y=12%-78%; avoid top 10%, bottom 20%, left 8% and right 15%. Do not cover any face, eyes or important hand gesture. Keep the three text entries grouped and move the group to genuine negative space rather than forcing a fixed center."
        else
            "LANDSCAPE SAFE AREA: Keep lettering at least 6% inside each edge and clear of faces and important action. Recompose independently."

    fun lines(channel: String,story: String,ep: Int?=null,episode: String="",tagline: String=""): List<String> =
        listOf(ChannelCatalog.currentChannel(channel),story,if(ep==null) tagline else "ตอนที่ $ep $episode")

    fun prompt(channel: String,story: String,ep: Int?=null,episode: String="",tagline: String="",category: String=""): String {
        val missing=buildList {
            if(channel.isBlank()) add("ชื่อช่อง")
            if(story.isBlank()) add("ชื่อเรื่อง")
            if(ep==null && tagline.isBlank()) add("คำโปรยเรื่อง")
            if(ep!=null && (ep<=0 || episode.isBlank())) add("เลข EP และชื่อตอน")
        }
        if(missing.isNotEmpty()) return "STOP — หยุดก่อนสร้างปก: ขาด ${missing.joinToString(", ")} ในบท ให้ขอข้อมูลที่ขาด ห้ามแต่งข้อความแทน ห้ามสร้างปกไร้ข้อความ และคงงานนี้เป็น PENDING ไม่ขยับคิว"
        return """
MANDATORY COVER TEXT — RENDER VISIBLE LETTERING INSIDE THE ACTUAL IMAGE.
ต้องวาดข้อความไทยครบทั้ง 3 รายการลงในภาพปกจริง ไม่ใช่พิมพ์คำบรรยายใต้ภาพหรือเพียงเว้นพื้นที่ว่าง ภาพสวยแต่ไม่มีข้อความถือว่าปกยังไม่เสร็จ
This is a cover/poster. Scene-image instructions that prohibit text do not apply to this cover. Render all required text below, verbatim; do not replace the story title with the episode title or omit the channel/tagline. Never return textless key art.
BEGIN EXACT COVER TEXT
${lines(channel,story,ep,episode,tagline).joinToString("\n")}
END EXACT COVER TEXT
Do not draw the BEGIN/END labels. Draw only the three required entries. Thai spelling, punctuation and wording must match exactly. Main story title largest; channel smaller; ${if(ep==null) "story tagline" else "episode number and episode title"} secondary. Do not add ${if(ep==null) "episode labels" else "a fourth tagline line"}.
$layout
GENRE TYPOGRAPHY: ${genreStyle(category)}\nPRODUCTION TYPOGRAPHY: Use a professional film-poster typographic hierarchy, clear Thai letterforms, appropriate kerning and line spacing, and contrast suited to the scene. Preserve exact wording; never invent extra copy.
COVER TEXT CHECK BEFORE DONE: verify every required entry is visibly present and readable inside the image. If missing, misspelled or cropped, repair this same cover while preserving cast, locked wardrobe and composition; do not advance to next. If the tool cannot render the lettering, report PENDING and ask for correction instead of claiming DONE. No extra text or watermark.
        """.trimIndent()
    }
}
