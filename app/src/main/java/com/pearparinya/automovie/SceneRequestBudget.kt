package com.pearparinya.automovie

internal class SceneWorkPaused(reason: String) : IllegalStateException("$reason • บันทึกช่วงที่ผ่านแล้ว กดทำต่อได้")

/** One allowance for writing, formatting and repairs at the same episode checkpoint. */
internal class SceneRequestBudget(
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val maxRequests: Int = 8,
    private val maxMillis: Long = 180_000L
) {
    private var checkpoint: Pair<Int,Int>? = null
    private var started: Long? = null
    private var attempts = 0
    val active: Boolean get() = checkpoint != null

    fun select(episode: Int, firstScene: Int): Boolean {
        val next = episode to firstScene
        if (checkpoint == next) return false
        checkpoint = next
        started = null
        attempts = 0
        return true
    }

    fun allow(requestMillis: Long): Long {
        val scene = checkpoint ?: return requestMillis
        val time = now()
        val start = started ?: time.also { started = it }
        val remaining = maxMillis - (time - start).coerceAtLeast(0)
        if (attempts >= maxRequests || remaining <= 0) {
            throw SceneWorkPaused("พัก EP ${scene.first} ซีน ${scene.second} หลังลอง $attempts ครั้ง")
        }
        attempts++
        return minOf(requestMillis, remaining)
    }
}
