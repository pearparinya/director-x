package com.pearparinya.automovie

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    fun used(provider: String): Int = if(prefs.getString(provider+"_month","")==month()) prefs.getInt(provider+"_used",0) else 0
    fun limit(provider: String)=if(provider=="tavily") 900 else 1500
    @Synchronized fun reserve(provider: String) {
        val count=used(provider); require(count<limit(provider)) { "ถึงขีดจำกัดค้นฟรีบนเครื่องในเดือนนี้" }
        check(prefs.edit().putString(provider+"_month",month()).putInt(provider+"_used",count+1).commit()) { "บันทึกโควตาไม่สำเร็จ" }
    }
}

internal object FreeProductSearch {
    private fun searchAdditional(provider: String, key: String, query: String, store: Int, control: ProductSearchControl): List<ProductSearch.Result> {
        val domain = listOf("tiktokshop.com", "shopee.co.th", "lazada.co.th")[store]
        val searchQuery = query.take(120) + " site:" + domain
        val endpoint = if(provider=="serpapi") "https://serpapi.com/search.json" else "https://api.firecrawl.dev/v2/search"
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000; conn.readTimeout = 45000
        val payload = if(provider=="serpapi") JSONObject().put("engine","google").put("q",searchQuery).put("api_key",key).put("num",10)
            else JSONObject().put("query",searchQuery).put("limit",10)
        conn.requestMethod="POST"; conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json")
        if(provider=="firecrawl") conn.setRequestProperty("Authorization","Bearer $key")
        control.attach(conn)
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code=conn.responseCode
            if(code !in 200..299) throw ProductSearchError(when(code) {
                401,403 -> "คีย์หรือสิทธิ์ไม่พร้อม (HTTP $code)"
                402,429 -> "โควตาหรืออัตราเรียกไม่พร้อม (HTTP $code)"
                else -> "บริการค้นหาขัดข้อง (HTTP $code)"
            })
            val body=JSONObject(conn.inputStream.bufferedReader().use { it.readText().take(500000) })
            val rows=if(provider=="serpapi") body.optJSONArray("organic_results") ?: JSONArray()
                else body.optJSONObject("data")?.optJSONArray("web") ?: body.optJSONArray("data") ?: JSONArray()
            val found=mutableListOf<ProductSearch.Result>()
            for(i in 0 until rows.length()) {
                control.check()
                val row=rows.optJSONObject(i) ?: continue
                val url=if(provider=="serpapi") row.optString("link") else row.optString("url")
                val title=row.optString("title").take(200)
                if(title.isNotBlank() && ProductSearch.matchesPlatform(url,store) && found.none { it.url==url })
                    found.add(ProductSearch.Result(title,url,""))
                if(found.size>=5) break
            }
            return found
        } finally { control.detach(conn); conn.disconnect() }
    }
    fun search(context: Context, provider: String, query: String, store: Int, control: ProductSearchControl): List<ProductSearch.Result> {
        val settings=FreeSearchSettings(context); val key=settings.key(provider)
        if(key.isBlank()) throw ProductSearchError("ยังไม่ได้ตั้ง API Key")
        val domains=listOf(listOf("tiktokshop.com", "shop-th.tiktok.com"),listOf("shopee.co.th"),listOf("lazada.co.th"))
        require(store in domains.indices)
        val request=JSONObject().put("query",query.take(120)+" "+listOf("TikTok Shop Thailand","Shopee Thailand","Lazada Thailand")[store])
        if(provider=="tavily") request.put("search_depth","basic").put("max_results",10).put("auto_parameters",false).put("include_domains",JSONArray(domains[store]))
        else request.put("type","lite").put("numResults",10).put("includeDomains",JSONArray(domains[store]))
        if(provider=="serpapi" || provider=="firecrawl") return searchAdditional(provider, key, query, store, control)
        // Reserve before sending, including failures, so retries cannot bypass the local ceiling.
        settings.reserve(provider)
        val conn=URL(if(provider=="tavily") "https://api.tavily.com/search" else "https://api.exa.ai/search").openConnection() as HttpURLConnection
        conn.requestMethod="POST"; conn.connectTimeout=20000; conn.readTimeout=45000; conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/json")
        if(provider=="tavily") conn.setRequestProperty("Authorization","Bearer $key") else conn.setRequestProperty("x-api-key",key)
        control.attach(conn)
        try {
            conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code=conn.responseCode
            if(code !in 200..299) throw ProductSearchError(when(code) { 401,403 -> "คีย์หรือสิทธิ์ไม่พร้อม (HTTP $code)"; 402,429 -> "โควตาหรืออัตราเรียกไม่พร้อม (HTTP $code)"; else -> "บริการค้นหาขัดข้อง (HTTP $code)" })
            val body=conn.inputStream.bufferedReader().use { it.readText().take(500000) }
            val rows=JSONObject(body).optJSONArray("results") ?: JSONArray(); val found=mutableListOf<ProductSearch.Result>()
            for(i in 0 until rows.length()) {
                control.check(); val row=rows.optJSONObject(i) ?: continue
                val url=row.optString("url").trim(); val title=row.optString("title").trim().take(200)
                if(title.isNotBlank() && ProductSearch.matchesPlatform(url,store) && found.none { it.url==url }) found.add(ProductSearch.Result(title,url,""))
                if(found.size>=5) break
            }
            return found
        } finally { control.detach(conn); conn.disconnect() }
    }
}
