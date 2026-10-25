package com.pearparinya.automovie

/** One text contract for all covers; scene-image text restrictions do not apply to posters. */
internal object CoverTypography {
    fun lines(channel: String,story: String,ep: Int?=null,episode: String="",tagline: String=""): List<String> =
        listOf(channel,story,if(ep==null) tagline else "EP.${ep.toString().padStart(2,'0')} : $episode")

    fun prompt(channel: String,story: String,ep: Int?=null,episode: String="",tagline: String=""): String {
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
Stack the text as one compact group centered horizontally at x=50%, within x=15-85% and y=52-78% (lower-middle safe zone). Keep faces clear. Use legible Thai display type and local contrast support; resize or wrap long entries without dropping words or clipping letters.
COVER TEXT CHECK BEFORE DONE: verify every required entry is visibly present and readable inside the image. If missing, misspelled or cropped, repair this same cover while preserving cast, locked wardrobe and composition; do not advance to next. If the tool cannot render the lettering, report PENDING and ask for correction instead of claiming DONE. No extra text or watermark.
        """.trimIndent()
    }
}
