package com.pearparinya.automovie

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Grounded search only: display source titles and product links returned by Google, never model-invented URLs. */
internal object GeminiProductSearch {
    fun search(context: Context, query: String, platform: Int, key: String, control: ProductSearchControl = ProductSearchControl(), model: String = GeminiSettings.firstModel(context)): List<ProductSearch.Result> {
        val stores = listOf("TikTok Shop Thailand", "Shopee Thailand", "Lazada Thailand")
        require(platform in stores.indices && query.isNotBlank()) { "กรุณาเลือกแพลตฟอร์มและระบุคำค้น" }
        require(Regex("gemini-[A-Za-z0-9.-]+").matches(model)) { "โมเดล Gemini ไม่ถูกต้อง" }
        val prompt = "ค้นหาหน้าสินค้ารายชิ้นที่มีอยู่จริงบน ${stores[platform]} ประเทศไทย สำหรับ ${query.take(120)} จำนวนไม่เกิน 5 ชิ้น แสดงแหล่งข้อมูลจากเว็บเท่านั้น ห้ามแต่งสินค้า"
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 90000; conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("x-goog-api-key", key)
        control.attach(conn)
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if(code !in 200..299) throw ProductSearchError(when(code) {
                401,403 -> "คีย์หรือสิทธิ์ไม่พร้อม (HTTP $code)"
                404 -> "โมเดลไม่พร้อมใช้งาน (HTTP 404)"
                429 -> "โควตาหรืออัตราเรียกเต็ม (HTTP 429)"
                in 500..599 -> "บริการขัดข้องชั่วคราว (HTTP $code)"
                else -> "ค้นหาไม่สำเร็จ (HTTP $code)"
            })
            val response = conn.inputStream.bufferedReader().use { reader ->
                val chars = CharArray(8192); val out = StringBuilder()
                while (out.length < 500000) { val n=reader.read(chars,0,minOf(chars.size,500000-out.length)); if(n<0) break; out.append(chars,0,n) }
                JSONObject(out.toString())
            }
            val chunks=response.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: JSONArray()
            if(chunks.length()==0) throw ProductSearchError("ผลค้นหาไม่มีแหล่งอ้างอิงจาก Google")
            val results=mutableListOf<ProductSearch.Result>()
            for(i in 0 until chunks.length()) {
                control.check()
                val web=chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val source=web.optString("uri"); val title=web.optString("title").trim().take(200)
                val url=runCatching { resolveSource(source, control) }.getOrDefault("")
                if(title.isNotBlank() && ProductSearch.matchesPlatform(url,platform) && results.none { it.url==url }) results.add(ProductSearch.Result(title,url,""))
                if(results.size==5) break
            }
            if(results.isEmpty()) throw ProductSearchError("พบแหล่งอ้างอิง แต่ไม่พบหน้าสินค้าที่ตรวจลิงก์ได้ในร้านที่เลือก")
            return results
        } finally { control.detach(conn); conn.disconnect() }
    }
    private fun resolveSource(source: String, control: ProductSearchControl): String {
        var current=source
        repeat(5) {
            control.check()
            if(ProductLinkPolicy.allowed(current)) return current
            val uri=URI(current)
            require(uri.scheme.equals("https",true) && uri.userInfo==null && (uri.port==-1 || uri.port==443) && uri.host.equals("vertexaisearch.cloud.google.com",true))
            val connection=URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false; connection.connectTimeout=8000; connection.readTimeout=8000
            control.attach(connection)
            try {
                require(connection.responseCode in 300..399)
                current=URL(URL(current),connection.getHeaderField("Location") ?: error("missing location")).toString()
            } finally { control.detach(connection); connection.disconnect() }
        }
        return ""
    }
}
