package com.pearparinya.automovie

internal object EpisodeRules {
    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val INTRO_MS = 3000L
    fun filename(title: String, ep: Int, episodeTitle: String): String {
        require(ep > 0)
        fun clean(value: String, limit: Int, fallback: String): String {
            var safe=value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.', '_').ifBlank { fallback }
            while(safe.toByteArray(Charsets.UTF_8).size>limit) {
                safe=safe.dropLast(1)
                if(safe.lastOrNull()?.isHighSurrogate()==true) safe=safe.dropLast(1)
            }
            return safe
        }
        return "${clean(title,110,"ชื่อเรื่อง")}_EP%02d_${clean(episodeTitle,70,"ชื่อตอน")}.mp4".format(ep)
    }
    fun estimateBytes(durationMs: Long, bitrate: Int, sourceBytes: Long): Long =
        sourceBytes + durationMs * bitrate / 8000 * 2 + 100L * 1024 * 1024
}
