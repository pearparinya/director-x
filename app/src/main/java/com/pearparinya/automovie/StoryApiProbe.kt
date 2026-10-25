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
            401,403 -> "คีย์ไม่ถูกต้อง หรือไม่มีสิทธิ์ใช้โมเดล"
            429 -> "โควตาเต็ม / เรียกถี่เกินไป • ยังยืนยันว่าพร้อมเขียนไม่ได้"
            404 -> "ไม่พบโมเดลที่ตั้งไว้"
            400,422 -> "โมเดลไม่รับคำขอ / JSON Schema นี้"
            else -> "บริการตอบ HTTP ${e.status} • ยังไม่พร้อม"
        }
        is java.io.IOException -> "เชื่อมต่อไม่สำเร็จ ตรวจอินเทอร์เน็ต / ลองใหม่"
        else -> "ทดสอบไม่ผ่าน • ตรวจคีย์ โมเดล และรูปแบบคำตอบ"
    }
}
