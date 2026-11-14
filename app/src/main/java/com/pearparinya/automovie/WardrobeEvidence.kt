package com.pearparinya.automovie

/** Distinguishes missing/ambiguous values from an explicit human wardrobe decision. */
internal object WardrobeEvidence {
    private fun absent(value: String): Boolean =
        Regex("(?i)^(none|n/a|not applicable|ไม่มี)\\s*$").matches(value.trim())

    fun isAllNone(outfit: String): Boolean =
        runCatching { AutoDirectorWardrobe.parts(outfit).values.all(::absent) }.getOrDefault(false)

    /** Only a clear instruction may select the original MASTER outfit. */
    fun useMasterOutfit(outfit: String): Boolean {
        val s=outfit.trim().lowercase()
        if (s.isEmpty()) return false
        if (Regex("(?i)(do not use|not from|ห้ามใช้|ไม่ใช้|ไม่เอา).{0,25}master").containsMatchIn(s)) return false
        return Regex("(?i)(ชุด.{0,20}master|master.{0,20}(outfit|clothing|clothes|wardrobe|ชุด)|(?:outfit|clothing|clothes|wardrobe).{0,20}master)").containsMatchIn(s)
    }

    fun isUnknownOrAllNone(outfit: String): Boolean = outfit.isBlank() || isAllNone(outfit)
}
