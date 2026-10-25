package com.pearparinya.automovie

import java.io.IOException
import java.util.concurrent.CancellationException

/** Retry only transport failures, throttling and temporary HTTP failures; never invalid content. */
internal class StoryHttpFailure(val status: Int, val retryAfter: String?, message: String,val detail: String=""): IllegalStateException(message)

internal class StoryRequestRetry(
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    private val wallNow: () -> Long = { System.currentTimeMillis() }
) {
    fun <T> execute(cancelled: () -> Boolean, onWait: (String) -> Unit, request: () -> T): T {
        var retries=0
        while(true) {
            if(cancelled()) throw CancellationException()
            try { return request() } catch(e: Exception) {
                if(cancelled() || e is CancellationException) throw CancellationException()
                val temporary=canRetry(e)
                if(!temporary) throw e
                if(retries>=MAX_RETRIES) throw IllegalStateException("ลองทำต่ออัตโนมัติครบ $MAX_RETRIES ครั้งแล้ว • บันทึกช่วงที่เสร็จไว้ ตรวจอินเทอร์เน็ต / โควตาแล้วกดทำต่อ")
                val fallback=65L shl retries
                val suggested=(e as? StoryHttpFailure)?.retryAfter?.let { retrySeconds(it,wallNow()) }
                val seconds=maxOf(fallback,suggested ?: 0L)
                if(seconds>MAX_WAIT_SECONDS) throw IllegalStateException("บริการขอให้รอนานกว่า 15 นาที • บันทึกช่วงที่เสร็จไว้ รอโควตาคืนแล้วกดทำต่อ")
                retries++
                val deadline=now()+seconds*1000L
                var reported=-1L
                while(true) {
                    if(cancelled()) throw CancellationException()
                    val remaining=deadline-now(); if(remaining<=0) break
                    val left=(remaining+999)/1000
                    if(left!=reported) {
                        val reason=when {
                            e is StoryHttpFailure && e.status==429 -> "รอโควตา"
                            e is StoryHttpFailure && e.detail=="json_validate_failed" -> "คำตอบ JSON ไม่ผ่านรูปแบบ"
                            else -> "บริการหรือการเชื่อมต่อขัดข้องชั่วคราว"
                        }
                        onWait("$reason • ทำต่ออัตโนมัติใน $left วินาที • ครั้งที่ $retries/$MAX_RETRIES • กดหยุดได้")
                        reported=left
                    }
                    pause(minOf(250L,remaining))
                }
                if(cancelled()) throw CancellationException()
                onWait("กำลังทำต่ออัตโนมัติ • ครั้งที่ $retries/$MAX_RETRIES")
            }
        }
    }
    companion object {
        fun canRetry(e: Exception): Boolean = e is IOException && e !is javax.net.ssl.SSLException ||
            e is StoryHttpFailure && (e.status==408 || e.status==429 || e.status in 500..599 || e.detail=="json_validate_failed")
        fun canFallback(e: Exception): Boolean = canRetry(e) || e is StoryHttpFailure &&
            (e.status==413 || e.detail=="context_length_exceeded")
        const val MAX_RETRIES=4
        const val MAX_WAIT_SECONDS=900L
        fun retrySeconds(value: String,now: Long): Long? {
            value.trim().toDoubleOrNull()?.let { if(it.isFinite() && it>=0) return kotlin.math.ceil(it).toLong() }
            return runCatching {
                val time=java.time.ZonedDateTime.parse(value.trim(),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                ((time-now).coerceAtLeast(0)+999)/1000
            }.getOrNull()
        }
    }
}
