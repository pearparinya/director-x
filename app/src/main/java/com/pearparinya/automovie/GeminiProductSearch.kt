package com.pearparinya.automovie

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Grounded search only: display source titles and product links returned by Google, never model-invented URLs. */
internal object GeminiProductSearch {
    fun search(context: Context, query: String, platform: Int, key: String): List<ProductSearch.Result> {
        val stores = listOf("TikTok Shop Thailand", "Shopee Thailand", "Lazada Thailand")
        require(platform in stores.indices && query.isNotBlank()) { "กรุณาเลือกแพลตฟอร์มและระบุคำค้น" }
        val model = GeminiSettings.firstModel(context)
        require(Regex("gemini-[A-Za-z0-9.-]+").matches(model)) { "โมเดล Gemini ไม่ถูกต้อง" }
        val prompt = "ค้นหาหน้าสินค้ารายชิ้นที่มีอยู่จริงบน ${stores[platform]} ประเทศไทย สำหรับ ${query.take(120)} จำนวนไม่เกิน 5 ชิ้น แสดงแหล่งข้อมูลจากเว็บเท่านั้น ห้ามแต่งสินค้า"
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 90000; conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("x-goog-api-key", key)
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            require(code in 200..299) { "Gemini ใช้งานค้นเว็บไม่ได้ (HTTP $code) ตรวจโมเดล สิทธิ์ หรือโควตา" }
            val response = conn.inputStream.bufferedReader().use { reader ->
                val chars = CharArray(8192); val out = StringBuilder()
                while (out.length < 500000) { val n=reader.read(chars,0,minOf(chars.size,500000-out.length)); if(n<0) break; out.append(chars,0,n) }
                JSONObject(out.toString())
            }
            val chunks=response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: JSONArray()
            val results=mutableListOf<ProductSearch.Result>()
            for(i in 0 until chunks.length()) {
                val web=chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val source=web.optString("uri"); val title=web.optString("title").trim().take(200)
                val url=runCatching { resolveSource(source) }.getOrDefault("")
                if(title.isNotBlank() && ProductSearch.matchesPlatform(url,platform) && results.none { it.url==url }) results.add(ProductSearch.Result(title,url,""))
                if(results.size==5) break
            }
            return results
        } finally { conn.disconnect() }
    }
    private fun resolveSource(source: String): String {
        var current=source
        repeat(5) {
            if(ProductLinkPolicy.allowed(current)) return current
            val uri=URI(current)
            require(uri.scheme.equals("https",true) && uri.userInfo==null && (uri.port==-1 || uri.port==443) && uri.host.equals("vertexaisearch.cloud.google.com",true))
            val connection=URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false; connection.connectTimeout=8000; connection.readTimeout=8000
            try {
                require(connection.responseCode in 300..399)
                current=URL(URL(current),connection.getHeaderField("Location") ?: error("missing location")).toString()
            } finally { connection.disconnect() }
        }
        return ""
    }
}
