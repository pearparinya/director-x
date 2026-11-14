package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/** Fixed endpoints; provider errors and credentials are never copied into job files or UI. */
internal enum class CompatibleService(val label: String, val url: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4.1-mini"),
    CEREBRAS("Cerebras", "https://api.cerebras.ai/v1/chat/completions", "gpt-oss-120b"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-flash"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openrouter/free"),
    SAMBANOVA("SambaNova", "https://api.sambanova.ai/v1/chat/completions", "Meta-Llama-3.3-70B-Instruct"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest"),
    COHERE("Cohere", "https://api.cohere.ai/compatibility/v1/chat/completions", "command-a-03-2025")
}

internal class InvalidStoryResponse(message: String): IllegalStateException(message)

internal class CompatibleStoryClient(
    private val service: CompatibleService,
    private val apiKey: String,
    private val model: String,
    private val cancelled: () -> Boolean,
    private val onRetry: (String) -> Unit = {},
    private val automaticRetry: Boolean = true
) : StoryTextClient {
    @Volatile private var connection: HttpURLConnection? = null
    override fun cancel() { connection?.disconnect() }
    private var connectTimeoutMillis=20000
    private var readTimeoutMillis=180000
    override fun useProposalTimeouts() { connectTimeoutMillis=10000; readTimeoutMillis=25000 }
    override fun generate(prompt: String, schema: JSONObject): JSONObject =
        if (automaticRetry) StoryRequestRetry().execute(cancelled, onRetry) { request(prompt, schema) }
        else request(prompt, schema)

    private fun request(prompt: String, schema: JSONObject): JSONObject {
        if (cancelled()) throw CancellationException()
        require(service !in setOf(CompatibleService.DEEPSEEK, CompatibleService.MISTRAL, CompatibleService.SAMBANOVA, CompatibleService.CEREBRAS)) { "API นี้ไม่เปิดใช้งานแล้ว" }
        require(apiKey.isNotBlank()) { "ตั้งค่า ${service.label} API Key ก่อนเริ่ม" }
        val body = buildRequest(service, model, prompt, schema)
        val conn = URL(service.url).openConnection() as HttpURLConnection
        connection = conn
        try {
            conn.requestMethod = "POST"; conn.connectTimeout = connectTimeoutMillis; conn.readTimeout = readTimeoutMillis
            conn.instanceFollowRedirects = false; conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status !in 200..299) throw StoryHttpFailure(status, conn.getHeaderField("Retry-After"), if(service==CompatibleService.DEEPSEEK && status==402) "DeepSeek: ยอดเงิน API ไม่เพียงพอ (HTTP 402) • ข้ามบริการนี้" else "${service.label} ไม่พร้อมใช้งาน (HTTP $status) • ตรวจคีย์ โมเดล หรือโควตา")
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            conn.inputStream.use { input -> while (true) {
                if (cancelled()) throw CancellationException()
                val n = input.read(buffer); if (n < 0) break
                require(out.size() + n <= 2_000_000) { "คำตอบ ${service.label} ใหญ่เกินกำหนด" }
                out.write(buffer, 0, n)
            } }
            return parse(JSONObject(out.toString("UTF-8")), service)
        } finally { conn.disconnect(); connection = null }
    }

    companion object {
        fun buildRequest(service: CompatibleService, model: String, prompt: String, schema: JSONObject): JSONObject {
            require(Regex("[A-Za-z0-9_.:/-]{1,120}").matches(model)) { "ชื่อโมเดลไม่ถูกต้อง" }
            if (service == CompatibleService.OPENROUTER) require(model == "openrouter/free" || model.endsWith(":free")) { "โหมด OpenRouter อนุญาตเฉพาะโมเดลฟรี" }
            return JSONObject().put("model", model).put("stream", false)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content",
                    "$prompt\nReturn a single valid JSON object following this JSON schema exactly. No Markdown. JSON schema:\n$schema")))
                .put("response_format", JSONObject().put("type", "json_object"))
        }
        fun parse(response: JSONObject, service: CompatibleService): JSONObject {
            val choice = response.optJSONArray("choices")?.optJSONObject(0) ?: error("${service.label} ไม่ส่งคำตอบ")
            val message = choice.optJSONObject("message") ?: error("${service.label} ไม่ส่งข้อความ")
            require(message.optString("refusal").let { it.isBlank() || it == "null" }) { "${service.label} ปฏิเสธคำขอ" }
            if(choice.optString("finish_reason") == "length") throw InvalidStoryResponse("${service.label} ส่งคำตอบไม่ครบ")
            require(choice.optString("finish_reason") == "stop") { "${service.label} หยุดคำขอ กรุณาตรวจรายละเอียด" }
            return try { JSONObject(message.getString("content")) } catch (_: Exception) { throw InvalidStoryResponse("${service.label} ส่ง JSON ไม่สมบูรณ์") }
        }
    }
}
