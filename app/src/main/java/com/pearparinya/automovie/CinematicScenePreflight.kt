package com.pearparinya.automovie

/**
 * Optional structured preflight for scenes whose props have been explicitly inventoried.
 * Legacy scenes without a prop inventory retain their existing behavior.
 */
internal data class CinematicProp(
    val id: String,
    val presentInFirstFrame: Boolean,
    val requiredForAction: Boolean,
    val screenSide: String = "",
    val expectedScreenSide: String = "",
    val expectedCount: Int = 1,
    val firstFrameCount: Int? = null
)

internal object CinematicScenePreflight {
    fun issues(props: List<CinematicProp>): List<String> = buildList {
        val ids = props.map { it.id.trim().lowercase() }
        if (ids.any { it.isEmpty() }) add("กรุณาระบุรหัสวัตถุทุกชิ้น")
        if (ids.distinct().size != ids.size) add("พบรหัสวัตถุซ้ำในฉาก")
        props.forEach { prop ->
            if (prop.expectedCount < 0 || (prop.firstFrameCount != null && prop.firstFrameCount < 0))
                add("จำนวนวัตถุ ${prop.id} ต้องไม่ติดลบ")
            if (prop.requiredForAction && prop.expectedCount == 0)
                add("วัตถุ ${prop.id} จำเป็นต่อการกระทำ แต่กำหนดจำนวนที่ต้องใช้เป็นศูนย์")
            if (prop.presentInFirstFrame && prop.firstFrameCount == 0)
                add("วัตถุ ${prop.id} ระบุว่ามีในภาพเริ่มต้น แต่จำนวนเป็นศูนย์")
            if (!prop.presentInFirstFrame && prop.firstFrameCount != null && prop.firstFrameCount > 0)
                add("วัตถุ ${prop.id} ระบุว่าไม่มีในภาพเริ่มต้น แต่จำนวนมากกว่าศูนย์")
            if (prop.requiredForAction && prop.presentInFirstFrame && prop.firstFrameCount != null && prop.firstFrameCount < prop.expectedCount)
                add("วัตถุ ${prop.id} ในภาพเริ่มต้นมีจำนวนไม่เพียงพอสำหรับการกระทำ")
            if (prop.requiredForAction && !prop.presentInFirstFrame)
                add("วัตถุ ${prop.id} จำเป็นต่อการกระทำ แต่ไม่มีในภาพเริ่มต้น")
            val side = prop.screenSide.trim().lowercase()
            val expected = prop.expectedScreenSide.trim().lowercase()
            if (side.isNotEmpty() && side !in setOf("left", "right", "center"))
                add("ตำแหน่งวัตถุ ${prop.id} ต้องเป็น left, right หรือ center")
            if (expected.isNotEmpty() && expected !in setOf("left", "right", "center"))
                add("ตำแหน่งเป้าหมายของวัตถุ ${prop.id} ไม่ถูกต้อง")
            if (side.isNotEmpty() && expected.isNotEmpty() &&
                side in setOf("left", "right", "center") &&
                expected in setOf("left", "right", "center") && side != expected)
                add("ตำแหน่งซ้าย–ขวาของวัตถุ ${prop.id} ไม่ตรงกับภาพเริ่มต้น")
        }
    }
}
