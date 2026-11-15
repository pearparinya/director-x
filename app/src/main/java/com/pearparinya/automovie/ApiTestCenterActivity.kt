package com.pearparinya.automovie

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline-only fallback consent and diagnostics.
 * Consent here controls simulations only until runtime integration is separately reviewed.
 * No HTTP calls, quota usage, or credential disclosure.
 */
class ApiTestCenterActivity : Activity() {
    private val navy = Color.rgb(12, 15, 38)
    private val panel = Color.rgb(25, 31, 61)
    private val gold = Color.rgb(246, 191, 88)
    private val pale = Color.rgb(223, 232, 250)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val probing = AtomicBoolean(false)
    private val preferences by lazy { getSharedPreferences("api_fallback_simulation", MODE_PRIVATE) }

    private fun approved(): Set<StoryProvider> {
        val ids = preferences.getStringSet("approved_ids", emptySet())?.toSet().orEmpty()
        return StoryProvider.activeProviders.filter { it.id in ids }.toSet()
    }

    private fun order(): List<StoryProvider> {
        val ids = preferences.getString("order", "")?.split(",").orEmpty()
        return (ids.mapNotNull { id -> StoryProvider.activeProviders.firstOrNull { it.id == id } } +
            StoryProvider.activeProviders).distinct()
    }

    private fun text(value: String, size: Float = 15f, highlight: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(if (highlight) gold else pale)
            if (highlight) setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(9), dp(6), dp(9))
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(15).toFloat()
            setStroke(dp(1), Color.rgb(65, 75, 110))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(28))
            setBackgroundColor(navy)
        }
        root.addView(text("API TEST CENTER", 25f, true))
        root.addView(text("ตรวจสอบและจำลองการสลับ API แบบออฟไลน์", 16f))
        root.addView(text("ไม่มีการเชื่อมต่อ API • ไม่ใช้เครดิต • ไม่แสดงคีย์", 13f))
        root.addView(text("การอนุญาตในหน้านี้ใช้กับการจำลองเท่านั้น ยังไม่เปิดการสลับ API ในงานเขียนบทจริง", 13f, true))
        root.addView(text("ทดสอบออนไลน์จะส่งคำขอสร้าง JSON สั้น ๆ ไปยังผู้ให้บริการและอาจใช้โควตา • ไม่มีการทดสอบอัตโนมัติ", 13f, true))

        val providers = card()
        providers.addView(text("สิทธิ์ API สำรองและลำดับการทดลอง", 18f, true))
        root.addView(providers)
        val report = card()
        report.addView(text("สถานะผู้ให้บริการ", 18f, true))
        root.addView(report)
        val result = card()
        result.addView(text("ผลการจำลอง", 18f, true))
        val simulation = text("ยังไม่ได้ทดสอบ", 15f)
        result.addView(simulation)
        root.addView(result)
        // Keep safe, credit-free simulations beside their result, before live API actions.
        val simulationActions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        result.addView(simulationActions)
        val liveReport = card()
        liveReport.addView(text("ทดสอบการเชื่อมต่อจริง (กดเองเท่านั้น)", 18f, true))
        val liveStatus = text("ยังไม่ได้ทดสอบออนไลน์", 14f)
        liveReport.addView(liveStatus)
        root.addView(liveReport)

        fun refresh() {
            providers.removeAllViews()
            providers.addView(text("สิทธิ์ API สำรองและลำดับการทดลอง", 18f, true))
            val sequence = order()
            val consent = approved()
            sequence.forEachIndexed { index, provider ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(5), 0, dp(9))
                }
                row.addView(text("${index + 1}. ${provider.label}", 16f, true))
                row.addView(Switch(this).apply {
                    text = if (provider in consent) "อนุญาตจำลองเป็น API สำรอง" else "ยังไม่อนุญาต"
                    setTextColor(pale)
                    isChecked = provider in consent
                    setOnCheckedChangeListener { _, checked ->
                        val ids = approved().map { it.id }.toMutableSet()
                        if (checked) ids.add(provider.id) else ids.remove(provider.id)
                        preferences.edit().putStringSet("approved_ids", ids).apply()
                        refresh()
                    }
                })
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(Button(this).apply {
                    text = "ขึ้น"
                    isEnabled = index > 0
                    setOnClickListener {
                        val reordered = order().toMutableList()
                        java.util.Collections.swap(reordered, index, index - 1)
                        preferences.edit().putString("order", reordered.joinToString(",") { it.id }).apply()
                        refresh()
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                actions.addView(Button(this).apply {
                    text = "ลง"
                    isEnabled = index < sequence.lastIndex
                    setOnClickListener {
                        val reordered = order().toMutableList()
                        java.util.Collections.swap(reordered, index, index + 1)
                        preferences.edit().putString("order", reordered.joinToString(",") { it.id }).apply()
                        refresh()
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(actions)
                providers.addView(row)
            }

            report.removeAllViews()
            report.addView(text("สถานะผู้ให้บริการ", 18f, true))
            val enabled = StoryProvider.activeProviders.filter { it.id in StoryProvider.allowedIds(this) }.toSet()
            val configured = StoryProvider.activeProviders.filter { it.hasKey(this) }.toSet()
            StoryApiDiagnostics.inspect(order(), enabled, configured, approved()).forEach { item ->
                val description = when (item.state) {
                    StoryApiDiagnostics.State.READY -> "พร้อมสำหรับการจำลองสำรอง"
                    StoryApiDiagnostics.State.MISSING_KEY -> "ยังไม่มี API Key"
                    StoryApiDiagnostics.State.DISABLED -> "ปิดใช้งานในตั้งค่า API"
                    StoryApiDiagnostics.State.NOT_APPROVED_FOR_FALLBACK -> "ยังไม่ได้อนุญาตจำลองสำรอง"
                }
                report.addView(text("${item.provider.label} — $description • ผลล่าสุด: ${StoryProvider.lastTest(this, item.provider)}", 14f))
            }
        }

        fun runProbe(targets: List<StoryProvider>) {
            if (!probing.compareAndSet(false, true)) return
            liveStatus.text = "กำลังตรวจสอบ ${targets.size} ผู้ให้บริการ • อาจใช้โควตา"
            probeExecutor.execute {
                val messages = mutableListOf<String>()
                try {
                    targets.forEach { provider ->
                        if (!provider.hasKey(this)) {
                            messages.add("${provider.label}: ยังไม่มีคีย์ • ไม่ส่งคำขอ")
                        } else if (provider.id !in StoryProvider.allowedIds(this)) {
                            messages.add("${provider.label}: ปิดใช้งาน • ไม่ส่งคำขอ")
                        } else {
                            val started = System.nanoTime()
                            val outcome = try {
                                val client = StoryClientFactory.create(
                                    provider, provider.key(this), provider.model(this),
                                    cancelled = { false }, automaticRetry = false,
                                    context = if (provider == StoryProvider.GEMINI) this else null
                                )
                                StoryApiProbe.check(client)
                                StoryProvider.recordTest(this, provider, true)
                                val millis = (System.nanoTime() - started) / 1_000_000
                                "ผ่าน • ${millis} ms"
                            } catch (e: Exception) {
                                val safeReason = StoryApiProbe.error(e)
                                StoryProvider.recordTest(this, provider, false, safeReason)
                                "ไม่ผ่าน • $safeReason"
                            }
                            messages.add("${provider.label}: $outcome")
                        }
                        mainHandler.post { if (!isFinishing && !isDestroyed) liveStatus.text = messages.joinToString("\n") }
                    }
                } finally {
                    probing.set(false)
                    mainHandler.post { if (!isFinishing && !isDestroyed) refresh() }
                }
            }
        }
        fun simulate(status: Int) {
            val enabled = StoryProvider.activeProviders.filter { it.id in StoryProvider.allowedIds(this) }.toSet()
            val configured = StoryProvider.activeProviders.filter { it.hasKey(this) }.toSet()
            val next = StoryApiDiagnostics.simulateFallback(
                StoryProvider.selected(this), enabled, configured, approved(),
                status, order = order()
            )
            simulation.text = if (next == null)
                "HTTP $status: ไม่สลับ API (ไม่มีตัวสำรองที่ผ่านเงื่อนไข หรือรหัสข้อผิดพลาดไม่อนุญาต)"
            else "HTTP $status: จำลองเลือก ${next.label} เป็นตัวสำรอง • ไม่ได้เรียก API จริง"
        }
        simulationActions.addView(Button(this).apply {
            text = "จำลอง HTTP 429 (จำกัดคำขอ)"
            setOnClickListener { simulate(429) }
        })
        simulationActions.addView(Button(this).apply {
            text = "จำลอง HTTP 401 (ต้องไม่สลับ)"
            setOnClickListener { simulate(401) }
        })
        simulationActions.addView(Button(this).apply {
            text = "จำลอง HTTP 503 (บริการไม่พร้อม)"
            setOnClickListener { simulate(503) }
        })

        StoryProvider.activeProviders.forEach { provider ->
            root.addView(Button(this).apply {
                text = "ทดสอบออนไลน์: ${provider.label}"
                setOnClickListener { runProbe(listOf(provider)) }
            })
        }
        root.addView(Button(this).apply {
            text = "ทดสอบออนไลน์ทั้งหมด (อาจใช้โควตา 4 รายการ)"
            setOnClickListener {
                android.app.AlertDialog.Builder(this@ApiTestCenterActivity)
                    .setTitle("ยืนยันการใช้โควตา API")
                    .setMessage("จะส่งคำขอทดสอบจริงไปยัง API ที่เปิดใช้งานและมีคีย์ สูงสุด 4 ผู้ให้บริการ โดยไม่ลองซ้ำอัตโนมัติ")
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("เริ่มทดสอบ") { _, _ -> runProbe(StoryProvider.activeProviders) }
                    .show()
            }
        })
        root.addView(Button(this).apply {
            text = "ตรวจสอบสถานะใหม่ (ออฟไลน์)"
            setOnClickListener { refresh() }
        })
        refresh()
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
