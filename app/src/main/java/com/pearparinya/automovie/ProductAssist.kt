package com.pearparinya.automovie

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** User-confirmed product facts; store original affiliate links without rewriting them. */
internal object ProductAssist {
    fun confirmedProduct(activity: Activity, premise: String): JSONObject? {
        val prefs = activity.getSharedPreferences("product_assist", Activity.MODE_PRIVATE)
        if (prefs.getString("confirmed_premise", null) != premise) return null
        return runCatching { JSONObject(prefs.getString("confirmed_product", "").orEmpty()) }.getOrNull()
    }
    fun show(activity: Activity, premise: EditText, topic: String, autoSearch: Boolean = false) {
        val prefs = activity.getSharedPreferences("product_assist", Activity.MODE_PRIVATE)
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        fun label(text: String) { panel.addView(TextView(activity).apply { this.text = text; textSize = 15f }) }
        fun field(hintText: String, value: String): EditText = EditText(activity).apply { hint = hintText; setText(value); panel.addView(this) }
        label("ค้นหาในร้าน แล้วแชร์หรือคัดลอกลิงก์กลับมา • ตรวจว่าเป็นสินค้าเดียวกันก่อนรวมลิงก์ • แอปตรวจสิทธิค่าคอมมิชชันไม่ได้")
        label("Shopee: ลิงก์ Affiliate ใหม่มักขึ้นต้น s.shopee.co.th ส่วน shope.ee และ th.shp.ee อาจเป็นลิงก์ทั่วไป โปรดสร้างและตรวจในโปรแกรม Affiliate")
        label("ข้อมูลที่บันทึกไว้อาจเป็นสินค้าเดิม โปรดตรวจและแก้ให้ตรงก่อนยืนยัน")
        val query = field("ค้นหาสินค้า", topic)
        label("เลือกแพลตฟอร์มสำหรับค้นหาด้วย AI")
        val platform = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, listOf("TikTok Shop", "Shopee", "Lazada"))
            panel.addView(this)
        }
        val searchButton = Button(activity).apply {
            text = "✦ ให้ AI ค้นหาสินค้า 5 รายการ"
            setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(0, 106, 155))
            panel.addView(this)
        }
        val engine = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, listOf("Gemini 5 โปรเจกต์ → OpenRouter", "OpenRouter เท่านั้น"))
            panel.addView(this)
        }
        label("ค้นด้วย API ที่ตั้งค่าไว้ • อาจมีค่าใช้จ่าย • ผลลัพธ์ขึ้นอยู่กับข้อมูลสาธารณะ")
        listOf("TikTok Shop" to "https://www.tiktok.com/search?q=", "Shopee" to "https://shopee.co.th/search?keyword=", "Lazada" to "https://www.lazada.co.th/catalog/?q=").forEach { (name, url) ->
            panel.addView(Button(activity).apply { text = "ค้นหาใน $name"; setOnClickListener {
                val encoded = URLEncoder.encode(query.text.toString().trim(), "UTF-8")
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url + encoded))) }
                    .onFailure { AlertDialog.Builder(activity).setMessage("เปิดหน้าค้นหาไม่ได้ กรุณาเปิดแอป $name ด้วยตนเอง").setPositiveButton("ตกลง", null).show() }
            } })
        }
        val links = listOf("TikTok Shop", "Shopee", "Lazada").mapIndexed { i, name -> field("ลิงก์ Affiliate $name (หากมี)", prefs.getString("link$i", "").orEmpty()) }
        val shared = prefs.getString("shared_link", "").orEmpty()
        if (shared.isNotBlank()) {
            label("ลิงก์ที่แชร์เข้ามา: $shared")
            links.forEachIndexed { i, edit -> panel.addView(Button(activity).apply { text = "ใส่ลิงก์แชร์ในช่อง ${listOf("TikTok", "Shopee", "Lazada")[i]}"; setOnClickListener { edit.setText(shared) } }) }
        }
        val source = field("ลิงก์สินค้าต้นทาง (ไม่ใช่ลิงก์ Affiliate)", prefs.getString("source_url", "").orEmpty())
        panel.addView(Button(activity).apply { text = "เปิดสินค้าเพื่อรับลิงก์ Affiliate"; setOnClickListener {
            val url = ProductLinkPolicy.firstUrl(source.text.toString())
            if (!ProductLinkPolicy.allowed(url)) AlertDialog.Builder(activity).setMessage("เลือกสินค้าหรือวางลิงก์สินค้าก่อน").setPositiveButton("ตกลง", null).show()
            else runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { AlertDialog.Builder(activity).setMessage("เปิดหน้าสินค้าไม่ได้").setPositiveButton("ตกลง", null).show() }
        } })
        val name = field("ชื่อสินค้า / ยี่ห้อ / รุ่น", prefs.getString("name", "").orEmpty())
        val facts = field("คุณสมบัติ วิธีใช้ และข้อสังเกตที่ตรวจแล้ว", prefs.getString("facts", "").orEmpty())
        val rating = field("คะแนนและจำนวนรีวิว (ถ้ามี พร้อมแหล่งที่มา)", prefs.getString("rating", "").orEmpty())
        val experience = field("ประสบการณ์ใช้จริงของคุณ (ถ้ามี)", prefs.getString("experience", "").orEmpty())
        searchButton.setOnClickListener {
            val searchText = query.text.toString().trim(); val selectedPlatform = platform.selectedItemPosition; val selectedEngine = engine.selectedItemPosition
            if (searchText.isBlank()) {
                AlertDialog.Builder(activity).setMessage("กรุณาระบุคำค้นสินค้า").setPositiveButton("ตกลง", null).show()
                return@setOnClickListener
            }
            val control=ProductSearchControl()
            val attempts=mutableListOf<ProductSearchRotation.Attempt>()
            val allowed=StoryProvider.allowedIds(activity)
            if(selectedEngine==0 && "gemini" in allowed) {
                (0..4).forEach { slot ->
                    val settings=GeminiSettings(activity,StoryProvider.GEMINI,slot)
                    if(settings.hasSaved()) attempts.add(ProductSearchRotation.Attempt("Gemini ชุดที่ ${slot+1}/5") {
                        val key=runCatching { settings.key() }.getOrElse { throw ProductSearchError("อ่านคีย์บนเครื่องไม่ได้") }
                        if(key.isBlank()) throw ProductSearchError("ยังไม่ได้ตั้งค่าคีย์")
                        val model=activity.getSharedPreferences("gemini_key_pool",Activity.MODE_PRIVATE).getString("model",null)?.takeIf { it.isNotBlank() } ?: settings.model()
                        GeminiProductSearch.search(activity.applicationContext,searchText,selectedPlatform,key,control,model)
                    })
                }
            }
            val orSettings=ExtraApiSettings(activity, ExtraApiProvider.OPENROUTER)
            if("openrouter" in allowed && orSettings.hasKey()) attempts.add(ProductSearchRotation.Attempt("OpenRouter") {
                ProductSearch.searchOpenRouter(activity.applicationContext,searchText,selectedPlatform,control)
            })
            if(attempts.isEmpty()) {
                AlertDialog.Builder(activity).setTitle("ยังไม่มี API ที่พร้อมค้นหา").setMessage("เพิ่มคีย์ Gemini หรือ OpenRouter และตรวจการตั้งค่า API ก่อนลองใหม่").setPositiveButton("ตกลง",null).show()
                return@setOnClickListener
            }
            searchButton.isEnabled = false
            val handler=android.os.Handler(android.os.Looper.getMainLooper())
            val started=android.os.SystemClock.elapsedRealtime()
            val progressText=TextView(activity).apply { textSize=18f; setPadding(32,24,32,24); setTextColor(Color.WHITE) }
            val progressPanel=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; addView(progressText); addView(android.widget.ProgressBar(activity).apply { isIndeterminate=true }) }
            val progress = AlertDialog.Builder(activity).setTitle("✦ กำลังค้นหาสินค้า")
                .setView(progressPanel).setNegativeButton("ยกเลิก",null).create()
            var step="กำลังเตรียมค้นหา"
            val tick=object: Runnable { override fun run() {
                if(!progress.isShowing) return
                val seconds=(android.os.SystemClock.elapsedRealtime()-started)/1000
                progressText.text="$step\nเวลารวม %02d:%02d\nOpenRouter อาจมีค่าใช้จ่ายตามบัญชี".format(seconds/60,seconds%60)
                handler.postDelayed(this,1000)
            } }
            progress.setOnDismissListener { control.cancel(); handler.removeCallbacks(tick); searchButton.isEnabled=true }
            progress.setOnShowListener { progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { progress.dismiss() } }
            progress.show(); handler.post(tick)
            Thread {
                val outcome=runCatching { ProductSearchRotation.run(attempts,control) { index,total,label,state ->
                    activity.runOnUiThread { if(progress.isShowing && !control.cancelled()) step="$label • $index/$total\n$state" }
                } }
                activity.runOnUiThread {
                    if(control.cancelled() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    val found=outcome.getOrNull(); progress.dismiss()
                    if(found==null) {
                        AlertDialog.Builder(activity).setTitle("ค้นหาสินค้าไม่สำเร็จ").setMessage("การค้นหาขัดข้อง กรุณาลองใหม่หรือค้นจากร้านโดยตรง").setPositiveButton("ตกลง",null).show()
                    } else {
                        val products=found.results
                        if(products.isEmpty()) {
                            AlertDialog.Builder(activity).setTitle("ยังไม่พบสินค้าที่ตรวจแหล่งได้")
                                .setMessage(found.failures.joinToString("\n").ifBlank { "ไม่พบข้อมูล กรุณาลองค้นใหม่" }+"\n\nลองคำค้นใหม่ ค้นจากร้าน หรือวางลิงก์เองได้")
                                .setPositiveButton("ตกลง",null).show()
                        } else {
                            val cards = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12) }
                            cards.addView(TextView(activity).apply { text = "พบ ${products.size} รายการ • แตะเลือก 1 รายการ แล้วตรวจข้อมูลกับร้านอีกครั้ง"; setPadding(8, 8, 8, 16) })
                            val resultsDialog = AlertDialog.Builder(activity).setTitle("✦ สินค้าที่ค้นพบ").setView(ScrollView(activity).apply { addView(cards) }).setNegativeButton("กลับไปค้นใหม่", null).create()
                            products.forEachIndexed { index, product ->
                                cards.addView(Button(activity).apply {
                                    text = "${index + 1}  ${product.name}\n${product.detail.ifBlank { "ดูรายละเอียดจากร้าน" }}\nเปิดตรวจจากลิงก์ต้นทางได้หลังเลือก"
                                    isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
                                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(4, 67, 108))
                                    minHeight = 105
                                    setOnClickListener {
                                        name.setText(product.name); facts.setText(product.detail)
                                        rating.setText(""); experience.setText(""); links.forEach { it.setText("") }
                                        source.setText(product.url)
                                        prefs.edit().putString("source_url", product.url).putString("name", product.name).putString("facts", product.detail)
                                            .putString("link0", "").putString("link1", "").putString("link2", "")
                                            .putString("rating", "").putString("experience", "").remove("shared_link")
                                            .remove("confirmed_product").remove("confirmed_premise").apply()
                                        resultsDialog.dismiss()
                                        AlertDialog.Builder(activity).setTitle("เลือกสินค้าแล้ว").setMessage("ตรวจชื่อและคุณสมบัติกับร้านก่อนยืนยัน • ลิงก์ที่พบเป็นลิงก์สินค้า กรุณาเปลี่ยนเป็นลิงก์ Affiliate ของคุณหากต้องการค่าคอมมิชชัน") .setPositiveButton("ตกลง", null).show()
                                    }
                                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
                            }
                            resultsDialog.show()
                        }
                    }
                }
            }.start()
        }
        label("รายการสินค้าที่เลือกไว้สูงสุด 5 รายการ • ค้นหาและเลือกรายการจากร้านก่อน แล้วบันทึกไว้เปรียบเทียบได้ที่นี่")
        val candidates = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; panel.addView(this) }
        fun currentProduct(): JSONObject = JSONObject().put("source_url", source.text.toString().trim()).put("name", name.text.toString().trim())
            .put("facts", facts.text.toString().trim()).put("rating", rating.text.toString().trim())
            .put("experience", experience.text.toString().trim()).apply {
                links.forEachIndexed { i, edit -> put("link$i", edit.text.toString().trim()) }
            }
        fun loadProduct(product: JSONObject) {
            source.setText(product.optString("source_url")); name.setText(product.optString("name")); facts.setText(product.optString("facts"))
            rating.setText(product.optString("rating")); experience.setText(product.optString("experience"))
            links.forEachIndexed { i, edit -> edit.setText(product.optString("link$i")) }
        }
        fun refreshCandidates() {
            candidates.removeAllViews()
            repeat(5) { slot ->
                val key = "candidate_$slot"
                val saved = runCatching { JSONObject(prefs.getString(key, "{}").orEmpty()) }.getOrDefault(JSONObject())
                val title = saved.optString("name").ifBlank { "ว่าง" }
                candidates.addView(Button(activity).apply {
                    text = "${slot + 1}. $title"
                    setOnClickListener {
                        val actions = if (saved.optString("name").isBlank()) arrayOf("บันทึกข้อมูลสินค้าปัจจุบัน") else arrayOf("เลือกสินค้านี้", "บันทึกข้อมูลปัจจุบันทับรายการนี้", "ลบรายการนี้")
                        AlertDialog.Builder(activity).setTitle("รายการสินค้า ${slot + 1}: $title").setItems(actions) { _, choice ->
                            when (actions[choice]) {
                                "เลือกสินค้านี้" -> loadProduct(saved)
                                "ลบรายการนี้" -> { prefs.edit().remove(key).apply(); refreshCandidates() }
                                else -> if (name.text.isBlank()) {
                                    AlertDialog.Builder(activity).setMessage("กรุณาระบุชื่อสินค้าก่อนบันทึก").setPositiveButton("ตกลง", null).show()
                                } else { prefs.edit().putString(key, currentProduct().toString()).apply(); refreshCandidates() }
                            }
                        }.setNegativeButton("ยกเลิก", null).show()
                    }
                })
            }
        }
        refreshCandidates()
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
                val edits = prefs.edit().putString("source_url", source.text.toString().trim())
                links.forEachIndexed { i, edit -> edits.putString("link$i", edit.text.toString().trim()) }
                edits.putString("name", name.text.toString().trim()).putString("facts", facts.text.toString().trim())
                    .putString("rating", rating.text.toString().trim()).putString("experience", experience.text.toString().trim()).remove("shared_link").apply()
                val verified = listOf("สินค้า: ${name.text}", "ข้อมูลที่ผู้ใช้ตรวจ: ${facts.text}", if(source.text.isNotBlank()) "แหล่งข้อมูลสินค้า: ${source.text}" else "",
                    if (rating.text.isNotBlank()) "คะแนนที่ผู้ใช้ระบุ: ${rating.text}" else "", if (experience.text.isNotBlank()) "ประสบการณ์ที่ผู้ใช้ระบุ: ${experience.text}" else "ยังไม่ได้ระบุประสบการณ์ใช้จริง ห้ามอ้างว่าได้ทดลองใช้",
                    "ไม่ระบุราคา ไม่สร้างสรรพคุณ คะแนน หรือรีวิวขึ้นเอง")
                val confirmed = verified.filter { it.isNotBlank() }.joinToString("\n")
                prefs.edit().putString("confirmed_premise", confirmed).putString("confirmed_product", currentProduct().toString()).apply()
                premise.setText(confirmed)
                dialog.dismiss()
            } }
        dialog.show()
        if (autoSearch && topic.isNotBlank() && runCatching { GeminiSettings.configured(activity).isNotEmpty() }.getOrDefault(false)) searchButton.post { if (dialog.isShowing) searchButton.performClick() }
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
