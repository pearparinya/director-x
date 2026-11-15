package com.pearparinya.automovie

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.TimeZone
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local limits are conservative estimates, not the provider's account balance. */
internal class FreeSearchSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("free_product_search", Context.MODE_PRIVATE)
    private fun secret(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("director_free_search",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("director_free_search",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(provider: String, value: String) {
        val key=value.trim(); require(key.length in 12..1024 && key.none { it.isWhitespace() }) { "ตรวจ API Key อีกครั้ง" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,secret())
        prefs.edit().putString(provider+"_key",Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).putString(provider+"_iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).apply()
    }
    fun key(provider: String): String = runCatching {
        val data=prefs.getString(provider+"_key",null) ?: return ""
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,Base64.decode(prefs.getString(provider+"_iv",null),Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),Charsets.UTF_8)
    }.getOrDefault("")
    fun clear(provider: String) { prefs.edit().remove(provider+"_key").remove(provider+"_iv").apply() }
    private fun month()=SimpleDateFormat("yyyy-MM",Locale.US).format(Date())
    private fun period(provider: String): String = if(provider=="langsearch") SimpleDateFormat("yyyy-MM-dd",Locale.US).apply { timeZone=TimeZone.getTimeZone("UTC") }.format(Date()) else month()
    fun used(provider: String): Int = if(prefs.getString(provider+"_month","")==period(provider)) prefs.getInt(provider+"_used",0) else 0
    fun ceiling(provider: String): Int = when(provider) { "tavily"->900; "exa"->1000; "serpapi"->200; "firecrawl"->400; "langsearch"->20; else -> 0 }
    fun limit(provider: String): Int = prefs.getInt(provider+"_limit",ceiling(provider)).coerceIn(0,ceiling(provider))
    fun setLimit(provider: String, value: Int) { require(provider in PROVIDERS.map { it.first } && value in 0..ceiling(provider)); prefs.edit().putInt(provider+"_limit",value).apply() }
    fun reserve(provider: String) = synchronized(LOCK) {
        val count=used(provider); if(count>=limit(provider)) throw ProductSearchError("ถึงเพดานที่ตั้งไว้บนเครื่อง")
        check(prefs.edit().putString(provider+"_month",period(provider)).putInt(provider+"_used",count+1).commit()) { "บันทึกโควตาไม่สำเร็จ" }
    }
    companion object { private val LOCK=Any(); val PROVIDERS=listOf("tavily" to "Tavily", "exa" to "Exa", "serpapi" to "SerpApi", "firecrawl" to "Firecrawl", "langsearch" to "LangSearch") }
}

internal object FreeProductSearch {
    fun search(context: Context, provider: String, query: String, store: Int, control: ProductSearchControl): List<ProductSearch.Result> {
        val settings=FreeSearchSettings(context); val key=settings.key(provider)
        if(key.isBlank()) throw ProductSearchError("ยังไม่ได้ตั้ง API Key")
        val domains=listOf(listOf("tiktokshop.com", "shop-th.tiktok.com"),listOf("shopee.co.th"),listOf("lazada.co.th"))
        require(store in domains.indices)
        require(provider in FreeSearchSettings.PROVIDERS.map { it.first }) { "ไม่รู้จักบริการค้นหา" }
        val scopedQuery=query.take(120)+" site:"+domains[store].joinToString(" OR site:")
        val request=JSONObject().put("query",scopedQuery)
        val endpoint=when(provider) {
            "tavily" -> { request.put("search_depth","basic").put("max_results",10).put("auto_parameters",false).put("include_domains",JSONArray(domains[store])); "https://api.tavily.com/search" }
            "exa" -> { request.put("type","lite").put("numResults",10).put("includeDomains",JSONArray(domains[store])); "https://api.exa.ai/search" }
            "firecrawl" -> { request.put("limit",10).put("includeDomains",JSONArray(domains[store])).put("country","TH"); "https://api.firecrawl.dev/v2/search" }
            "langsearch" -> { request.put("count",10).put("includeDomains",JSONArray(domains[store])); "https://api.langsearch.com/v1/web-search" }
            else -> "https://serpapi.com/search?engine=google&hl=th&gl=th&q="+URLEncoder.encode(scopedQuery,"UTF-8")+"&api_key="+URLEncoder.encode(key,"UTF-8")
        }
        // Counters are local estimates; external usage and billing cannot be verified here.
        settings.reserve(provider)
        val conn=URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod=if(provider=="serpapi") "GET" else "POST"; conn.instanceFollowRedirects=false
        conn.connectTimeout=20000; conn.readTimeout=45000; conn.doOutput=provider!="serpapi"
        conn.setRequestProperty("Content-Type","application/json")
        if(provider=="exa") conn.setRequestProperty("x-api-key",key) else if(provider!="serpapi") conn.setRequestProperty("Authorization","Bearer $key")
        control.attach(conn)
        try {
            if(provider!="serpapi") conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code=conn.responseCode
            if(code !in 200..299) throw ProductSearchError(when(code) { 401,403 -> "คีย์หรือสิทธิ์ไม่พร้อม (HTTP $code)"; 402,429 -> "โควตาหรืออัตราเรียกไม่พร้อม (HTTP $code)"; else -> "บริการค้นหาขัดข้อง (HTTP $code)" })
            val body=conn.inputStream.bufferedReader().use { reader ->
                val buffer=CharArray(8192); val result=StringBuilder()
                while(result.length<500000) { val n=reader.read(buffer,0,minOf(buffer.size,500000-result.length)); if(n<0) break; result.append(buffer,0,n) }
                result.toString()
            }
            val obj=runCatching { JSONObject(body) }.getOrElse { throw ProductSearchError("ข้อมูลตอบกลับไม่สมบูรณ์") }
            if(obj.has("error") || (provider=="firecrawl" && !obj.optBoolean("success",false)) || (provider=="langsearch" && obj.optInt("code",200)!=200)) throw ProductSearchError("บริการแจ้งข้อผิดพลาด ตรวจคีย์และโควตา")
            val validShape=when(provider) {
                "tavily", "exa" -> obj.optJSONArray("results")!=null
                "serpapi" -> obj.optJSONObject("search_metadata")!=null || obj.optJSONArray("organic_results")!=null
                "firecrawl", "langsearch" -> obj.optJSONObject("data")!=null
                else -> false
            }
            if(!validShape) throw ProductSearchError("รูปแบบคำตอบจากบริการไม่ถูกต้อง")
            return parseResults(provider,obj,store) { control.check() }
        } finally { control.detach(conn); conn.disconnect() }
    }

    internal fun parseResults(provider: String, obj: JSONObject, store: Int, check: () -> Unit = {}): List<ProductSearch.Result> {
        val rows=when(provider) {
            "serpapi" -> obj.optJSONArray("organic_results")
            "firecrawl" -> obj.optJSONObject("data")?.optJSONArray("web")
            "langsearch" -> obj.optJSONObject("data")?.optJSONObject("webPages")?.optJSONArray("value")
            else -> obj.optJSONArray("results")
        } ?: JSONArray()
        val found=mutableListOf<ProductSearch.Result>()
        for(i in 0 until rows.length()) {
            check(); val row=rows.optJSONObject(i) ?: continue
            val url=row.optString(if(provider=="serpapi") "link" else "url").trim()
            val title=row.optString(if(provider=="langsearch") "name" else "title").trim().take(200)
            if(ProductSearch.relevantTitle(title) && ProductSearch.matchesPlatform(url,store) && found.none { it.url==url }) found.add(ProductSearch.Result(title,url,""))
            if(found.size>=5) break
        }
        return found
    }
}
