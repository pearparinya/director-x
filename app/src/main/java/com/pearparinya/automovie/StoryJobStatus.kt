package com.pearparinya.automovie

import org.json.JSONObject
import org.json.JSONArray

/** Heartbeat measures app liveness/time only. Percent advances only at validated checkpoints. */
internal class StoryJobStatus(previous: JSONObject?, checkpoint: Int,
    private val elapsed: ()->Long={android.os.SystemClock.elapsedRealtime()},
    private val wall: ()->Long={System.currentTimeMillis()}) {
    private val start=elapsed()
    private val prior=(previous?.optLong("elapsed_ms") ?: 0L).coerceAtLeast(0)
    private var phaseStart=start
    private val value=JSONObject().put("state","running").put("percent",checkpoint.coerceIn(0,99))
        .put("phase","preparing").put("started_at",previous?.optLong("started_at",wall()) ?: wall())
        .put("failures",previous?.optJSONArray("failures") ?: JSONArray())
    @Synchronized fun recordFailure(provider: StoryProvider,category: String): JSONObject {
        // Never store provider payload, prompt, story text, or credentials in diagnostics.
        val safe=if(category.matches(Regex("HTTP [1-5][0-9][0-9]( • (invalid_schema|context_length_exceeded|json_validate_failed))?")) ||
            category in setOf("XML ไม่ตรงบท","หมดเวลารอ","TLS ขัดข้อง","เครือข่ายขัดข้อง","ตรวจคำตอบไม่ผ่าน")) category else "ตรวจคำตอบไม่ผ่าน"
        val old=value.getJSONArray("failures"); val next=JSONArray()
        for(i in maxOf(0,old.length()-5) until old.length()) next.put(old.getJSONObject(i))
        next.put(JSONObject().put("provider",provider.label).put("reason",safe))
        value.put("failures",next)
        return snapshot(true)
    }
    @Synchronized fun setTask(message: String) {
        val task=when {
            message.contains("เลือก MASTER") -> "เลือกตัวละคร"
            message.contains("โครงเรื่อง") -> "วางโครงเรื่อง"
            message.startsWith("เขียน EP") -> message.take(80)
            message.startsWith("บันทึกแล้ว") -> message.take(80)
            else -> null
        }
        if(task!=null) value.put("task",task)
    }
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
        val rawProvider=status.optString("provider")
        val visibleProvider=if(rawProvider.equals("cerebras",ignoreCase=true)) "API อัตโนมัติ" else rawProvider.replaceFirstChar { it.uppercaseChar() }
        val provider=if(status.optBoolean("hybrid")) "Groq + Gemini (ขณะนี้ $visibleProvider)" else visibleProvider
        val time=duration(status.optLong("elapsed_ms"))
        val phase=if(running && !stale) " • ช่วงนี้ ${duration(status.optLong("phase_elapsed_ms"))}" else ""
        val update=if(heartbeat>0) java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date(heartbeat)) else "—"
        val failures=status.optJSONArray("failures")
        val rows=if(failures==null) emptyList() else (0 until failures.length()).mapNotNull { i ->
            failures.optJSONObject(i)?.takeUnless { it.optString("provider").equals("cerebras",ignoreCase=true) }
                ?.let { "${it.optString("provider")}: ${it.optString("reason")}" }
        }
        val history=if(rows.isEmpty()) "" else "\nการลองล่าสุด:\n"+rows.joinToString("\n")
        val task=status.optString("task").takeIf { it.isNotBlank() }?.let { "\nขั้นตอน: $it" }.orEmpty()
        val message=if(status.optString("message").contains("cerebras",ignoreCase=true)) "กดทำต่อเพื่อใช้ API ที่ตั้งค่าปัจจุบัน" else status.optString("message")
        return "$provider • ซีนที่ผ่านตรวจ ${status.optInt("percent").coerceIn(0,100)}%\n$label\nใช้เวลา $time$phase$task\n${if(state=="awaiting_outline") "เปิดงานเขียนบท แล้วกดทำต่อ • ใช้โครงเรื่องเดิม ไม่ต้องยืนยันอีก" else message}$history\nแอปอัปเดตสถานะล่าสุด $update"
    }
}
