package com.pearparinya.automovie

import java.util.concurrent.CancellationException

/** One in-flight request across keys. A 429 cools down only the affected key, assuming each key uses a separate project. */
internal object GeminiPacing {
    private val lock=Object()
    private var active=false
    private var nextStart=0L
    private val blockedUntil=mutableMapOf<String,Long>()
    private fun fingerprint(key: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun now()=android.os.SystemClock.elapsedRealtime()
    fun keyCooldownMillis(key: String): Long = synchronized(lock) { maxOf(0L,(blockedUntil[fingerprint(key)] ?: 0L)-now()) }
    fun delayMillis(key: String): Long = synchronized(lock) { maxOf(0L,(blockedUntil[fingerprint(key)] ?: 0L)-now(),nextStart-now(),if(active) 1000L else 0L) }
    fun acquire(key: String,cancelled: () -> Boolean) {
        synchronized(lock) {
            while(true) {
                if(cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
                val n=now()
                val blocked=(blockedUntil[fingerprint(key)] ?: 0L)-n
                if(blocked>0) throw StoryHttpFailure(429,((blocked+999)/1000).toString(),"Gemini อยู่ระหว่างพักตามโควตา")
                val wait=nextStart-n
                if(!active && wait<=0) { active=true; nextStart=n+3000L; return }
                try { lock.wait(if(active) 200L else minOf(200L,wait.coerceAtLeast(1L))) }
                catch(_: InterruptedException) { Thread.currentThread().interrupt(); throw CancellationException() }
            }
        }
    }
    fun on429(key: String,retryAfter: String?) {
        val seconds=maxOf(65L,retryAfter?.let { StoryRequestRetry.retrySeconds(it,System.currentTimeMillis()) } ?: 0L).coerceAtMost(604800L)
        synchronized(lock) { val id=fingerprint(key); blockedUntil[id]=maxOf(blockedUntil[id] ?: 0L,now()+seconds*1000L) }
    }
    fun release() { synchronized(lock) { active=false; lock.notifyAll() } }
}
