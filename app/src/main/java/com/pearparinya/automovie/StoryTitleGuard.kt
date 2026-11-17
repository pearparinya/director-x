package com.pearparinya.automovie

/** Validates generated titles without changing a user-supplied title or existing story. */
internal object StoryTitleGuard {
    fun validate(title: String, channel: String, premise: String) {
        require(title.isNotBlank()) { "ชื่อเรื่องว่างเปล่า • กรุณาตั้งชื่อใหม่จากเหตุการณ์ในบท" }
        val normalizedTitle=title.lowercase().replace(Regex("\\s+"), "")
        val normalizedChannel=channel.lowercase().replace(Regex("\\s+"), "")
        if(normalizedChannel.length>=5 && normalizedTitle.contains(normalizedChannel)) {
            throw IllegalArgumentException("ชื่อเรื่องซ้ำชื่อช่อง • ตั้งชื่อใหม่จากเหตุการณ์และความขัดแย้งในบท")
        }
        // Distinctive channel tokens (not generic titles or honorifics) must not leak into an unrelated story.
        val tokens=channel.lowercase().split(Regex("[\\s,|/]+"))
            .map { it.trim() }.filter { it.length>=5 && it !in setOf("เรื่องเล่า","ละครสั้น","สะท้อนสังคม") }
        val distinctive=tokens.flatMap { token -> listOf(token,token.removePrefix("บ้าน").takeIf { token.startsWith("บ้าน") && it.length>=4 }).filterNotNull() }.distinct()
        for(token in distinctive) {
            if(normalizedTitle.contains(token) && !premise.lowercase().contains(token)) {
                throw IllegalArgumentException("ชื่อเรื่องนำคำเฉพาะจากชื่อช่อง '$token' มาใช้โดยไม่เกี่ยวกับเรื่อง • กรุณาตั้งชื่อใหม่")
            }
        }
    }
}
