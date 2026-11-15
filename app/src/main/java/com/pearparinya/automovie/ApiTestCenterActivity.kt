package com.pearparinya.automovie

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Offline-only diagnostics. Never sends requests or prints API keys.
 * Access from an explicit intent until Studio navigation is wired up.
 */
class ApiTestCenterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(12, 15, 38))
        }
        fun label(value: String, size: Float = 16f): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.WHITE)
                setPadding(4, 12, 4, 12)
            }
        root.addView(label("API TEST CENTER • ตรวจสอบแบบออฟไลน์", 21f))
        root.addView(label("ไม่มีการเชื่อมต่อ API • ไม่ใช้เครดิต • ไม่แสดงคีย์", 13f))
        val report = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(report)
        fun refresh() {
            report.removeAllViews()
            val active = StoryProvider.activeProviders
            val enabled = active.filter { it.id in StoryProvider.allowedIds(this) }.toSet()
            val configured = active.filter { it.hasKey(this) }.toSet()
            // No automatic fallback approval is assumed.
            val statuses = StoryApiDiagnostics.inspect(active, enabled, configured, emptySet())
            statuses.forEach { item ->
                val description = when (item.state) {
                    StoryApiDiagnostics.State.READY -> "พร้อมใช้เป็น API สำรอง"
                    StoryApiDiagnostics.State.MISSING_KEY -> "ยังไม่มี API Key"
                    StoryApiDiagnostics.State.DISABLED -> "ปิดใช้งาน"
                    StoryApiDiagnostics.State.NOT_APPROVED_FOR_FALLBACK -> "มีคีย์ • ยังไม่ได้อนุญาตให้สลับอัตโนมัติ"
                }
                report.addView(label("${item.provider.label} — $description"))
            }
        }
        root.addView(Button(this).apply {
            text = "ตรวจสอบสถานะใหม่ (ออฟไลน์)"
            setOnClickListener { refresh() }
        })
        val simulation = label("ผลจำลอง: ยังไม่ได้ทดสอบ", 14f)
        root.addView(Button(this).apply {
            text = "จำลอง API สำรองเมื่อพบ HTTP 429"
            setOnClickListener {
                val active = StoryProvider.activeProviders
                val primary = StoryProvider.selected(this@ApiTestCenterActivity)
                val enabled = active.filter { it.id in StoryProvider.allowedIds(this@ApiTestCenterActivity) }.toSet()
                val configured = active.filter { it.hasKey(this@ApiTestCenterActivity) }.toSet()
                val next = StoryApiDiagnostics.simulateFallback(
                    primary, enabled, configured, emptySet(), 429
                )
                simulation.text = if (next == null)
                    "ไม่สลับ API: ยังไม่มีการอนุมัติสำรองโดยชัดแจ้ง (ปลอดภัย)"
                else "ผลจำลอง: ${next.label} (ไม่มีการเรียก API)"
            }
        })
        root.addView(simulation)
        refresh()
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
