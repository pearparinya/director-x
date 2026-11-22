package com.pearparinya.automovie

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** Compact reusable scene card for image and Flow pickers. */
internal object SceneLockCard {
    fun create(
        context: Context, workspace: File, sceneId: String,
        medium: SceneLocks.Medium, artifact: File?, onChanged: () -> Unit = {}
    ): LinearLayout {
        val navy = Color.rgb(12, 15, 38)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 16, 22, 16)
            setBackgroundColor(navy)
        }
        val heading = TextView(context).apply {
            text = "🎬 $sceneId  •  " + if (medium == SceneLocks.Medium.IMAGE) "ภาพ" else "Flow"
            setTextColor(Color.rgb(255, 190, 74))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        }
        val status = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(0, 6, 0, 6)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val primary = Button(context).apply {
            isAllCaps = false
            textSize = 12f
            setTextColor(navy)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(83, 224, 210))
        }
        val more = Button(context).apply {
            text = "⋯"
            contentDescription = "ตัวเลือกซีน $sceneId"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(37, 32, 78))
        }
        fun refresh() {
            val state = SceneLocks.load(workspace)
            val locked = SceneLocks.isLocked(state, sceneId, medium, artifact)
            status.text = if (locked) "🔒 อนุมัติแล้ว" else "⏳ รอตรวจสอบ"
            primary.text = if (locked) "🔒 ล็อกแล้ว" else "💾 บันทึกและล็อก"
            primary.isEnabled = !locked && artifact?.isFile == true && artifact.length() > 0L
            primary.alpha = if (primary.isEnabled) 1f else 0.55f
        }
        primary.setOnClickListener {
            SceneLockDialog.show(context, workspace, sceneId, medium, artifact) {
                refresh()
                onChanged()
            }
        }
        more.setOnClickListener {
            SceneLockDialog.show(context, workspace, sceneId, medium, artifact) {
                refresh()
                onChanged()
            }
        }
        row.addView(primary, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(more, LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(heading)
        card.addView(status)
        card.addView(row)
        refresh()
        return card
    }
}
