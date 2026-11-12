package com.pearparinya.automovie

internal object HomeReviewState {
    const val WARDROBE_CONFIRMED = "ยืนยันชุดในบทแล้ว"
    fun wardrobe(saved: String, current: String): String = when {
        saved.isBlank() -> "ยังไม่ได้ยืนยัน"
        saved == current -> WARDROBE_CONFIRMED
        else -> "บทเปลี่ยน • ตรวจชุดอีกครั้ง"
    }
    fun wardrobeProgress(saved: String, partial: String, current: String, previouslyChanged: Boolean): String = when {
        saved.isNotBlank() && saved==current -> WARDROBE_CONFIRMED
        partial.isNotBlank() && partial==current -> "บันทึกชุดที่เลือกแล้ว • ยังไม่ยืนยันทั้งหมด"
        saved.isNotBlank() || partial.isNotBlank() || previouslyChanged -> "ข้อมูลชุดเปลี่ยน • ตรวจอีกครั้ง"
        else -> "ยังไม่ได้ยืนยัน"
    }
    fun shouldWarn(wardrobeStatus: String, pendingSceneCount: Int): Boolean =
        wardrobeStatus != WARDROBE_CONFIRMED || pendingSceneCount > 0
}
