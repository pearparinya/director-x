package com.pearparinya.automovie

internal object PromptMenuStatus {
    fun image(done: Int, total: Int, running: Boolean, failed: Int) =
        format("คำสั่งสร้างภาพ", "รายการ", done, total, running, failed)

    fun flow(done: Int, total: Int, running: Boolean, failed: Int) =
        format("คำสั่ง Flow", "ฉาก", done, total, running, failed)

    private fun format(name: String, unit: String, done: Int, total: Int, running: Boolean, failed: Int): String {
        val ready = if (unit == "ฉาก") "$name พร้อมใช้" else "${name}พร้อมใช้"
        val progress = "$done/$total $unit"
        val retry = failed.coerceIn(0, total - done)
        val pending = total - done - retry
        return when {
            total == 0 -> "ยังไม่มี$name"
            done == total -> "✅ $ready $progress"
            running -> "กำลังปรับ$name · เสร็จแล้ว $progress"
            done == 0 && retry == 0 -> "ยังไม่ได้ปรับ$name · $progress"
            else -> listOfNotNull(
                "$ready $progress",
                "รอดำเนินการ $pending $unit".takeIf { pending > 0 },
                "ต้องลองใหม่ $retry $unit".takeIf { retry > 0 }
            ).joinToString(" · ")
        }
    }
}
