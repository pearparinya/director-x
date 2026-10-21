package com.pearparinya.automovie

import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portable reference memory and text prompts; no image generation or remote-memory claims. */
internal object FlowProductionPack {
    const val minimumSeconds = 180
    val names = linkedMapOf("gawin" to "กวิน", "rinlada" to "รินรดา", "mind" to "เมย์")
    val dna = mapOf(
        "gawin" to "Kawin: adult fictional man, short dark swept textured hair, defined jaw, thick brows, brown eyes, narrow mustache and short chin/jaw beard. Match his own attached MASTER; preserve reference build without adding muscles.",
        "rinlada" to "Rinrada: adult fictional woman, long dark voluminous wavy hair, tapered oval face, arched dark brows, berry-rose lips, small gold hoops and fine gold pendant necklace. Match her own MASTER; preserve original overall proportions.",
        "mind" to "May: adult fictional woman, chin-length chestnut-auburn bob, softly rounded oval face, full cheeks and coral-pink lips. Match her own MASTER; preserve original overall proportions. Do not give her Rinrada's long hair or jewelry."
    )
    fun masterCommand(category: String, channel: String): String = """
DIRECTOR FLOW STUDIO — สร้างไฟล์คำสั่ง ไม่สร้างภาพหรือวิดีโอใน ระบบวางบท
ช่อง: $channel
หมวด: $category
สร้างเรื่องใหม่จนจบ เลือกจำนวน EP ตามโครงเรื่อง เช่น 4–7 ตอนหรือมากกว่าหากจำเป็น พร้อมเหตุผล ไม่ล็อกไว้ที่ 4 ตอน ทุก EP ยาวอย่างน้อย 180 วินาทีจากผลรวมเวลาซีนจริง ไม่รวมปก ใช้ซีนละ 8 วินาที ค่าเริ่มต้น 24 ซีน = 192 วินาที ห้ามยืดเวลาด้วยซีนซ้ำหรือข้อความแทนเนื้อหา
แนบภาพตัวละคร: กวิน + รินรดา + เมย์ จับคู่ CHARACTER_GAWIN=กวิน / CHARACTER_RINLADA=รินรดา / CHARACTER_MIND=เมย์ ใช้เป็นรหัสอ้างอิงในพรอมป์ ไม่ใช่ชื่อไฟล์ ส่วน master ใน story.json เป็นข้อมูลภายในสำหรับแอป ภาพล่าสุดมีลำดับเหนือคำบรรยาย เก็บข้อมูลตัวละครใน CHARACTER_MEMORY.json ไม่อ้างว่า Flow จำภาพจากแชตอื่นได้
${dna.values.joinToString("\n")}
${FlowVisualDirection.identity}
${FlowVisualDirection.color}
คงใบหน้า ผม สีผิว สัดส่วนและเสน่ห์ความเซ็กซี่ตามภาพอ้างอิงของตัวละครผู้ใหญ่ ไม่เพิ่มหรือลดสัดส่วนเอง เปลี่ยนชุดจาก MASTER ให้เหมาะกับเวลาและกิจกรรม แต่เหตุการณ์ต่อเนื่องใช้ชุดเดิม สร้าง wardrobe ledger ก่อนเขียนซีน
IMAGE PROMPT ภาษาอังกฤษเต็มในแต่ละซีน: ขึ้นต้นด้วยรายชื่อคนที่ต้องแนบและรหัส CHARACTER_GAWIN / CHARACTER_RINLADA / CHARACTER_MIND ไม่อ้างชื่อไฟล์หรือนามสกุลในพรอมป์ เฉพาะตัวละครที่อยู่จริง DNA เต็ม ชุด สี เนื้อผ้า ท่าทาง สถานะต้นคลิป สถานที่ พร็อพ แสง ระยะภาพ และตำแหน่งตัวละคร ห้ามอ้าง 'เหมือนซีนก่อน' แทนรายละเอียด
VIDEO PROMPT ภาษาอังกฤษ บทพูดไทย: 8 seconds, vertical 9:16, locked-off static camera, one continuous action; supplied scene image as first frame. ระบุผู้พูดหนึ่งคนด้วยชื่อ ผม ชุด และตำแหน่ง คนอื่นปิดปาก ห้ามพูดทับ เสียงประจำตัวคงที่ ไม่รวมเสียงบรรยายกับบทพูดในซีนเดียว ระบุเป้าหมายสายตา สถานะเริ่มและจบคลิป บทพูดต้องพูดจบใน 8 วินาที ไม่มีข้อความหรือลายน้ำ ต้องตรวจตำแหน่งกับภาพจริงก่อนใช้วิดีโอ
ส่ง ZIP จริงหนึ่งไฟล์: START.txt รวมวิธีใช้และข้อมูลตัวละคร, EP01.txt แยกตามจำนวนตอนจริง แต่ละไฟล์รวมคำสั่งปก 9:16 / 16:9 และทุกซีน, BACKUP/story.json รวมข้อมูลความจำและความต่อเนื่อง ไม่สร้างภาพในระบบวางบท
คำสั่งปกต้องใช้มาตรฐานต่อไปนี้เต็มทุกใบ:
${FlowVisualDirection.poster}
${FlowVisualDirection.layout("9x16")}
${FlowVisualDirection.layout("16x9")}
จัดชื่อช่อง ชื่อเรื่อง EP และคำโปรยเป็นกลุ่มเดียว ชื่อเรื่องใหญ่ที่สุด ชุดตรง EP ใช้เหตุการณ์เปิดปม ไม่เปิดเผยตอนจบ
ปกแต่ละใบ: IMAGE PROMPT เต็ม ใช้ชุดประจำ EP ออกแบบแต่ละสัดส่วนใหม่ รายการข้อความไทยตรงชื่อช่อง ชื่อเรื่อง เลข EP และชื่อตอน จัดตัวอักษรไม่ทับหน้า เว้นขอบ ไม่มีโลโก้หรือข้อความอื่น
story.json ต้องเป็น UTF-8 และมีรูปแบบนี้ (เพิ่ม EP และซีนให้ครบ ห้ามส่งตัวอย่างนี้แทนบทเต็ม):
{"schema_version":3,"production_mode":"flow_commands","character_mode":"original","channel":"$channel","category":"$category","title":"ชื่อเรื่องไทย","tagline":"คำโปรย","duration_seconds":8,"aspect_ratio":"9:16","episode_count":4,"scene_count":96,"episodes":[{"ep":1,"title":"ชื่อตอน","tagline":"คำโปรยตอน","scene_count":24,"scenes":[{"scene":1,"duration_seconds":8,"story_day":"DAY01","story_time":"เช้า","activity":"home","wardrobe_event":"DAY01_HOME","continuity_group":"DAY01_HOME","location":"สถานที่","characters":[{"name":"เมย์","master":"MIND_MASTER.png","outfit":"ชุดเต็ม","visual_description":"ลักษณะจาก MASTER ภาษาอังกฤษ","screen_position":"frame-left"}],"start_state":"สถานะต้นคลิป","end_state":"สถานะจบคลิป","action":"เหตุการณ์ไทย","dialogue":"บทพูดไทยหรือว่าง","speaker":"ชื่อผู้พูดหรือว่าง","narration":"เสียงบรรยายหรือว่าง","image_prompt":"Full English IMAGE PROMPT","flow_prompt":"Full English VIDEO PROMPT with Thai speech"}]}]}
ค่าจำนวน EP/scene_count เป็นตัวอย่างรูปแบบ ต้องแทนด้วยจำนวนจริง เช็กทุก EP ≥180 วินาที ชุดต่อเนื่อง ผู้พูดถูกคน และพรอมป์ครบก่อนส่งไฟล์ ห้ามอ้างว่าสร้างหรือตรวจภาพจริงแล้ว แสดงชื่อเรื่อง คำโปรย จำนวน EP ซีนและเวลารวม พร้อมลิงก์ ZIP ที่สร้างจริง
    """.trimIndent()

    fun issues(plan: JSONObject): List<String> = buildList {
        if(plan.optString("character_mode","original")!="original") add("ชุด Flow นี้ใช้ MASTER กวิน รินรดา เมย์เท่านั้น กรุณาใช้เครื่องมือเดิมสำหรับตัวละครใหม่")
        if(plan.optString("title").isBlank()) add("ขาดชื่อเรื่อง")
        if(plan.optString("aspect_ratio","9:16")!="9:16") add("ชุด Flow นี้กำหนดภาพฉาก 9:16")
        val eps=plan.optJSONArray("episodes")
        if(eps==null || eps.length()==0) { add("ยังไม่มี EP"); return@buildList }
        for(i in 0 until eps.length()) {
            val ep=eps.getJSONObject(i); val scenes=ep.optJSONArray("scenes")
            if(ep.optInt("ep")!=i+1) add("เลข EP ต้องเรียงจาก 1")
            if(scenes==null || scenes.length()==0) { add("EP ${i+1} ไม่มีซีน"); continue }
            var total=0
            for(j in 0 until scenes.length()) {
                val s=scenes.getJSONObject(j); val id="EP${i+1} ซีน${j+1}"
                val duration=s.optInt("duration_seconds",plan.optInt("duration_seconds",8))
                total+=duration
                if(duration!=8) add("$id ต้องใช้ 8 วินาทีสำหรับชุด Veo นี้")
                if(s.optInt("scene")!=j+1) add("$id เลขซีนไม่ต่อเนื่อง")
                for(field in listOf("action","image_prompt","flow_prompt","location","start_state","end_state"))
                    if(s.optString(field).isBlank()) add("$id ขาด $field")
                val cast=s.optJSONArray("characters")
                if(cast==null || cast.length()==0) { add("$id ขาดตัวละคร"); continue }
                val people=(0 until cast.length()).map { cast.getJSONObject(it) }
                val keys=people.map { StoryIdentity.keyFor(it.optString("name")) }
                if(keys.any { it !in names.keys } || keys.distinct().size!=keys.size) add("$id ตัวละครไม่ตรง MASTER หรือซ้ำ")
                people.forEach { p ->
                    if(!StoryIdentity.matchesMaster(p.optString("name"),p.optString("master"))) add("$id MASTER ไม่ตรงคน")
                    for(field in listOf("outfit","visual_description","screen_position")) if(p.optString(field).isBlank()) add("$id ขาด $field ของ ${p.optString("name")}")
                }
                val dialogue=s.optString("dialogue").trim().takeUnless { it in listOf("ไม่มี","none") }.orEmpty()
                if(dialogue.isNotBlank() && StoryIdentity.keyFor(s.optString("speaker")) !in keys) add("$id ผู้พูดไม่อยู่ในฉาก")
                if(dialogue.isNotBlank() && s.optString("narration").trim() !in listOf("","ไม่มี","none")) add("$id บทพูดกับบรรยายต้องแยกซีน")
            }
            if(total<minimumSeconds) add("EP ${i+1} ยาว $total วินาที ต้องอย่างน้อย $minimumSeconds")
        }
    }
    fun write(output: OutputStream, plan: JSONObject?, masters: Map<String,File>, memory: JSONObject, category: String, channel: String) {
        require(names.keys.all { masters[it]?.isFile==true }) { "MASTER ต้องครบทั้ง 3 คน" }
        if(plan!=null) require(issues(plan).isEmpty()) { issues(plan).joinToString("\n") }
        ZipOutputStream(output).use { zip ->
            fun put(path: String,text: String) { zip.putNextEntry(ZipEntry("DIRECTOR_FLOW/$path")); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            val refs=JSONObject(memory.toString())
            names.forEach { (id,name) ->
                val file=masters.getValue(id)
                val signature=ByteArray(12).also { bytes -> file.inputStream().use { it.read(bytes) } }
                val ext=when { signature[0]==0xff.toByte() && signature[1]==0xd8.toByte() -> "jpg"; String(signature.copyOfRange(0,4))=="RIFF" -> "webp"; else -> "png" }
                val path="MASTERS/${id.uppercase()}_MASTER.$ext"
                zip.putNextEntry(ZipEntry("DIRECTOR_FLOW/$path")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                refs.put(id,JSONObject().put("name",name).put("reference_label",FlowReferencePrompt.alias(id)).put("file",path).put("dna",dna[id]).put("sha256",EpisodeMedia.digest(file)).put("profile",memory.optJSONObject(id) ?: JSONObject()))
            }
            val allPeople=names.map { (id,name) -> JSONObject().put("name",name).put("outfit","เลือกชุดใหม่ตามบท").put("screen_position","ตามบท") }
            val start=StringBuilder(FlowReferencePrompt.header(allPeople))
            start.append("\nDIRECTOR FLOW • เริ่มใช้งาน\nเปิด EPxx.txt ที่ต้องการ แต่ละตอนรวมปก 2 สัดส่วนและทุกซีนไว้แล้ว เลือกคัดลอกหนึ่งบล็อกตั้งแต่บรรทัดแนบภาพจนจบบล็อก แนบเฉพาะภาพคนที่ระบุในบล็อกนั้น สร้างทีละปกหรือทีละซีน ไม่ส่งทั้งตอนไปให้ Flow สร้างครั้งเดียว\nรูปจริงอยู่ใน MASTERS ส่วนชื่ออ้างอิงในคำสั่งเป็นรหัส CHARACTER ไม่ขึ้นกับชื่อไฟล์หรือชื่ออัปโหลด\nตรวจภาพ ผู้พูด ชุดและตำแหน่งก่อนสร้างคลิป นำ MP4 กลับเข้าแอปเพื่อรวมตอน BACKUP/story.json ใช้นำกลับเข้าแอปและเก็บข้อมูลความจำ/ความต่อเนื่อง ไม่ต้องแนบไฟล์นี้ใน Flow\nยังไม่ได้สร้างหรือทดสอบภาพและวิดีโอจริง\n")
            if(plan==null) start.append("\nชุดแม่บทเริ่มงาน ยังไม่มีบท EP\n").append(masterCommand(category,channel))
            else {
                val backup=JSONObject(plan.toString()).put("character_memory",refs)
                put("BACKUP/story.json",backup.toString(2))
                val eps=plan.getJSONArray("episodes")
                for(i in 0 until eps.length()) {
                    val ep=eps.getJSONObject(i); val number=ep.getInt("ep"); val scenes=ep.getJSONArray("scenes")
                    val coverPeople=linkedMapOf<String,JSONObject>()
                    var total=0
                    for(j in 0 until scenes.length()) {
                        val scene=scenes.getJSONObject(j); total+=scene.optInt("duration_seconds",8)
                        FlowReferencePrompt.people(scene).forEach { person ->
                            val id=StoryIdentity.keyFor(person.getString("name"))!!
                            if(id !in coverPeople) coverPeople[id]=person
                        }
                    }
                    val text=StringBuilder(FlowReferencePrompt.header(coverPeople.values.toList()))
                    text.append("\nEP %02d • %s\nDuration: %d seconds\nเลือกคัดลอกปกหรือซีนทีละบล็อก พร้อมรายการแนบภาพด้านบนของบล็อกนั้น\n".format(number,ep.optString("title"),total))
                    val cue=ep.optString("cover_incident").ifBlank { scenes.getJSONObject(0).getString("action") }
                    for(ratio in listOf("9x16","16x9")) {
                        text.append("\n=== COVER $ratio • BEGIN ===\n").append(FlowVisualDirection.cover(ratio,FlowReferencePrompt.header(coverPeople.values.toList()),plan.optString("channel",channel),plan.getString("title"),number,
                            ep.optString("title"),ep.optString("tagline").ifBlank { plan.optString("tagline") },FlowReferencePrompt.normalize(cue))).append("\n=== COVER $ratio • END ===\n")
                    }
                    for(j in 0 until scenes.length()) text.append("\n").append(FlowReferencePrompt.scene(scenes.getJSONObject(j),number)).append("=== END SCENE ===\n")
                    put("EP%02d.txt".format(number),text.toString())
                }
                start.append("\nบทพร้อม ").append(eps.length()).append(" EP • ตรวจเวลาทุก EP อย่างน้อย 180 วินาทีแล้ว\n")
            }
            put("START.txt",start.toString())
        }
    }
}
