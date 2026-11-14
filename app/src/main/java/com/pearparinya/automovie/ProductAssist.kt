package com.pearparinya.automovie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** User-confirmed product facts; store original affiliate links without rewriting them. */
internal object ProductAssist {
    fun show(activity: Activity, premise: EditText, topic: String) {
        val prefs = activity.getSharedPreferences("product_assist", Activity.MODE_PRIVATE)
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        fun label(text: String) { panel.addView(TextView(activity).apply { this.text = text; textSize = 15f }) }
        fun field(hintText: String, value: String): EditText = EditText(activity).apply { hint = hintText; setText(value); panel.addView(this) }
        label("ค้นหาในร้าน แล้วแชร์หรือคัดลอกลิงก์ Affiliate กลับมา • ตรวจว่าเป็นสินค้าเดียวกันก่อนรวมลิงก์")
        label("ข้อมูลที่บันทึกไว้อาจเป็นสินค้าเดิม โปรดตรวจและแก้ให้ตรงก่อนยืนยัน")
        val query = field("ค้นหาสินค้า", topic)
        listOf("TikTok Shop" to "https://www.tiktok.com/search?q=", "Shopee" to "https://shopee.co.th/search?keyword=", "Lazada" to "https://www.lazada.co.th/catalog/?q=").forEach { (name, url) ->
            panel.addView(Button(activity).apply { text = "ค้นหาใน $name"; setOnClickListener {
                val encoded = URLEncoder.encode(query.text.toString().trim(), "UTF-8")
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url + encoded))) }
                    .onFailure { AlertDialog.Builder(activity).setMessage("เปิดหน้าค้นหาไม่ได้ กรุณาเปิดแอป $name ด้วยตนเอง").setPositiveButton("ตกลง", null).show() }
            } })
        }
        val links = listOf("TikTok Shop", "Shopee", "Lazada").mapIndexed { i, name -> field("ลิงก์ Affiliate $name", prefs.getString("link$i", "").orEmpty()) }
        val shared = prefs.getString("shared_link", "").orEmpty()
        if (shared.isNotBlank()) {
            label("ลิงก์ที่แชร์เข้ามา: $shared")
            links.forEachIndexed { i, edit -> panel.addView(Button(activity).apply { text = "ใส่ลิงก์แชร์ในช่อง ${listOf("TikTok", "Shopee", "Lazada")[i]}"; setOnClickListener { edit.setText(shared) } }) }
        }
        val name = field("ชื่อสินค้า / ยี่ห้อ / รุ่น", prefs.getString("name", "").orEmpty())
        val facts = field("คุณสมบัติ วิธีใช้ และข้อสังเกตที่ตรวจแล้ว", prefs.getString("facts", "").orEmpty())
        val rating = field("คะแนนและจำนวนรีวิว (ถ้ามี พร้อมแหล่งที่มา)", prefs.getString("rating", "").orEmpty())
        val experience = field("ประสบการณ์ใช้จริงของคุณ (ถ้ามี)", prefs.getString("experience", "").orEmpty())
        label("ไม่ต้องใส่ราคา • หากไม่เคยใช้ ให้เว้นประสบการณ์ว่าง • ตรวจข้อมูลก่อนส่งสร้างบท")
        val status = TextView(activity); panel.addView(status)
        panel.addView(Button(activity).apply { text = "ลองอ่านชื่อและคำอธิบายจากลิงก์"; setOnClickListener {
            val url = links.map { ProductLinkPolicy.firstUrl(it.text.toString()) }.firstOrNull { it.isNotBlank() }.orEmpty()
            if (!ProductLinkPolicy.allowed(url)) { status.text = "ใส่ลิงก์ HTTPS ของร้านที่รองรับก่อน หรือกรอกข้อมูลเอง"; return@setOnClickListener }
            status.text = "กำลังตรวจข้อมูลสาธารณะ…"
            Thread {
                val result = runCatching { ProductPage.fetch(url) }
                activity.runOnUiThread {
                    val data = result.getOrNull()
                    if (data == null || data.first.isBlank()) status.text = "ดึงข้อมูลไม่ได้หรือหน้าไม่เปิดเผยข้อมูล กรุณากรอกเอง"
                    else { if (name.text.isBlank()) name.setText(data.first); if (facts.text.isBlank()) facts.setText(data.second); status.text = "ข้อมูลจากหน้าเว็บเป็นฉบับร่าง โปรดตรวจให้ตรงสินค้า • คะแนนและรูปยังไม่ได้ตรวจ" }
                }
            }.start()
        } })
        val dialog = AlertDialog.Builder(activity).setTitle("นายเปียพาช้อป • ข้อมูลสินค้า").setView(ScrollView(activity).apply { addView(panel) })
            .setPositiveButton("ยืนยันข้อมูลใช้สร้างบท", null).setNegativeButton("ยกเลิก", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (name.text.isBlank() || facts.text.isBlank()) {
                    status.text = "กรุณาระบุชื่อสินค้าและข้อมูลสินค้าที่ตรวจสอบแล้ว"
                    return@setOnClickListener
                }
                if (links.any { it.text.isNotBlank() && !ProductLinkPolicy.allowed(ProductLinkPolicy.firstUrl(it.text.toString())) }) {
                    status.text = "ตรวจลิงก์ให้เป็น HTTPS ของร้านที่รองรับ"
                    return@setOnClickListener
                }
                val edits = prefs.edit()
                links.forEachIndexed { i, edit -> edits.putString("link$i", edit.text.toString().trim()) }
                edits.putString("name", name.text.toString().trim()).putString("facts", facts.text.toString().trim())
                    .putString("rating", rating.text.toString().trim()).putString("experience", experience.text.toString().trim()).remove("shared_link").apply()
                val verified = listOf("สินค้า: ${name.text}", "ข้อมูลที่ผู้ใช้ตรวจ: ${facts.text}",
                    if (rating.text.isNotBlank()) "คะแนนที่ผู้ใช้ระบุ: ${rating.text}" else "", if (experience.text.isNotBlank()) "ประสบการณ์ที่ผู้ใช้ระบุ: ${experience.text}" else "ยังไม่ได้ระบุประสบการณ์ใช้จริง ห้ามอ้างว่าได้ทดลองใช้",
                    "ไม่ระบุราคา ไม่สร้างสรรพคุณ คะแนน หรือรีวิวขึ้นเอง")
                premise.setText(verified.filter { it.isNotBlank() }.joinToString("\n"))
                dialog.dismiss()
            } }
        dialog.show()
    }
}

internal object ProductPage {
    fun fetch(original: String): Pair<String, String> {
        var next = original
        repeat(5) {
            require(ProductLinkPolicy.allowed(next)) { "ลิงก์ไม่รองรับ" }
            val connection = URL(next).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false; connection.connectTimeout = 10000; connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) DirectorX/1.0")
            try {
                val code = connection.responseCode
                if (code in 300..399) { next = URL(URL(next), connection.getHeaderField("Location") ?: error("no redirect")).toString(); return@repeat }
                require(code in 200..299 && connection.contentType.orEmpty().contains("html", true))
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val result = StringBuilder()
                    val buffer = CharArray(8192)
                    while (result.length < 750000) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 750000 - result.length))
                        if (count < 0) break
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                }
                fun meta(key: String): String {
                    val tag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).firstOrNull {
                        Regex("(?:property|name)\\s*=\\s*['\"]" + Regex.escape(key) + "['\"]", RegexOption.IGNORE_CASE).containsMatchIn(it.value)
                    }?.value.orEmpty()
                    val raw = Regex("content\\s*=\\s*['\"]([^'\"]*)['\"]", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1).orEmpty()
                    return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim().take(600)
                }
                return meta("og:title") to meta("og:description")
            } finally { connection.disconnect() }
        }
        error("redirect limit")
    }
}
