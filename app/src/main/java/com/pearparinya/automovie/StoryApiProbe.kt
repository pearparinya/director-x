package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Uses the real generation endpoint/schema, not just key format or a models-list endpoint. */
internal object StoryApiProbe {
    fun check(client: StoryTextClient) {
        val schema=JSONObject().put("type","object").put("properties",JSONObject().put("ok",JSONObject().put("type","boolean")))
            .put("required",JSONArray().put("ok"))
        val response=client.generate("Connection test only. Return exactly {\"ok\":true}. Do not write a story.",schema)
        require(response.opt("ok")==true) { "เชื่อมต่อได้ แต่คำตอบทดสอบไม่ผ่านรูปแบบ" }
    }
    fun error(e: Exception): String=when(e) {
        is StoryHttpFailure -> when(e.status) {
            401 -> "ยืนยันตัวตนไม่ผ่าน • ตรวจคีย์หรือสถานะคีย์กับผู้ให้บริการ (ยังยืนยันวันหมดอายุไม่ได้)"
            403 -> "ไม่มีสิทธิ์ใช้งาน • ตรวจคีย์ บัญชี และสิทธิ์โมเดล (ยังยืนยันวันหมดอายุไม่ได้)"
            402 -> "บัญชีหรือเครดิตไม่พร้อม • ตรวจการชำระเงินกับผู้ให้บริการ"
            429 -> "โควตาเต็ม / เรียกถี่เกินไป • ยังยืนยันว่าพร้อมเขียนไม่ได้"
            404 -> "ไม่พบโมเดลหรือปลายทางที่ตั้งไว้"
            400,422 -> "โมเดลไม่รับคำขอ / JSON Schema นี้"
            500,502,503,504 -> "บริการขัดข้องหรือไม่พร้อมชั่วคราว • ลองใหม่ภายหลัง"
            else -> "บริการตอบ HTTP ${e.status} • ยังไม่พร้อม"
        }
        is java.io.IOException -> "เชื่อมต่อไม่สำเร็จ ตรวจอินเทอร์เน็ต / ลองใหม่"
        else -> "ทดสอบไม่ผ่าน • ตรวจคีย์ โมเดล และรูปแบบคำตอบ"
    }
}
