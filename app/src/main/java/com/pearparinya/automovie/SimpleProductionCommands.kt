package com.pearparinya.automovie

import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Two user-facing documents; project data and MASTER images remain in the app. */
internal object SimpleProductionCommands {
    const val IMAGES = "01_IMAGES_GEMINI_NEXT.txt"
    const val FLOW = "02_VIDEOS_FLOW.txt"
    private fun cards(story: JSONObject, images: Boolean): List<SceneCommandCards.Card> {
        val eps=story.getJSONArray("episodes")
        return buildList {
            for(i in 0 until eps.length()) {
                val number=eps.getJSONObject(i).getInt("ep")
                val rows=if(images) SceneCommandCards.images(story,number) else SceneCommandCards.videos(story,number)
                rows.filter { i==0 || !it.title.startsWith("ปกรวม") }.forEach { card ->
                    add(card.copy(title=if(card.title.startsWith("ปกรวม")) card.title else "EP.$number • ${card.title}"))
                }
            }
        }
    }
    fun images(story: JSONObject): String {
        val rows=cards(story,true)
        return """คำสั่งสร้างรูปทั้งเรื่อง • Gemini
แนบไฟล์นี้พร้อมภาพ MASTER ที่ระบุในแต่ละคำสั่ง แล้วเริ่มภาพแรกเท่านั้น
สร้างทีละภาพ รอ next จึงทำภาพถัดไป หัวข้อใช้แยกงาน ไม่ใช่ข้อความให้วาดลงซีน
ปกต้องมีข้อความตามคำสั่งครบ ส่วนซีนเนื้อเรื่องไม่มีข้อความ
ใช้ขนาดตามแต่ละงาน: ปกมีทั้ง 9:16 และ 16:9; ซีนทั้งหมดเป็น 9:16
แก้ภาพ / retry = แก้ภาพปัจจุบันตามรายละเอียด ไม่เลื่อนคิว รักษา MASTER และชุดประจำวันเดิม
ถ้ารูปไม่สำเร็จหรืออ้างอิงไม่ครบ ให้คง PENDING และแจ้งตามจริง ห้ามข้ามภาพ
ถ้าบริบทหาย ให้ขอไฟล์และ MASTER ใหม่พร้อมระบุหัวข้อที่จะทำต่อ
ครบ ${rows.size} ภาพแล้วให้หยุด ไม่สร้างเพิ่ม ชื่อช่อง ชื่อเรื่อง คำโปรย/ชื่อตอนบนปกต้องตรงตามคำสั่ง

"""+SceneCommandCards.join(rows)
    }
    fun videos(story: JSONObject): String = """คำสั่งวิดีโอทั้งเรื่อง • Flow • แนวตั้ง 9:16 • ซีนละ 8 วินาที
เลือกคัดลอกคำสั่งของ EP / ซีนที่ต้องการ พร้อมแนบภาพซีนเดียวกันที่ตรวจแล้วเป็นเฟรมเริ่มต้น
แต่ละบล็อกรวมการแสดง บทพูด เสียงบรรยากาศและเอฟเฟกต์ไว้แล้ว ไม่ต้องเปิดไฟล์บทพูดหรือ XML เพิ่ม
ไฟล์นี้เป็นชุดคำสั่ง ไม่ได้สั่ง Flow สร้างทั้งเรื่องอัตโนมัติ

"""+SceneCommandCards.join(cards(story,false))
    fun write(output: OutputStream,story: JSONObject) {
        val imageText=images(story); val videoText=videos(story)
        ZipOutputStream(output).use { zip ->
            listOf(IMAGES to imageText,FLOW to videoText).forEach { (name,text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
    }
}
