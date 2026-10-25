package com.pearparinya.automovie

import java.util.concurrent.CancellationException

/** One writer per request. Failed providers rest until their deadline; the same prompt goes to fallback. */
internal class StoryProviderRouter(
    private val cancelled: () -> Boolean,
    private val onRoute: (StoryProvider,Boolean)->Unit,
    private val now: () -> Long={ android.os.SystemClock.elapsedRealtime() },
    private val wallNow: () -> Long={ System.currentTimeMillis() }
) {
    private val blockedUntil=mutableMapOf<StoryProvider,Long>()
    fun <T> generate(preferred: StoryProvider,request: (StoryProvider)->T): T {
        val order=listOf(preferred,StoryProvider.entries.first { it!=preferred })
        for(candidate in order) {
            if(cancelled()) throw CancellationException()
            if((blockedUntil[candidate] ?: 0L)>now()) continue
            onRoute(candidate,candidate!=preferred)
            try {
                val result=request(candidate)
                if(cancelled()) throw CancellationException()
                blockedUntil.remove(candidate)
                return result
            } catch(e: Exception) {
                if(cancelled() || e is CancellationException) throw CancellationException()
                if(!StoryRequestRetry.canFallback(e)) throw e
                val suggested=(e as? StoryHttpFailure)?.retryAfter?.let { StoryRequestRetry.retrySeconds(it,wallNow()) }
                // Never retry sooner than the service asks, including a daily quota reset.
                val sampled=now()
                val seconds=maxOf(65L,suggested ?: 0L).coerceAtMost((Long.MAX_VALUE-sampled)/1000)
                blockedUntil[candidate]=sampled+seconds*1000
            }
        }
        val earliest=order.minOf { blockedUntil[it] ?: now() }
        val seconds=((earliest-now()).coerceAtLeast(0)+999)/1000
        throw StoryHttpFailure(429,seconds.toString(),"ทั้ง Groq และ Gemini ยังไม่พร้อม • รอแล้วลองต่ออัตโนมัติ")
    }
    companion object {
        fun preferred(sceneBatch: Boolean)=if(sceneBatch) StoryProvider.GROQ else StoryProvider.GEMINI
    }
}
