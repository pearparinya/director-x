package com.pearparinya.automovie

/** Assign meaning, not completion status, to the color of a menu option. */
internal object MenuChoiceStyle {
    const val STANDARD=0
    const val AI=1
    const val EDIT=2
    fun role(label: String): Int = when {
        label.contains("AI",ignoreCase=true) -> AI
        label.contains("แก้") || label.contains("แก้ไข") -> EDIT
        else -> STANDARD
    }
}
