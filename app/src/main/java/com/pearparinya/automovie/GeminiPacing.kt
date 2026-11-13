package com.pearparinya.automovie

import java.util.concurrent.CancellationException

/** One in-flight request across keys. A 429 cools down all Gemini keys in this process. */
internal object GeminiPacing {
    private val lock=Object()
    private var active=false
    private var nextStart=0L
    private var blockedUntil=0L
    private fun now()=android.os.SystemClock.elapsedRealtime()
    fun delayMillis(): Long = synchronized(lock) { maxOf(0L,blockedUntil-now(),nextStart-now(),if(active) 1000L else 0L) }
    fun acquire(cancelled: () -> Boolean) {
        synchronized(lock) {
            while(true) {
                if(cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
                val n=now()
                val blocked=blockedUntil-n
                if(blocked>0) throw StoryHttpFailure(429,((blocked+999)/1000).toString(),"Gemini อยู่ระหว่างพักตามโควตา")
                val wait=nextStart-n
                if(!active && wait<=0) { active=true; nextStart=n+3000L; return }
                try { lock.wait(if(active) 200L else minOf(200L,wait.coerceAtLeast(1L))) }
                catch(_: InterruptedException) { Thread.currentThread().interrupt(); throw CancellationException() }
            }
        }
    }
    fun on429(retryAfter: String?) {
        val seconds=maxOf(65L,retryAfter?.let { StoryRequestRetry.retrySeconds(it,System.currentTimeMillis()) } ?: 0L).coerceAtMost(604800L)
        synchronized(lock) { blockedUntil=maxOf(blockedUntil,now()+seconds*1000L) }
    }
    fun release() { synchronized(lock) { active=false; lock.notifyAll() } }
}
