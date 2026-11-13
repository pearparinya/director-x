package com.pearparinya.automovie

import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Optional OpenAI image analysis. Credentials and image bytes are never logged. */
internal class OpenAiVisionClient(private val key: String, private val model: String) {
    fun buildRequest(prompt: String, schema: JSONObject, images: List<Pair<String,File>>): JSONObject {
        require(key.isNotBlank()) { "ตั้งค่า OpenAI API Key ก่อน" }
        require(Regex("[A-Za-z0-9_.:/-]{1,120}").matches(model)) { "ชื่อโมเดล OpenAI ไม่ถูกต้อง" }
        require(images.isNotEmpty() && images.size<=8) { "ต้องมี MASTER จริง 1–8 ภาพ" }
        val hashes=ReferenceSnapshot.capture(images)
        val content=JSONArray().put(JSONObject().put("type","text").put("text",
            prompt+"\nReturn a single valid JSON object following this schema exactly, with no Markdown: "+schema))
        var total=0L
        for((id,file) in images) {
            require(file.isFile && file.length() in 1..5_000_000L) { "MASTER หายหรือใหญ่กว่า 5 MB: $id" }
            total+=file.length()
            require(total<=15_000_000L) { "ภาพ MASTER รวมเกิน 15 MB" }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(file.path,bounds)
            val mime=bounds.outMimeType
            require(bounds.outWidth>0 && bounds.outHeight>0 && mime in listOf("image/jpeg","image/png","image/webp")) { "ภาพ MASTER อ่านไม่ได้หรือชนิดไม่รองรับ: $id" }
            val bytes=file.readBytes()
            require(MasterLibrary.digest(bytes)==hashes[id]) { "MASTER เปลี่ยนระหว่างเตรียมส่ง: $id" }
            content.put(JSONObject().put("type","text").put("text","MASTER ID: $id. The next image belongs only to this character."))
            val data="data:$mime;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
            content.put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url",data)))
        }
        return JSONObject().put("model",model).put("stream",false)
            .put("response_format",JSONObject().put("type","json_object"))
            .put("messages",JSONArray().put(JSONObject().put("role","user").put("content",content)))
    }
    fun generate(prompt: String, schema: JSONObject, images: List<Pair<String,File>>): JSONObject {
        val body=buildRequest(prompt,schema,images)
        val conn=URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST"; conn.connectTimeout=20000; conn.readTimeout=180000
            conn.instanceFollowRedirects=false; conn.doOutput=true
            conn.setRequestProperty("Authorization","Bearer $key")
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            require(status in 200..299) { "OpenAI ไม่พร้อมใช้งาน (HTTP $status) • ตรวจคีย์ โมเดล สิทธิ์ หรือโควตา" }
            val out=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            conn.inputStream.use { input -> while(true) {
                val n=input.read(buffer); if(n<0) break
                require(out.size()+n<=2_000_000) { "คำตอบ OpenAI ใหญ่เกินกำหนด" }
                out.write(buffer,0,n)
            } }
            return CompatibleStoryClient.parse(JSONObject(out.toString("UTF-8")),CompatibleService.OPENAI)
        } finally { conn.disconnect() }
    }
}
