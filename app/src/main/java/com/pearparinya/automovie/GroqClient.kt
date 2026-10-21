package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/** Fixed Groq endpoint; credentials never enter prompts, files, URLs or error messages. */
internal class GroqClient(private val apiKey: String,private val model: String,private val cancelled: () -> Boolean): StoryTextClient {
    @Volatile private var connection: HttpURLConnection?=null
    override fun cancel() { connection?.disconnect() }
    override fun generate(prompt: String,schema: JSONObject): JSONObject {
        if(cancelled()) throw CancellationException()
        val body=request(model,prompt,schema)
        val conn=URL(ENDPOINT).openConnection() as HttpURLConnection
        connection=conn
        try {
            conn.requestMethod="POST"; conn.connectTimeout=20000; conn.readTimeout=120000; conn.instanceFollowRedirects=false
            conn.setRequestProperty("Authorization","Bearer $apiKey")
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8"); conn.doOutput=true
            if(cancelled()) throw CancellationException()
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            if(status !in 200..299) throw IllegalStateException(errorFor(status))
            val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
            conn.inputStream.use { input -> while(true) {
                if(cancelled()) throw CancellationException()
                val n=input.read(buffer); if(n<0) break
                require(output.size()+n<=2_000_000) { "คำตอบ Groq ใหญ่เกินกำหนด" }; output.write(buffer,0,n)
            } }
            if(cancelled()) throw CancellationException()
            return parseResponse(JSONObject(output.toString("UTF-8")))
        } finally { conn.disconnect(); connection=null }
    }
    companion object {
        const val ENDPOINT="https://api.groq.com/openai/v1/chat/completions"
        val MODELS=listOf("openai/gpt-oss-120b","openai/gpt-oss-20b")
        fun strictSchema(schema: JSONObject): JSONObject = JSONObject(schema.toString()).apply {
            if(optString("type")=="object") {
                put("additionalProperties",false)
                val properties=getJSONObject("properties")
                properties.keys().asSequence().toList().forEach { key -> properties.put(key,strictSchema(properties.getJSONObject(key))) }
            }
            if(optString("type")=="array") put("items",strictSchema(getJSONObject("items")))
        }
        fun request(model: String,prompt: String,schema: JSONObject): JSONObject {
            require(model in MODELS) { "เลือกโมเดล Groq ที่รองรับ JSON Schema" }
            return JSONObject().put("model",model).put("stream",false).put("max_completion_tokens",if(schema.getJSONObject("properties").has("scenes")) 3072 else 4096)
                .put("reasoning_effort","low")
                .put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt)))
                .put("response_format",JSONObject().put("type","json_schema").put("json_schema",JSONObject()
                    .put("name","director_story").put("strict",true).put("schema",strictSchema(schema))))
        }
        fun parseResponse(response: JSONObject): JSONObject {
            val choice=response.optJSONArray("choices")?.optJSONObject(0) ?: error("Groq ไม่ส่งบทกลับ")
            val message=choice.getJSONObject("message")
            require(message.optString("refusal").let { it.isBlank() || it=="null" }) { "Groq ปฏิเสธคำขอนี้ กรุณาตรวจแนวคิดเรื่อง" }
            require(choice.optString("finish_reason")=="stop") { "Groq ส่งคำตอบไม่ครบ ยังไม่บันทึกช่วงนี้ กดทำต่อได้" }
            return try { JSONObject(message.getString("content")) } catch(_: Exception) { error("Groq ไม่ส่ง JSON สมบูรณ์ ยังไม่บันทึกช่วงนี้") }
        }
        fun errorFor(status: Int): String=when(status) {
            400,422 -> "Groq ไม่รับรูปแบบคำขอหรือขนาดคำสั่ง (HTTP $status) ตรวจโมเดล / อัปเดตแอป"
            401,403 -> "Groq API Key ไม่ถูกต้อง หรือบัญชีไม่มีสิทธิ์ใช้โมเดลนี้"
            404 -> "ไม่พบโมเดล Groq นี้ ตรวจตั้งค่า API"
            413 -> "คำสั่งใหญ่เกินโควตา Groq ของบัญชีนี้ งานเดิมบันทึกไว้"
            429 -> "โควตาหรืออัตราเรียก Groq เต็มแล้ว รอให้โควตาคืนแล้วกดทำต่อ ไม่มีการเปลี่ยนโมเดลหรือเปิดแผนเสียเงินอัตโนมัติ"
            in 500..599 -> "บริการ Groq ขัดข้องชั่วคราว (HTTP $status) กดทำต่อภายหลัง"
            else -> "เชื่อมต่อ Groq ไม่สำเร็จ (HTTP $status)"
        }
    }
}
