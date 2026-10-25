package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

internal class GeminiClient(private val apiKey: String, private val model: String, private val cancelled: () -> Boolean,private val onRetry: (String)->Unit={},private val automaticRetry: Boolean=true): StoryTextClient {
    @Volatile private var connection: HttpURLConnection?=null
    override fun cancel() { connection?.disconnect() }
    override fun generate(prompt: String,schema: JSONObject): JSONObject = if(automaticRetry) StoryRequestRetry().execute(cancelled,onRetry) { generateOnce(prompt,schema) } else generateOnce(prompt,schema)
    private fun generateOnce(prompt: String,schema: JSONObject): JSONObject {
        if(cancelled()) throw CancellationException()
        require(Regex("gemini-[a-zA-Z0-9.-]+").matches(model))
        val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("role","user")
            .put("parts",JSONArray().put(JSONObject().put("text",prompt)))))
            .put("generationConfig",JSONObject().put("responseMimeType","application/json").put("responseJsonSchema",schema)
                .put("maxOutputTokens",16384))
        val conn=URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        connection=conn
        try {
            conn.requestMethod="POST"; conn.connectTimeout=20000; conn.readTimeout=180000; conn.instanceFollowRedirects=false
            conn.setRequestProperty("x-goog-api-key",apiKey); conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
            conn.doOutput=true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            if(status !in 200..299) throw StoryHttpFailure(status,conn.getHeaderField("Retry-After"),errorFor(status))
            val bytes=conn.inputStream.use { it.readBytesBounded(2_000_000) }
            if(cancelled()) throw CancellationException()
            return parseResponse(JSONObject(String(bytes,Charsets.UTF_8)))
        } finally { conn.disconnect(); connection=null }
    }
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { if(cancelled()) throw CancellationException(); val count=read(buffer); if(count<0) break
            require(output.size()+count<=limit) { "คำตอบใหญ่เกินกำหนด กรุณาลองช่วงนี้ใหม่" }; output.write(buffer,0,count) }
        return output.toByteArray()
    }
    companion object {
        fun errorFor(status: Int): String = when(status) {
            400 -> "Gemini ไม่รับรูปแบบคำขอหรือโมเดลนี้ กรุณาตรวจชื่อโมเดล / อัปเดตแอป"
            401,403 -> "API Key ไม่ถูกต้อง หรือบัญชีไม่มีสิทธิ์ใช้โมเดลนี้ ตรวจคีย์และสิทธิ์ใน AI Studio"
            404 -> "ไม่พบโมเดลนี้ กรุณาแก้ชื่อโมเดลในตั้งค่า Gemini"
            429 -> "โควตาหรืออัตราเรียก Gemini เต็มแล้ว งานที่เสร็จบันทึกไว้ กดทำต่อภายหลัง ไม่มีการเปลี่ยนไปใช้โมเดลอื่นอัตโนมัติ"
            in 500..599 -> "บริการ Gemini ขัดข้องชั่วคราว กดทำต่อภายหลัง"
            else -> "เชื่อมต่อ Gemini ไม่สำเร็จ (HTTP $status)"
        }
        fun parseResponse(response: JSONObject): JSONObject {
            require(response.optJSONObject("promptFeedback")?.optString("blockReason").isNullOrBlank()) { "Gemini ไม่รับคำขอนี้ กรุณาตรวจแนวคิดเรื่อง ไม่มีการเปลี่ยนคำเพื่อเลี่ยงข้อจำกัด" }
            val candidate=response.optJSONArray("candidates")?.optJSONObject(0) ?: error("Gemini ไม่ส่งบทกลับ กรุณาลองใหม่")
            require(candidate.optString("finishReason")=="STOP") { "Gemini ส่งคำตอบไม่ครบหรือหยุดคำขอ ยังไม่บันทึกช่วงนี้ กดทำต่อเพื่อลองใหม่" }
            val parts=candidate.getJSONObject("content").getJSONArray("parts")
            val text=(0 until parts.length()).map { parts.getJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
            return try { JSONObject(text) } catch(_: Exception) { error("คำตอบ Gemini ไม่ใช่ JSON สมบูรณ์ ยังไม่บันทึกช่วงนี้") }
        }
    }
}
