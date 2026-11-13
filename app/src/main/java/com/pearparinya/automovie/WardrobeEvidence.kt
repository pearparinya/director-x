package com.pearparinya.automovie

/** Distinguishes missing/ambiguous values from an explicit human wardrobe decision. */
internal object WardrobeEvidence {
    private fun absent(value: String): Boolean =
        Regex("(?i)^(none|n/a|not applicable|ไม่มี)\\s*$").matches(value.trim())

    fun isAllNone(outfit: String): Boolean =
        runCatching { AutoDirectorWardrobe.parts(outfit).values.all(::absent) }.getOrDefault(false)

    fun isUnknownOrAllNone(outfit: String): Boolean = outfit.isBlank() || isAllNone(outfit)
}
