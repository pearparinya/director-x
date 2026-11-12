package com.pearparinya.automovie

internal object HomeReviewState {
    const val WARDROBE_CONFIRMED = "ยืนยันชุดในบทแล้ว"
    fun wardrobe(saved: String, current: String): String = when {
        saved.isBlank() -> "ยังไม่ได้ยืนยัน"
        saved == current -> WARDROBE_CONFIRMED
        else -> "บทเปลี่ยน • ตรวจชุดอีกครั้ง"
    }
    fun shouldWarn(wardrobeStatus: String, pendingSceneCount: Int): Boolean =
        wardrobeStatus != WARDROBE_CONFIRMED || pendingSceneCount > 0
}
