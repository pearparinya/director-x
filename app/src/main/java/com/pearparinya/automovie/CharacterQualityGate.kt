package com.pearparinya.automovie

internal object CharacterQualityGate {
    fun referenceIssues(required: List<Pair<String,String>>, actual: List<Pair<String,String>>): List<String> = buildList {
        if(actual.size > 9) add("ภาพอ้างอิงเกิน 9 ภาพ")
        if(actual.map { it.first }.distinct().size != actual.size) add("รหัสภาพอ้างอิงซ้ำ")
        required.forEach { ref ->
            if(ref !in actual) add("ขาดภาพอ้างอิงที่ตรงกับต้นฉบับ: "+ref.first)
        }
    }

    data class Check(val label: String, val passed: Int, val total: Int) {
        init { require(total >= 0 && passed in 0..total) }
        val ready get() = total > 0 && passed == total
        fun summary() = "$label: $passed/$total" + if(total == 0) " • ยังไม่มีข้อมูล" else if(ready) " • ครบ" else " • รอตรวจ"
    }
    data class Report(val checks: List<Check>, val blockers: List<String>, val warnings: List<String>) {
        val ready get() = checks.isNotEmpty() && checks.all { it.ready } && blockers.isEmpty() && warnings.isEmpty()
        fun summary() = (listOf(if(ready) "ข้อมูลพร้อมตามรายการตรวจ" else "ยังมีรายการต้องตรวจ") +
            checks.map { it.summary() } + blockers.map { "ต้องแก้: $it" } + warnings.map { "ตรวจเพิ่มเติม: $it" }).joinToString("\n")
    }
}
