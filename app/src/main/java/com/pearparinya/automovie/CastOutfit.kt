package com.pearparinya.automovie

import java.io.File
import java.security.MessageDigest

internal data class CastOutfit(val master: String, val outfit: String) {
    private val key: String get() {
        val normalized = master.trim().lowercase(java.util.Locale.ROOT) + "\u0000" + outfit.trim()
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    fun file(project: File) = File(project, "approved_cast_outfits/$key.png")
    fun attachment(project: File): Pair<String, File>? = file(project).takeIf {
        master.isNotBlank() && outfit.isNotBlank() && it.isFile && it.length() > 0
    }?.let { "OUTFIT_CAST_$key" to it }
}
