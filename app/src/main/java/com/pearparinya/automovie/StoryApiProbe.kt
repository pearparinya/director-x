package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Uses the real generation endpoint/schema, not just key format or a models-list endpoint. */
internal object StoryApiProbe {
    private class InvalidProbeResponse: IllegalArgumentException("Invalid probe response")
    fun check(client: StoryTextClient) {
        val schema=JSONObject().put("type","object").put("properties",JSONObject().put("ok",JSONObject().put("type","boolean")))
            .put("required",JSONArray().put("ok"))
        val response=client.generate("Connection test only. Return exactly {\"ok\":true}. Do not write a story.",schema)
        if(response.opt("ok")!=true) throw InvalidProbeResponse()
    }
    /** Only show trusted status and parsed retry time; never include raw server text or credentials. */
    fun error(e: Exception,now: Long=System.currentTimeMillis()): String = when(e) {
        is StoryHttpFailure -> {
            val message=when(e.status) {
                401 -> "ยืนยันตัวตนไม่ผ่าน • ตรวจคีย์กับผู้ให้บริการ (ยังยืนยันวันหมดอายุไม่ได้)"
                403 -> "ไม่มีสิทธิ์ใช้งาน • ตรวจบัญชีและสิทธิ์โมเดล (ยังยืนยันวันหมดอายุไม่ได้)"
                402 -> "บัญชีหรือเครดิตไม่พร้อม • ตรวจยอดและการชำระเงิน"
                429 -> "โควตาเต็ม / เรียกถี่เกินไป • ตรวจโควตากับผู้ให้บริการ"
                404 -> "ไม่พบโมเดลหรือปลายทาง • ตรวจชื่อโมเดล"
                400,422 -> "โมเดลไม่รับคำขอหรือรูปแบบ JSON ที่กำหนด • ตรวจชื่อโมเดล"
                408 -> "หมดเวลารอจากผู้ให้บริการ • ลองใหม่"
                in 500..599 -> "บริการขัดข้องหรือไม่พร้อมชั่วคราว • ลองใหม่ภายหลัง"
                else -> "บริการยังไม่พร้อม • ตรวจบัญชีและชื่อโมเดล"
            }
            val wait=if(e.status==429 || e.status==503) e.retryAfter?.let { StoryRequestRetry.retrySeconds(it,now) } else null
            val retry=wait?.takeIf { it>0 && it<=604800 }?.let { " • ผู้ให้บริการแนะนำให้รออย่างน้อย $it วินาทีนับจากเวลาทดสอบ" }.orEmpty()
            "HTTP ${e.status} • $message$retry"
        }
        is java.net.SocketTimeoutException -> "หมดเวลารอคำตอบ • ตรวจอินเทอร์เน็ตหรือลองใหม่"
        is javax.net.ssl.SSLException -> "เชื่อมต่อแบบปลอดภัยไม่สำเร็จ • ตรวจวันเวลาและเครือข่าย"
        is java.io.IOException -> "เชื่อมต่อไม่สำเร็จ • ตรวจอินเทอร์เน็ต / ลองใหม่"
        is InvalidProbeResponse -> "ได้รับคำตอบแต่ไม่ผ่านรูปแบบทดสอบ JSON • ตรวจโมเดล"
        else -> "ทดสอบไม่ผ่าน • ตรวจคีย์ โมเดล และรูปแบบคำตอบ"
    }
}
