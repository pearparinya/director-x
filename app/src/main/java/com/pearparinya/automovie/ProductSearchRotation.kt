package com.pearparinya.automovie

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.net.HttpURLConnection

internal class ProductSearchControl {
    private val stopped=AtomicBoolean(false)
    private val active=AtomicReference<HttpURLConnection?>(null)
    fun check() { if(stopped.get()) throw CancellationException("ยกเลิกการค้นหา") }
    fun attach(connection: HttpURLConnection) { check(); active.set(connection); if(stopped.get()) { connection.disconnect(); check() } }
    fun detach(connection: HttpURLConnection) { active.compareAndSet(connection,null) }
    fun cancel() { stopped.set(true); active.getAndSet(null)?.disconnect() }
    fun cancelled() = stopped.get()
}

/** Try configured independent projects in order; no credentials appear in status or failures. */
internal object ProductSearchRotation {
    data class Attempt(val label: String, val run: () -> List<ProductSearch.Result>)
    data class Outcome(val results: List<ProductSearch.Result>, val failures: List<String>)
    fun run(attempts: List<Attempt>, control: ProductSearchControl, progress: (Int,Int,String,String) -> Unit): Outcome {
        val failures=mutableListOf<String>()
        attempts.forEachIndexed { index, attempt ->
            control.check(); progress(index+1,attempts.size,attempt.label,"กำลังค้นหา")
            val result=try { attempt.run() } catch(e: Exception) {
                control.check()
                failures.add("${attempt.label}: ${friendly(e)}")
                progress(index+1,attempts.size,attempt.label,"${friendly(e)} • ลองชุดถัดไป")
                emptyList()
            }
            control.check()
            if(result.isNotEmpty()) return Outcome(result,failures)
            if(failures.size==0 || !failures.last().startsWith("${attempt.label}:")) failures.add("${attempt.label}: ไม่พบหน้าสินค้าที่ตรวจแหล่งได้")
        }
        return Outcome(emptyList(),failures)
    }
    fun friendly(e: Throwable): String = when(e) {
        is java.net.SocketTimeoutException -> "การเชื่อมต่อหมดเวลา"
        is java.net.UnknownHostException -> "เชื่อมต่อเครือข่ายไม่ได้"
        is java.io.IOException -> "เชื่อมต่อบริการไม่สำเร็จ"
        is ProductSearchError -> e.publicMessage
        else -> "บริการไม่พร้อมหรือข้อมูลตอบกลับไม่สมบูรณ์"
    }
}
internal class ProductSearchError(val publicMessage: String): Exception(publicMessage)
