package com.pearparinya.automovie

internal object BatchTiming {
    fun remaining(elapsed: Long, initial: Int, done: Int, total: Int, finished: Boolean): Long? {
        val newlyCompleted = done - initial
        if (finished || newlyCompleted < 2 || done >= total || elapsed <= 0) return null
        return elapsed / newlyCompleted * (total - done)
    }
}
