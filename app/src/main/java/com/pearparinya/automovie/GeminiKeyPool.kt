package com.pearparinya.automovie

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/** Picks once per client/task. Retries of that request retain the same selected key. */
internal class GeminiKeyRotation {
    private val cursor=AtomicInteger()
    fun <T> next(items: List<T>): T {
        require(items.isNotEmpty()) { "ยังไม่มี Gemini API Key • กรุณาตั้งค่า" }
        return items[Math.floorMod(cursor.getAndIncrement(),items.size)]
    }
}
internal object GeminiKeyPool {
    private val rotation=GeminiKeyRotation()
    fun next(context: Context): Pair<Int,String> = rotation.next(GeminiSettings.configured(context))
}
