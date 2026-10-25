package com.pearparinya.automovie

/** A shared brief for image requests. References identify people; scene facts define clothes and action. */
internal object ImagePromptPolicy {
    const val referenceAppeal = "คงเสน่ห์และความเซ็กซี่ตามภาพอ้างอิงของตัวละครผู้ใหญ่"
    fun productionMaster(name: String, source: String, profile: String): String = """
สร้างภาพอ้างอิงตัวละครผู้ใหญ่ $name จาก $source ที่แนบ หนึ่งคนต่อหนึ่งภาพ
${identity(source)}
${if(profile.isNotBlank()) "ข้อมูลอ้างอิงรายคน: $profile" else ""}
เลือกเสื้อผ้าที่สวยงามเหมาะสม ทรงพอดีตัว มีแนวเอวและการทิ้งตัวของผ้าตามธรรมชาติ เลือกชนิด สี และรายละเอียดชุดให้เหมาะกับตัวละคร ไม่บังคับใช้เสื้อหลวมคลุมรูปร่าง
ยืนผ่อนคลาย มือไม่บังลำตัว พื้นหลังเทาอ่อน แสงนุ่มสม่ำเสมอ มุมกล้องระดับสายตา เห็นศีรษะถึงเข่า ภาพสมจริงแนวนอน 16:9 ไม่มีข้อความหรือลายน้ำ
คงเอกลักษณ์เดิม ไม่ออกแบบใบหน้าใหม่ ไม่ขยายหรือลดสัดส่วนเอง ไม่คัดลอกชุดหรือฉากหลังเดิม
แสดงภาพจริงเพื่อให้ตรวจใบหน้า ผม รูปร่าง มือและเสื้อผ้าก่อนเลือกเป็น MASTER ใหม่ ไม่อ้างว่าผ่านการทดสอบหรือรับประกันความเหมือน หากสร้างไม่ได้ให้แจ้งตามจริงและหยุด ไม่ปรับคำเพื่อหลบตัวกรอง
    """.trimIndent()

    fun identity(source: String) =
        "ใช้ $source ให้ตรงตัวละครรายคน คงใบหน้า ทรงผม สีผิว สัดส่วนรูปร่างโดยรวมตามภาพอ้างอิงของตัวละครผู้ใหญ่ โดยไม่เพิ่มหรือลดสัดส่วนเอง $referenceAppeal ชุดยึดบทของฉากปัจจุบัน\n${BodyReferencePolicy.preciseIdentity}"

    fun style(aspect: String, framing: String = "", lighting: String = "") =
        "ภาพถ่ายสมจริง $aspect • ${framing.ifBlank { "จัดระยะให้เห็นใบหน้าและการกระทำครบตามฉาก" }} • ${lighting.ifBlank { "แสงเหมาะกับเวลาและสถานที่ตามบท" }}"

    fun scene(cast: String, location: String, action: String, aspect: String = "16:9",
              source: String = "ภาพ MASTER ที่แนบ", framing: String = "", lighting: String = "",
              continuity: String = "", filename: String = "", changeWardrobe: Boolean = true): String = buildString {
        append("สร้างภาพฉากละครจากไฟล์อ้างอิงที่แนบ ${if(changeWardrobe) "ใช้ชุดที่ระบุสำหรับฉากนี้ เปลี่ยนชุดเมื่อวันเวลา กิจกรรม หรือบทกำหนด เหตุการณ์ต่อเนื่องให้ใช้ชุดเดิม" else "คงเครื่องแต่งกายตามภาพอ้างอิง"} สร้างภาพจริงหนึ่งภาพ\n${identity(source)}\nตัวละครและชุด:\n$cast\nสถานที่: $location\nการกระทำ: $action\n${style(aspect,framing,lighting)}\n")
        if (continuity.isNotBlank()) append("ความต่อเนื่อง: $continuity\n")
        append("${PerformanceDirection.guide}\nFreeze one meaningful expressive instant, not a sequence or collage.\nไม่มีข้อความหรือลายน้ำ")
        if (filename.isNotBlank()) append("\nชื่อไฟล์: $filename")
        append("\nหากสร้างไม่ได้ให้แจ้งตามจริง")
    }

    fun edit(source: String, change: String, aspect: String = "16:9", cast: String = "", filename: String = ""): String = buildString {
        append("แก้ไข $source ที่แนบและแสดงภาพจริงหนึ่งภาพ\nเปลี่ยนเฉพาะ: $change\n")
        if (cast.isNotBlank()) append("ตัวละครอ้างอิง:\n$cast\n")
        append("คงใบหน้า ทรงผม สัดส่วนรูปร่างโดยรวมของตัวละครตามไฟล์อ้างอิง ไม่เพิ่มหรือลดสัดส่วนเอง $referenceAppeal คงท่าทาง มือ ระยะกล้อง แสง และองค์ประกอบที่ไม่ได้ขอเปลี่ยนตามภาพเดิม\nภาพถ่ายสมจริง $aspect ไม่มีข้อความหรือลายน้ำ")
        if (filename.isNotBlank()) append("\nชื่อไฟล์: $filename")
        append("\nหากแก้ไขไม่ได้ให้แจ้งตามจริง")
    }

    fun newCharacter(reference: String, location: String) =
        "สร้างภาพจริงหนึ่งภาพของตัวละครสมมุติผู้ใหญ่คนใหม่ ตั้งชื่อไทย ออกแบบใบหน้าและทรงผมใหม่\nอ้างอิง $reference เฉพาะสไตล์ภาพและความสมจริง\nชุด: ชุดลำลองเหมาะกับกิจกรรม\nสถานที่: $location\nการกระทำ: เลือกหนึ่งกิจกรรมในชีวิตประจำวัน\n${style("16:9")} ไม่มีข้อความหรือลายน้ำ\nแสดงภาพจริงแล้วแจ้งชื่อและชุดสั้น ๆ หากสร้างไม่ได้ให้แจ้งตามจริง"

    fun coverReference(source: String) =
        "ใช้ $source อ้างอิงตัวละคร ใบหน้า ทรงผม สัดส่วนรูปร่างโดยรวมตามต้นฉบับ $referenceAppeal ชุดและสถานที่ตามตอน จัดภาพถ่ายสมจริงให้สื่อเหตุการณ์เด่น และเว้นพื้นที่ข้อความตามรูปแบบปก"
}
