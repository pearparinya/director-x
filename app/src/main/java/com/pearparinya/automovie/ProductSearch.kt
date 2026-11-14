package com.pearparinya.automovie

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Search public pages; only present product URLs explicitly cited by the provider. */
internal object ProductSearch {
    data class Result(val name: String, val url: String, val detail: String)
    fun search(context: Context, query: String, platform: Int): List<Result> {
        val settings = ExtraApiSettings(context, ExtraApiProvider.OPENROUTER)
        val key = settings.key()
        require(key.isNotBlank()) { "กรุณาตั้งค่า OpenRouter API ก่อนค้นหา" }
        val names = listOf("TikTok Shop", "Shopee", "Lazada")
        require(platform in names.indices && query.isNotBlank()) { "กรุณาเลือกแพลตฟอร์มและระบุคำค้น" }
        val prompt = "ค้นเว็บหาหน้าสินค้าจริงใน ${names[platform]} ประเทศไทย สำหรับคำค้น: ${query.take(120)}. " +
            "ให้ผลไม่เกิน 5 รายการ เฉพาะหน้าสินค้ารายชิ้นที่ค้นพบจริง อ้างอิง URL ที่ค้นพบ ห้ามเดาหรือสร้าง URL. " +
            "ตอบ JSON อย่างเดียวรูปแบบ {\"products\":[{\"name\":\"ชื่อสินค้า\",\"url\":\"https://...\",\"detail\":\"รายละเอียดสั้นจากแหล่งข้อมูล\"}]}. " +
            "ถ้าไม่พบให้ products เป็นรายการว่าง ห้ามระบุราคา คะแนน รีวิว หรือแต่งข้อมูล"
        val payload = JSONObject().put("model", settings.model()).put("tools", JSONArray().put(JSONObject().put("type", "openrouter:web_search")))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val conn = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 90000; conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $key"); conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            require(code in 200..299) { "ค้นหาไม่สำเร็จ (HTTP $code) ตรวจสิทธิ์ค้นเว็บ โควตา และโมเดล OpenRouter" }
            val response = conn.inputStream.bufferedReader().use { reader ->
                val b = CharArray(8192); val out = StringBuilder()
                while (out.length < 500000) { val n = reader.read(b, 0, minOf(b.size, 500000-out.length)); if (n < 0) break; out.append(b, 0, n) }
                JSONObject(out.toString())
            }
            val message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val citations = mutableSetOf<String>()
            val annotations = message.optJSONArray("annotations") ?: JSONArray()
            for (i in 0 until annotations.length()) {
                val a = annotations.optJSONObject(i) ?: continue
                val url = a.optJSONObject("url_citation")?.optString("url").orEmpty()
                if (url.isNotBlank()) citations.add(url)
            }
            require(citations.isNotEmpty()) { "ผลค้นหาไม่มีแหล่งอ้างอิงที่ตรวจได้ กรุณาลองใหม่หรือค้นจากร้านโดยตรง" }
            val content = message.optString("content").trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val rows = JSONObject(content).optJSONArray("products") ?: JSONArray()
            val results = mutableListOf<Result>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val url = row.optString("url").trim(); val name = row.optString("name").trim().take(200)
                if (name.isNotBlank() && url in citations && matchesPlatform(url, platform) && results.none { it.url == url }) {
                    results.add(Result(name, url, row.optString("detail").trim().take(500)))
                }
                if (results.size == 5) break
            }
            return results
        } finally { conn.disconnect() }
    }
    fun matchesPlatform(url: String, platform: Int): Boolean {
        if (!ProductLinkPolicy.allowed(url)) return false
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val host = uri.host.lowercase(); val path = uri.path.orEmpty().lowercase()
        return when (platform) {
            0 -> (host == "shop-th.tiktok.com" || host.endsWith(".tiktokshop.com")) && (path.contains("/pdp/") || path.contains("/product/"))
            1 -> (host == "shopee.co.th" || host.endsWith(".shopee.co.th")) && (Regex("-i\\.\\d+\\.\\d+").containsMatchIn(path) || Regex("/product/\\d+/\\d+").containsMatchIn(path))
            2 -> (host == "lazada.co.th" || host.endsWith(".lazada.co.th")) && path.contains("/products/")
            else -> false
        }
    }
}
