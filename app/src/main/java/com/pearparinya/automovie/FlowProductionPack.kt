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
DIRECTOR X — สร้างไฟล์คำสั่ง ไม่สร้างภาพหรือวิดีโอใน ระบบวางบท
ช่อง: $channel
หมวด: $category
${ChannelCatalog.direction(category)}
สร้างเรื่องใหม่จนจบ เลือกจำนวน EP ตามโครงเรื่อง เช่น 4–7 ตอนหรือมากกว่าหากจำเป็น พร้อมเหตุผล ไม่ล็อกไว้ที่ 4 ตอน ทุก EP ยาวอย่างน้อย 180 วินาทีจากผลรวมเวลาซีนจริง ไม่รวมปก ใช้ซีนละ 8 วินาที ค่าเริ่มต้น 24 ซีน = 192 วินาที ห้ามยืดเวลาด้วยซีนซ้ำหรือข้อความแทนเนื้อหา
แนบภาพตัวละคร: กวิน + รินรดา + เมย์ จับคู่ CHARACTER_GAWIN=กวิน / CHARACTER_RINLADA=รินรดา / CHARACTER_MIND=เมย์ ใช้เป็นรหัสอ้างอิงในพรอมป์ ไม่ใช่ชื่อไฟล์ ส่วน master ใน story.json เป็นข้อมูลภายในสำหรับแอป ภาพล่าสุดมีลำดับเหนือคำบรรยาย เก็บข้อมูลตัวละครใน CHARACTER_MEMORY.json ไม่อ้างว่า Flow จำภาพจากแชตอื่นได้
${dna.values.joinToString("\n")}
${FlowVisualDirection.identity}
${FlowVisualDirection.color}
${AutoDirectorContent.femaleFashion}
${AutoDirectorContent.dailyWardrobe}
${PerformanceDirection.guide}
${PerformanceDirection.planning}
${SoundDesign.planning}
คงใบหน้า ผม สีผิว สัดส่วนและเสน่ห์ความเซ็กซี่ตามภาพอ้างอิงของตัวละครผู้ใหญ่ ไม่เพิ่มหรือลดสัดส่วนเอง ห้ามคัดลอกชุดจาก MASTER ออกแบบชุดใหม่ตามบท วันเดียวกันใช้เสื้อ กางเกง/กระโปรง รองเท้าและเครื่องประดับเดิมทั้งวัน แม้เปลี่ยนสถานที่หรือกิจกรรม วันใหม่ต้องวางชุดใหม่ สร้าง wardrobe ledger ก่อนเขียนซีน
IMAGE PROMPT ภาษาอังกฤษเต็มในแต่ละซีน: ขึ้นต้นด้วยรายชื่อคนที่ต้องแนบและรหัส CHARACTER_GAWIN / CHARACTER_RINLADA / CHARACTER_MIND ไม่อ้างชื่อไฟล์หรือนามสกุลในพรอมป์ เฉพาะตัวละครที่อยู่จริง DNA เต็ม ชุด สี เนื้อผ้า ท่าทาง สถานะต้นคลิป สถานที่ พร็อพ แสง ระยะภาพ และตำแหน่งตัวละคร ห้ามอ้าง 'เหมือนซีนก่อน' แทนรายละเอียด
VIDEO PROMPT ภาษาอังกฤษ บทพูดไทย: 8 seconds, vertical 9:16, locked-off static camera, one continuous action; supplied scene image as first frame. ระบุผู้พูดหนึ่งคนด้วยชื่อ ผม ชุด และตำแหน่ง คนอื่นปิดปาก ห้ามพูดทับ เสียงประจำตัวคงที่ ไม่รวมเสียงบรรยายกับบทพูดในซีนเดียว ระบุเป้าหมายสายตา สถานะเริ่มและจบคลิป บทพูดต้องพูดจบใน 8 วินาที ไม่มีข้อความหรือลายน้ำ ต้องตรวจตำแหน่งกับภาพจริงก่อนใช้วิดีโอ
ส่ง ZIP จริงหนึ่งไฟล์: START.txt วิธีใช้, COVER_STORY.txt ปกรวมของเรื่อง 9:16 สำหรับ Flow หรือ ChatGPT, EP01_IMAGES.txt คำสั่งภาพฉากและปก EP, EP01_VIDEOS_FLOW.txt คำสั่งวิดีโอ Flow แยกตามจำนวนตอนจริง, BACKUP/story.json บทและความจำ ไม่สร้างภาพในระบบวางบท
คำสั่งปกต้องใช้มาตรฐานต่อไปนี้เต็มทุกใบ:
${FlowVisualDirection.poster}
${FlowVisualDirection.layout("9x16")}
ปกรวมต้องวาดข้อความจริงครบ: ชื่อช่อง + ชื่อเรื่อง + คำโปรยเรื่อง ส่วนปก EP ต้องวาดครบ: ชื่อช่อง + ชื่อเรื่อง + เลข EP และชื่อตอน จัดเป็นกลุ่มเดียว ชื่อเรื่องใหญ่ที่สุด ชุดตรง EP ใช้เหตุการณ์เปิดปม ไม่เปิดเผยตอนจบ ห้ามเว้นข้อความหรือใช้กฎไม่มีข้อความของภาพฉากกับปก
ปกแต่ละใบ: IMAGE PROMPT เต็ม ใช้ชุดประจำ EP ออกแบบแต่ละสัดส่วนใหม่ วาดข้อความไทยลงในภาพจริง: ปกรวมมีชื่อช่อง ชื่อเรื่อง คำโปรยเรื่อง; ปก EP มีชื่อช่อง ชื่อเรื่อง เลข EP และชื่อตอน ไม่เพิ่มคำโปรยเป็นบรรทัดที่สี่ จัดตัวอักษรไม่ทับหน้า เว้นขอบ ไม่มีโลโก้หรือข้อความอื่น
story.json ต้องเป็น UTF-8 และมีรูปแบบนี้ (เพิ่ม EP และซีนให้ครบ ห้ามส่งตัวอย่างนี้แทนบทเต็ม):
{"schema_version":3,"production_mode":"flow_commands","character_mode":"original","channel":"$channel","category":"$category","title":"ชื่อเรื่องไทย","tagline":"คำโปรย","duration_seconds":8,"aspect_ratio":"9:16","episode_count":4,"scene_count":96,"episodes":[{"ep":1,"title":"ชื่อตอน","tagline":"คำโปรยตอน","scene_count":24,"scenes":[{"scene":1,"duration_seconds":8,"story_day":"DAY01","story_time":"เช้า","activity":"home","wardrobe_event":"DAY01_HOME","continuity_group":"DAY01_HOME","location":"สถานที่","characters":[{"name":"เมย์","master":"MIND_MASTER.png","outfit":"ชุดเต็ม","visual_description":"ลักษณะจาก MASTER ภาษาอังกฤษ","screen_position":"frame-left"}],"start_state":"สถานะต้นคลิป","end_state":"สถานะจบคลิป","action":"เหตุการณ์ไทย","dialogue":"บทพูดไทยหรือว่าง","speaker":"ชื่อผู้พูดหรือว่าง","narration":"เสียงบรรยายหรือว่าง","image_prompt":"Full English IMAGE PROMPT","flow_prompt":"Full English VIDEO PROMPT with Thai speech"}]}]}
ค่าจำนวน EP/scene_count เป็นตัวอย่างรูปแบบ ต้องแทนด้วยจำนวนจริง เช็กทุก EP ≥180 วินาที ชุดต่อเนื่อง ผู้พูดถูกคน และพรอมป์ครบก่อนส่งไฟล์ ห้ามอ้างว่าสร้างหรือตรวจภาพจริงแล้ว แสดงชื่อเรื่อง คำโปรย จำนวน EP ซีนและเวลารวม พร้อมลิงก์ ZIP ที่สร้างจริง
    """.trimIndent()

    fun issues(plan: JSONObject): List<String> = buildList {
        addAll(FlowCast.issues(plan))
        if(plan.optString("character_mode","original")!="original") add("ชุดนี้ใช้ตัวละครหลัก 3 คนและตัวละครเสริมที่ลงทะเบียนในเรื่อง กรุณาใช้เครื่องมือเดิมสำหรับโหมดตัวละครใหม่ทั้งหมด")
        if(plan.optString("title").isBlank()) add("ขาดชื่อเรื่อง")
        if(plan.optString("aspect_ratio","16:9")!="9:16") add("ชุด Flow นี้กำหนดภาพฉาก 9:16")
        if(plan.optInt("performance_version")>=1 && plan.optJSONArray("episodes")!=null) {
            AutoDirectorWardrobe.shots(plan).forEach { shot -> runCatching { PerformanceDirection.validate(shot.scene) }.exceptionOrNull()?.let { add("EP${shot.ep} SC${shot.scene.optInt("scene")}: ${it.message}") } }
        }
        if(plan.optString("content_profile")==AutoDirectorContent.profile && plan.optJSONArray("episodes")!=null) addAll(AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(plan)))
        if(plan.optInt("audio_version")>=1) AutoDirectorWardrobe.shots(plan).forEach { shot -> runCatching { SoundDesign.validate(shot.scene) }.exceptionOrNull()?.let { add("EP${shot.ep}: ${it.message}") } }
        val eps=plan.optJSONArray("episodes")
        if(eps==null || eps.length()==0) { add("ยังไม่มี EP"); return@buildList }
        for(i in 0 until eps.length()) {
            val ep=eps.getJSONObject(i); val scenes=ep.optJSONArray("scenes")
            if(ep.optInt("ep")!=i+1) add("เลข EP ต้องเรียงจาก 1")
            if(scenes==null || scenes.length()==0) { add("EP ${i+1} ไม่มีซีน"); continue }
            if(plan.optString("content_profile")==AutoDirectorContent.profile) {
                val expected=runCatching { ClipPlan.count(plan) }.getOrDefault(24)
                if(scenes.length()!=expected) add("AutoDirector EP ${i+1} ต้องมี $expected ฉาก")
                runCatching { AutoDirectorContent.validateWardrobes(ep,plan.optInt("autodirector_version")>=AutoDirectorContent.version) }.exceptionOrNull()?.let { add(it.message ?: "ชุดต้องครบ 4 ส่วน") }
            }
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
                val keys=people.map { FlowCast.id(it) }
                if(keys.any { it==null } || keys.distinct().size!=keys.size) add("$id ตัวละครไม่ตรง MASTER หรือซ้ำ")
                people.forEach { p ->
                    if(!FlowCast.matches(plan,p)) add("$id MASTER ไม่ตรงคน")
                    for(field in listOf("outfit","visual_description","screen_position")) if(p.optString(field).isBlank()) add("$id ขาด $field ของ ${p.optString("name")}")
                }
                val dialogue=s.optString("dialogue").trim().takeUnless { it in listOf("ไม่มี","none") }.orEmpty()
                if(dialogue.isNotBlank() && people.none { it.optString("name")==s.optString("speaker") || StoryIdentity.keyFor(s.optString("speaker"))?.let { key -> key==FlowCast.id(it) }==true }) add("$id ผู้พูดไม่อยู่ในฉาก")
                if(dialogue.isNotBlank() && s.optString("narration").trim() !in listOf("","ไม่มี","none")) add("$id บทพูดกับบรรยายต้องแยกซีน")
            }
            if(plan.optInt("clip_plan_version")==1) {
                runCatching { ClipPlan.count(plan) }.fold(onSuccess={ expected ->
                    if(scenes.length()!=expected || total!=expected*8) add("EP ${i+1} จำนวนซีนหรือเวลาไม่ตรงที่เลือก: ต้อง $expected ซีน ${expected*8} วินาที"); Unit
                },onFailure={ add("รูปแบบหรือระยะเวลาคลิปไม่ถูกต้อง"); Unit })
            } else if(total<minimumSeconds) add("EP ${i+1} ยาว $total วินาที ต้องอย่างน้อย $minimumSeconds")
        }
    }
    fun write(output: OutputStream, plan: JSONObject?, masters: Map<String,File>, memory: JSONObject, category: String, channel: String) {
        val definitions=FlowCast.definitions(plan)
        require(definitions.all { masters[FlowCast.id(it)]?.isFile==true }) { "ภาพ MASTER ต้องครบตัวละครหลัก 3 คนและตัวละครเสริมที่ลงทะเบียน" }
        if(plan!=null) require(issues(plan).isEmpty()) { issues(plan).joinToString("\n") }
        ZipOutputStream(output).use { zip ->
            fun put(path: String,text: String) { zip.putNextEntry(ZipEntry("DIRECTOR_FLOW/$path")); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            val refs=JSONObject(memory.toString())
            definitions.forEach { person ->
                val id=FlowCast.id(person)!!; val name=person.getString("name")
                val file=masters.getValue(id)
                val signature=ByteArray(12).also { bytes -> file.inputStream().use { it.read(bytes) } }
                val ext=when { signature[0]==0xff.toByte() && signature[1]==0xd8.toByte() -> "jpg"; String(signature.copyOfRange(0,4))=="RIFF" -> "webp"; else -> "png" }
                val path="MASTERS/${id.uppercase()}_MASTER.$ext"
                zip.putNextEntry(ZipEntry("DIRECTOR_FLOW/$path")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                refs.put(id,JSONObject().put("name",name).put("reference_label",FlowReferencePrompt.alias(id)).put("file",path).put("dna",plan?.optJSONObject("lead_references")?.optJSONObject(id)?.optString("visual_description")?.takeIf { it.isNotBlank() } ?: dna[id] ?: person.optString("visual_description")).put("sha256",EpisodeMedia.digest(file)).put("profile",memory.optJSONObject(id) ?: JSONObject()))
            }
            val allPeople=names.map { (id,name) -> JSONObject().put("name",name).put("outfit","เลือกชุดใหม่ตามบท").put("screen_position","ตามบท") }
            val start=StringBuilder(FlowReferencePrompt.header(allPeople))
            start.append("\nDIRECTOR X • เริ่มใช้งาน\nเริ่มจาก COVER_STORY.txt เพื่อสร้างปกรวมของเรื่องใน Flow หรือ ChatGPT จากนั้นเปิด EPxx_IMAGES.txt เพื่อสร้างภาพฉากและปกตอน ส่วน EPxx_VIDEOS_FLOW.txt เป็นแม่แบบวิดีโอสำหรับ Flow เท่านั้น เลือกคัดลอกหนึ่งบล็อกตั้งแต่บรรทัดแนบภาพจนจบบล็อก แนบเฉพาะภาพคนที่ระบุในบล็อกนั้น สร้างทีละปกหรือทีละซีน ไม่ส่งทั้งตอนไปให้ Flow สร้างครั้งเดียว\nรูปจริงอยู่ใน MASTERS ส่วนชื่ออ้างอิงในคำสั่งเป็นรหัส CHARACTER ไม่ขึ้นกับชื่อไฟล์หรือชื่ออัปโหลด\nสร้างภาพฉากจาก MASTER → นำเข้าภาพในเมนู EP / ซีน → เทียบ MASTER และยืนยันภาพพร้อมตำแหน่งผู้พูด → เปิดคำสั่งวิดีโอและแนบภาพที่ยืนยันเป็นเฟรมเริ่มต้น → ตรวจคลิปต้น กลาง ท้าย หากภาพ ชุด บท หรือ MASTER เปลี่ยนต้องตรวจใหม่ แม่แบบวิดีโอใน ZIP ยังไม่ใช่ภาพที่ผ่านการยืนยัน ตรวจภาพ ผู้พูด ชุดและตำแหน่งก่อนสร้างคลิป นำ MP4 กลับเข้าแอปเพื่อรวมตอน BACKUP/story.json ใช้นำกลับเข้าแอปและเก็บข้อมูลความจำ/ความต่อเนื่อง ไม่ต้องแนบไฟล์นี้ใน Flow\nยังไม่ได้สร้างหรือทดสอบภาพและวิดีโอจริง\n")
            if(plan==null) start.append("\nชุดแม่บทเริ่มงาน ยังไม่มีบท EP\n").append(masterCommand(category,channel))
            else {
                val commandPlan=JSONObject(plan.toString()).apply { if(optString("channel").isBlank()) this.put("channel",channel) }
                val allEpisodes=plan.getJSONArray("episodes")
                for(e in 0 until allEpisodes.length()) {
                    val number=allEpisodes.getJSONObject(e).getInt("ep")
                    put("COMMANDS/EP%02d_IMAGES.txt".format(number),SceneCommandCards.join(SceneCommandCards.images(commandPlan,number)))
                    put("COMMANDS/EP%02d_VIDEOS.txt".format(number),SceneCommandCards.join(SceneCommandCards.videos(commandPlan,number)))
                }
                val backup=JSONObject(plan.toString()).put("character_memory",refs)
                put("BACKUP/story.json",backup.toString(2))
                if(plan.optString("content_profile")==AutoDirectorContent.profile) {
                    put("AUTODIRECTOR/01_OUTLINE.txt",AutoDirectorContent.outline(plan))
                    put("AUTODIRECTOR/WARDROBE_LOCKS.txt",AutoDirectorWardrobe.catalog(AutoDirectorWardrobe.shots(plan)))
                    put("AUTODIRECTOR/02_COVER_STORY_GEMINI.txt",AutoDirectorContent.covers(plan))
                    put("AUTODIRECTOR/ALL_GEMINI_NEXT.txt",AutoDirectorContent.geminiNext(plan))
                    val episodes=plan.getJSONArray("episodes")
                    for(e in 0 until episodes.length()) {
                        val number=episodes.getJSONObject(e).getInt("ep")
                        put("AUTODIRECTOR/EP%02d_IMAGES_GEMINI.txt".format(number),AutoDirectorContent.images(plan,number))
                        put("AUTODIRECTOR/EP%02d_GEMINI_NEXT.txt".format(number),AutoDirectorContent.geminiNext(plan,number))
                        put("AUTODIRECTOR/EP%02d_DIALOGUE.txt".format(number),AutoDirectorContent.dialogue(plan,number))
                        put("AUTODIRECTOR/EP%02d_FLOW.xml".format(number),AutoDirectorContent.flowXml(plan,number))
                    }
                    start.append("\nGemini NEXT: เปิด AUTODIRECTOR/ALL_GEMINI_NEXT.txt เพื่อคัดลอกทั้งเรื่อง หรือ EPxx_GEMINI_NEXT.txt เฉพาะตอน แนบ MASTER ตามรายการ วางคำสั่งครั้งเดียว สร้างทีละภาพแล้วพิมพ์ next; ไม่สร้างวิดีโอใน Gemini ถ้าข้อความยาวเกินช่องแชตให้แนบไฟล์ TXT พร้อม MASTER แล้วสั่งอ่านไฟล์และเริ่มคิว\n")
                    start.append("\nAutoDirector v45: ใช้ไฟล์ใน AUTODIRECTOR ตามลำดับ โครงเรื่อง → ปกรวม Gemini → ภาพ Gemini ราย EP → บทพูด → XML สำหรับคัดลอกไป Flow ภาพอ้างอิงอยู่ใน MASTERS โดย The Man=GAWIN, Woman 1=RINLADA, Woman 2=MIND; ตัวเสริม Supporting ตามรหัสไฟล์ XML เป็นเอกสารจัดบท ไม่ใช่ไฟล์รัน Flow และยังต้องตรวจภาพจริงก่อนใช้\n")
                }
                val leads=names.map { (id,name) ->
                    val epRows=plan.getJSONArray("episodes")
                    var found: JSONObject?=null
                    for(e in 0 until epRows.length()) {
                        val scenes=epRows.getJSONObject(e).getJSONArray("scenes")
                        for(j in 0 until scenes.length()) if(found==null) found=FlowReferencePrompt.people(scenes.getJSONObject(j)).firstOrNull { FlowCast.id(it)==id }
                    }
                    found ?: JSONObject().put("name",name).put("master","${id.uppercase()}_MASTER.png").put("outfit","ชุดใหม่สำหรับปกรวมตามบท ไม่คัดลอกชุดต้นฉบับ").put("screen_position","จัดตามองค์ประกอบปก")
                }
                val premise=plan.optString("cover_incident").ifBlank { plan.getJSONArray("episodes").getJSONObject(0).optString("cover_incident").ifBlank { plan.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0).getString("action") } }
                val mainCover=StringBuilder()
                for(ratio in listOf("9x16","16x9")) mainCover.append(FlowVisualDirection.storyCover(ratio,FlowReferencePrompt.header(leads),plan.optString("channel",channel),plan.getString("title"),plan.optString("tagline"),FlowReferencePrompt.normalize(premise))).append("\n=== END STORY COVER ===\n\n")
                put("COVER_STORY.txt",mainCover.toString())
                val eps=plan.getJSONArray("episodes")
                for(i in 0 until eps.length()) {
                    val ep=eps.getJSONObject(i); val number=ep.getInt("ep"); val scenes=ep.getJSONArray("scenes")
                    val coverPeople=linkedMapOf<String,JSONObject>()
                    var total=0
                    for(j in 0 until scenes.length()) {
                        val scene=scenes.getJSONObject(j); total+=scene.optInt("duration_seconds",8)
                        FlowReferencePrompt.people(scene).forEach { person ->
                            val id=FlowCast.id(person)!!
                            if(id !in coverPeople) coverPeople[id]=person
                        }
                    }
                    val text=StringBuilder(FlowReferencePrompt.header(coverPeople.values.toList()))
                    val videos=StringBuilder("คำสั่งวิดีโอ Flow EP%02d • ใช้ภาพฉากที่ตรวจแล้วเป็นเฟรมเริ่มต้นทีละซีน\n".format(number))
                    text.append("\nEP %02d • %s\nDuration: %d seconds\nเลือกคัดลอกปกหรือซีนทีละบล็อก พร้อมรายการแนบภาพด้านบนของบล็อกนั้น\n".format(number,ep.optString("title"),total))
                    val cue=ep.optString("cover_incident").ifBlank { scenes.getJSONObject(0).getString("action") }
                    for(ratio in listOf("9x16","16x9")) {
                        text.append("\n=== COVER $ratio • BEGIN ===\n").append(FlowVisualDirection.cover(ratio,FlowReferencePrompt.header(coverPeople.values.toList()),plan.optString("channel",channel),plan.getString("title"),number,
                            ep.optString("title"),ep.optString("tagline").ifBlank { plan.optString("tagline") },FlowReferencePrompt.normalize(cue))).append("\n=== COVER $ratio • END ===\n")
                    }
                    for(j in 0 until scenes.length()) {
                        text.append("\n").append(FlowReferencePrompt.scene(scenes.getJSONObject(j),number,"image")).append("=== END SCENE ===\n")
                        videos.append("\n").append(FlowReferencePrompt.scene(scenes.getJSONObject(j),number,"video")).append("=== END SCENE ===\n")
                    }
                    put("EP%02d_IMAGES.txt".format(number),text.toString())
                    put("EP%02d_VIDEOS_FLOW.txt".format(number),videos.toString())
                }
                start.append("\nบทพร้อม ").append(eps.length()).append(" EP • ตรวจเวลาทุก EP ตามรูปแบบที่เลือกแล้ว\n")
            }
            put("START.txt",start.toString())
        }
    }
}
