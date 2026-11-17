package com.pearparinya.automovie

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

/** Minimal editor for a scene's validated prop inventory. */
internal object CinematicPropDialog {
    fun show(context: Context, sceneId: String, onSaved: () -> Unit = {}) {
        val store = CinematicPropStore(context)
        val input = EditText(context).apply {
            hint = "JSON array of props"
            minLines = 5
            maxLines = 14
            setText(runCatching { CinematicPropCodec.encode(store.load(sceneId)) }.getOrDefault("[]"))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("รายการวัตถุในฉาก $sceneId")
            .setMessage("แก้ไข JSON ของวัตถุในฉาก ระบบจะตรวจสอบก่อนบันทึก")
            .setView(input)
            .setPositiveButton("บันทึก", null)
            .setNegativeButton("ยกเลิก", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val result = runCatching {
                    val props = CinematicPropCodec.decode(input.text.toString())
                    check(store.save(sceneId, props)) { "บันทึกไม่สำเร็จ" }
                }
                result.onSuccess { dialog.dismiss(); onSaved() }
                    .onFailure { input.error = it.message ?: "ข้อมูลวัตถุไม่ถูกต้อง" }
            }
        }
        dialog.show()
    }
}
