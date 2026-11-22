package com.pearparinya.automovie

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File

/**
 * Shared compact approval control for scene image and Flow menus.
 * Call with the actual workspace and artifact for the selected scene.
 */
internal object SceneLockDialog {
    fun show(context: Context, workspace: File, sceneId: String, medium: SceneLocks.Medium,
             artifact: File?, onChanged: () -> Unit = {}) {
        val state = SceneLocks.load(workspace)
        val locked = SceneLocks.isLocked(state, sceneId, medium, artifact)
        val label = if (medium == SceneLocks.Medium.IMAGE) "ภาพ" else "Flow"
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 20)
            setBackgroundColor(Color.rgb(12, 15, 38))
        }
        body.addView(TextView(context).apply {
            text = "🎬 $sceneId  •  $label"
            textSize = 17f
            setTextColor(Color.rgb(255, 190, 74))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        })
        body.addView(TextView(context).apply {
            text = if (locked) "🔒 อนุมัติแล้ว • ป้องกันการสร้างทับ" else "⏳ รอตรวจสอบ • ยังไม่ล็อก"
            textSize = 14f
            setPadding(0, 12, 0, 0)
            setTextColor(Color.WHITE)
        })
        val action = when {
            locked -> "🔓 ปลดล็อกเพื่อแก้ไข"
            artifact?.isFile == true && artifact.length() > 0 -> "💾 บันทึกและล็อก$label"
            else -> null
        }
        val dialog = AlertDialog.Builder(context)
            .setView(body)
            .setNegativeButton("ปิด", null)
        if (action != null) dialog.setPositiveButton(action) { _, _ ->
            if (locked) {
                AlertDialog.Builder(context)
                    .setMessage("ปลดล็อก $sceneId ($label) เพื่อแก้ไข? ผลงานเดิมจะยังอยู่")
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("ปลดล็อก") { _, _ ->
                        SceneLocks.unlock(state, sceneId, medium)
                        SceneLocks.save(workspace, state)
                        onChanged()
                    }.show()
            } else {
                try {
                    SceneLocks.approve(state, sceneId, medium, requireNotNull(artifact))
                    SceneLocks.save(workspace, state)
                    onChanged()
                } catch (error: Exception) {
                    AlertDialog.Builder(context).setMessage(error.message ?: "บันทึกไม่สำเร็จ")
                        .setPositiveButton("ตกลง", null).show()
                }
            }
        }
        dialog.show()
    }
}
