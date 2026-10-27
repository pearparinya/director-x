package com.pearparinya.automovie

import org.json.JSONObject

/** Heartbeat measures app liveness/time only. Percent advances only at validated checkpoints. */
internal class StoryJobStatus(previous: JSONObject?, checkpoint: Int,
    private val elapsed: ()->Long={android.os.SystemClock.elapsedRealtime()},
    private val wall: ()->Long={System.currentTimeMillis()}) {
    private val start=elapsed()
    private val prior=(previous?.optLong("elapsed_ms") ?: 0L).coerceAtLeast(0)
    private var phaseStart=start
    private val value=JSONObject().put("state","running").put("percent",checkpoint.coerceIn(0,99))
        .put("phase","preparing").put("started_at",previous?.optLong("started_at",wall()) ?: wall())
    @Synchronized fun update(state: String,percent: Int,message: String,provider: String,phase: String): JSONObject {
        if(value.optString("phase")!=phase || value.optString("provider")!=provider) phaseStart=elapsed()
        value.put("state",state).put("percent",if(state=="complete") 100 else maxOf(value.optInt("percent"),percent.coerceIn(0,99)))
            .put("message",message).put("provider",provider).put("phase",phase).put("updated",wall())
        return snapshot(true)
    }
    @Synchronized fun heartbeat(): JSONObject = snapshot(value.optString("state")=="running")
    private fun snapshot(tick: Boolean): JSONObject {
        if(tick) value.put("elapsed_ms",prior+(elapsed()-start).coerceAtLeast(0))
            .put("phase_elapsed_ms",(elapsed()-phaseStart).coerceAtLeast(0)).put("heartbeat_at",wall())
        return JSONObject(value.toString())
    }
}

internal object StoryStatusText {
    fun duration(ms: Long): String {
        val seconds=ms.coerceAtLeast(0)/1000
        return "%02d:%02d:%02d".format(java.util.Locale.ROOT,seconds/3600,seconds/60%60,seconds%60)
    }
    fun format(status: JSONObject,alive: Boolean,now: Long=System.currentTimeMillis()): String {
        val state=status.optString("state")
        val running=state=="running" && alive
        val heartbeat=status.optLong("heartbeat_at",status.optLong("updated"))
        val stale=running && heartbeat>0 && now-heartbeat>15_000
        val label=when {
            state=="running" && !alive -> "งานถูกปิด • กดทำต่อจากช่วงที่บันทึกไว้"
            stale -> "สถานะขาดการอัปเดต • ตรวจงานก่อนทำต่อ"
            state=="running" -> when(status.optString("phase")) {
                "requesting" -> "กำลังรอคำตอบ API • ทำต่ออัตโนมัติ"
                "waiting","retry_wait" -> "กำลังพักรอ • ครบเวลาแล้วทำต่อเอง"
                "saved" -> "บันทึกช่วงบทแล้ว • กำลังทำต่อ"
                else -> "กำลังเตรียม / ตรวจบท"
            }
            state=="complete" -> "เสร็จสมบูรณ์"
            state=="awaiting_outline" -> "โครงเรื่องบันทึกแล้ว • กดทำต่อเพื่อเขียนบทอัตโนมัติ"
            state=="paused" -> "หยุดแล้ว • กดทำต่อได้"
            else -> "หยุดเพราะข้อผิดพลาด • ตรวจรายละเอียด"
        }
        val provider=if(status.optBoolean("hybrid")) "Groq + Gemini (ขณะนี้ ${status.optString("provider")})" else status.optString("provider").replaceFirstChar { it.uppercaseChar() }
        val time=duration(status.optLong("elapsed_ms"))
        val phase=if(running && !stale) " • ช่วงนี้ ${duration(status.optLong("phase_elapsed_ms"))}" else ""
        val update=if(heartbeat>0) java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date(heartbeat)) else "—"
        return "$provider • ซีนที่ผ่านตรวจ ${status.optInt("percent").coerceIn(0,100)}%\n$label\nใช้เวลา $time$phase\n${if(state=="awaiting_outline") "เปิดงานเขียนบท แล้วกดทำต่อ • ใช้โครงเรื่องเดิม ไม่ต้องยืนยันอีก" else status.optString("message")}\nแอปอัปเดตสถานะล่าสุด $update"
    }
}
