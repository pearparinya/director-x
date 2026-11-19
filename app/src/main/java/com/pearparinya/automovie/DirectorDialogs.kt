package com.pearparinya.automovie

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog

/** Shared filled action buttons for Studio forms, confirmations and updates. */
fun AlertDialog.Builder.showDirector(): AlertDialog {
    val dialog = create()
    dialog.show()
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    listOf(
        Triple(AlertDialog.BUTTON_POSITIVE, "✅", Color.rgb(255,205,99)),
        Triple(AlertDialog.BUTTON_NEGATIVE, "↩️", Color.rgb(145,216,249)),
        Triple(AlertDialog.BUTTON_NEUTRAL, "💡", Color.rgb(208,185,255))
    ).forEach { (which, emoji, color) ->
        val button = dialog.getButton(which) ?: return@forEach
        if (button.text.isNullOrBlank()) return@forEach
        button.text = "$emoji ${button.text}"
        button.isAllCaps = false
        button.setTextColor(Color.rgb(12,15,38))
        button.setTypeface(null,Typeface.BOLD)
        button.backgroundTintList = null
        button.background = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(12).toFloat()
        }
        button.setPadding(dp(6),dp(5),dp(6),dp(5))
        button.minHeight = dp(42)
        button.minimumWidth = 0
        button.textSize = 12f
        button.maxLines = 2
        (button.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.setMargins(dp(2),dp(3),dp(2),dp(3)); button.layoutParams = it
        }
    }
    val metrics = context.resources.displayMetrics
    dialog.window?.setLayout((metrics.widthPixels * 0.94f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    // Keep AppCompat's native button bar intact. Reparenting its buttons into a
    // weighted row can hide the entire bottom action/navigation area on phones.
    // Bound dialog height so long previews scroll instead of pushing actions away.
    val availableHeight = metrics.heightPixels
    val maxHeight = (availableHeight * 0.88f).toInt()
    dialog.window?.decorView?.post {
        val decor = dialog.window?.decorView ?: return@post
        if (decor.height > maxHeight) {
            dialog.window?.setLayout(
                (metrics.widthPixels * 0.94f).toInt(),
                maxHeight
            )
        }
    }
    return dialog
}
