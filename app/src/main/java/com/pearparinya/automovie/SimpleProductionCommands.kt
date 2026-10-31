package com.pearparinya.automovie

import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Compact structured commands. Never reuse stale image/video prose. Two documents per EP. */
internal object SimpleProductionCommands {
    data class Task(val id: String,val title: String,val ratio: String,val command: String,val references: List<JSONObject> = emptyList())
    fun imageName(ep: Int)="EP%02d_IMAGES.txt".format(ep)
    fun flowName(ep: Int)="EP%02d_FLOW.txt".format(ep)
    private fun episode(story: JSONObject,number: Int): JSONObject {
        val eps=story.getJSONArray("episodes")
        return (0 until eps.length()).map { eps.getJSONObject(it) }.first { it.getInt("ep")==number }
    }
    private fun firstEpisode(story: JSONObject)=story.getJSONArray("episodes").getJSONObject(0).getInt("ep")
    private fun people(ep: JSONObject): List<JSONObject> {
        val all=linkedMapOf<String,JSONObject>(); val rows=ep.getJSONArray("scenes")
        for(i in 0 until rows.length()) FlowReferencePrompt.people(rows.getJSONObject(i)).forEach { all.putIfAbsent(it.getString("name"),it) }
        return all.values.toList()
    }
    private const val identity="Fictional adults 25+. Match each person's own attached MASTER: face, hairstyle, skin and natural body proportions. No mixing, enlarging or slimming. MASTER supplies identity only; use the exact planned outfit and accessories below, never reference clothing. Opaque lined editorial fashion; natural anatomy and fabric."
    private fun cast(people: List<JSONObject>,poster: Boolean=false)=people.mapIndexed { i,p ->
        val position=if(!poster) p.optString("screen_position") else when(people.size) { 1 -> "center"; 2 -> if(i==0) "left" else "right"; else -> listOf("left","center","right")[i] }
        val alias=when(FlowCast.id(p)) { "gawin" -> "Kawin / Gawin"; "rinlada" -> "Rinrada / Rinlada"; "mind" -> "May / Mind"; else -> p.getString("name") }
        "${p.getString("name")} ($alias) | MASTER: ${p.getString("master")} | position: $position\nOutfit: ${p.getString("outfit")}"
    }.joinToString("\n")
    private fun acting(scene: JSONObject)=FlowReferencePrompt.people(scene).mapNotNull { person ->
        person.optJSONObject("performance")?.let { p ->
            person.getString("name")+": "+listOf("emotion","objective","subtext","gaze","body_language","voice","emotional_arc").mapNotNull { k -> p.optString(k).takeIf { it.isNotBlank() }?.let { "$k=$it" } }.joinToString("; ")
        }
    }.joinToString("\n")
    private fun light(scene: JSONObject)=scene.optString("lighting").ifBlank { "Bright neutral-white 5500K lighting appropriate to the location/time; natural skin and fabric detail, no yellow cast. Night stays at night with neutral artificial light." }
    fun imageTasks(story: JSONObject,number: Int): List<Task> = buildList {
        LandscapeProduction.requirePlan(story)
        val ep=episode(story,number); val rows=ep.getJSONArray("scenes"); val single=story.getJSONArray("episodes").length()==1
        fun poster(overall: Boolean,ratio: String) {
            val scope=if(overall) "STORY" else "EP%02d".format(number)
            val id="${scope}_COVER_${ratio.replace(':','x')}"
            val label=if(overall) (if(single) "ปกเรื่อง" else "ปกรวมทั้งเรื่อง") else "ปก EP.$number"
            val selected=if(overall) (0 until story.getJSONArray("episodes").length()).flatMap { people(story.getJSONArray("episodes").getJSONObject(it)) }.distinctBy { it.getString("name") }.take(3) else people(ep).take(3)
            val text=listOf(ChannelCatalog.currentChannel(story.getString("channel")),story.getString("title"),if(overall) story.getString("tagline") else "ตอนที่ $number ${ep.getString("title")}")
            val size=if(ratio=="9:16") "1080 x 1920" else "1920 x 1080"
            val note=(if(overall) story else ep).optString("production_note_$id")
            val command="""Generate ONE $ratio ${if(ratio=="9:16") "vertical" else "horizontal"} movie poster, $size. Cinematic waist-up composition; faces clear in the upper half, distinct expressions appropriate to ${story.optString("category")}. Recompose independently for this ratio; no stretching or collage.
$identity
${cast(selected,true)}
Bright neutral-white editorial light; realistic skin and fabric, readable expressions. Leave the text area clear.
EXACT VISIBLE THAI TEXT (three entries, no other lettering):
${text.joinToString("\n")}
TEXT SAFE AREA: x=15–85%, y=52–75%, centered at x=50%; at least 25% bottom clearance. Title largest, channel smaller, third entry secondary. Wrap long text without dropping words; never overlap faces or crop letters. Bold cinematic Thai lettering with restrained 3D depth, high contrast and a dark gradient behind text ONLY. Keep faces bright. This is a composition guide, not a platform overlay guarantee; inspect the actual result. Missing/wrong text stays PENDING for repair.
${if(note.isBlank()) "" else "Local design note (cannot override exact text, safe area, identity or wardrobe): $note"}""".trim()
            add(Task(id,"$label | ${if(ratio=="9:16") "แนวตั้ง" else "แนวนอน"} $ratio",ratio,command,selected))
        }
        if(number==firstEpisode(story)) for(ratio in listOf("9:16","16:9")) poster(true,ratio)
        if(!single) for(ratio in listOf("9:16","16:9")) poster(false,ratio)
        for(i in 0 until rows.length()) {
            val scene=rows.getJSONObject(i); val n=scene.getInt("scene"); val id="EP%02d_SC%03d".format(number,n)
            val command="""Generate ONE 9:16 vertical photorealistic Thai-drama scene image, 1080 x 1920. No text, captions or watermark. Depict the START STATE as a clear first frame for an 8-second video; do not show a sequence or jump to the completed action.
$identity
${cast(FlowReferencePrompt.people(scene))}
Style: ${story.optString("category")} — follow the written action, no new plot.
Story day: ${scene.getString("story_day")} — exact wardrobe remains locked across locations and EPs.
Location: ${scene.getString("location")}
Start frame: ${scene.getString("start_state")}
Action planned next (not a montage): ${scene.optString("flow_action").ifBlank { scene.getString("action") }}
Framing: ${scene.optString("framing").ifBlank { "Show all listed people, relevant hands and props with natural perspective." }}
Lighting: ${light(scene)}
Acting: ${acting(scene).ifBlank { "Individual restrained expressions consistent with the stated action and start state." }}
Mouths closed in this still. Preserve all props and screen positions for the matching video.
${scene.optString("production_image_note").takeIf { it.isNotBlank() }?.let { "Local visual correction (keep identity, outfit, cast and start state): $it" }.orEmpty()}""".trim()
            add(Task(id,"EP.$number | ซีน %03d | แนวตั้ง 9:16".format(n),"9:16",command,FlowReferencePrompt.people(scene)))
        }
    }
    fun videoTasks(story: JSONObject,number: Int): List<Task> {
        val rows=episode(story,number).getJSONArray("scenes")
        return (0 until rows.length()).map { i ->
            val scene=rows.getJSONObject(i); val n=scene.getInt("scene"); val id="EP%02d_SC%03d".format(number,n)
            val spec=ProductionCommandCheck.spec(scene)
            val line=when { spec.dialogue.isNotBlank() && spec.speaker in spec.offscreen -> "OFF-SCREEN CHARACTER SPEAKER ONLY: ${spec.speaker}. Exact Thai dialogue once: “${spec.dialogue}”. Keep speaker outside frame. All visible mouths closed; no lip-sync, narrator or other voices."
                spec.dialogue.isNotBlank() -> "ONLY SPEAKER: ${spec.speaker}. Exact Thai dialogue once: “${spec.dialogue}”. Only this person's lips move; everyone else is silent. No narrator."
                spec.narration.isNotBlank() -> "OFF-SCREEN MALE NARRATOR ONLY: “${spec.narration}”. All on-screen mouths closed; nobody lip-syncs the narration."
                else -> "No dialogue or narration; all on-screen mouths closed." }
            val command="""ภาพเริ่มต้นที่ต้องแนบ: $id.png — ใช้ภาพซีนที่ตรวจ MASTER ชุดและตำแหน่งแล้ว ไม่ใช้ MASTER แทนเฟรมฉาก
Flow: ONE continuous 8-second 9:16 vertical video, 1080 x 1920, locked-off camera. Preserve the supplied frame's identities, body proportions, exact outfits, positions, room and props. No cuts, captions or extra characters.
${cast(FlowReferencePrompt.people(scene))}
STYLE: ${story.optString("category")} — follow the written action, no new plot.
LOCATION: ${spec.location}
START: ${spec.startState}
ONE ACTION: ${spec.action}
END: ${spec.endState}. Hold briefly for continuity.
ACTING: ${acting(scene).ifBlank { "Natural distinct reactions, receive the cue then perform the single action and settle; no extra event." }}
$line Finish the assigned line naturally within 8 seconds; no extra voices, ad-libs or audible laughs.
${SoundDesign.prompt(spec.sound)}
LIGHTING: ${light(scene)}
${scene.optString("production_video_note").takeIf { it.isNotBlank() }?.let { "Local direction correction (keep cast, wardrobe, exact speech, action and audio rules): $it" }.orEmpty()}""".trim()
            Task(id,"EP.$number | ซีน %03d | วิดีโอ 9:16 | 8 วินาที".format(n),"9:16",command)
        }
    }
    fun block(task: Task,index: Int,images: Boolean)="[${if(images) "รูปที่ %02d".format(index+1) else "วิดีโอ"} | ${task.title}]\nรหัส: ${task.id}\n${if(images) "ชื่อไฟล์ภาพ" else "ชื่อไฟล์วิดีโอ"}: ${task.id}.${if(images) "png" else "mp4"}\n${task.command}"
    fun document(story: JSONObject,number: Int,images: Boolean): String {
        val tasks=if(images) imageTasks(story,number) else videoTasks(story,number)
        val coverCount=tasks.count { it.id.contains("_COVER_") }
        return buildString {
            append("${if(images) "คำสั่งสร้างรูป Gemini" else "คำสั่งวิดีโอ Flow"} | EP.$number | ${story.getString("title")}\n")
            append("${if(images) "${tasks.size} ภาพ: ปก $coverCount + ซีน ${tasks.size-coverCount}" else "${tasks.size} ซีน × 8 วินาที = ${tasks.size*8} วินาที (ไม่รวมปกเปิด)"}\n")
            append("สารบัญ — หัวข้อและรหัสเป็นป้ายกำกับ ห้ามวาดลงซีน:\n")
            tasks.forEachIndexed { i,t -> append("${i+1}. ${t.id} | ${t.title}\n") }
            if(images) append("\nแนบ MASTER ตามชื่อตัวละคร ไม่อิงลำดับอัปโหลด ไม่ต้องเปลี่ยนชื่อรูปจริง เริ่มภาพแรกเท่านั้น; next = ภาพถัดไปหลังภาพปัจจุบันสำเร็จและตรวจแล้ว\nแก้ภาพ <รหัส> / retry = แก้งานนั้น ไม่เลื่อนคิว ไม่เปลี่ยนอัตลักษณ์หรือชุดประจำวัน; ไปที่ <รหัส> = ทำต่อจากรายการที่มีในสารบัญ\nทุกครั้งแจ้ง ล่าสุด: <รหัส> | สถานะ DONE/PENDING | ถัดไป: <รหัส>; หากไม่สำเร็จ ข้อความปกผิด หรือขาดอ้างอิงให้คง PENDING ห้ามข้าม\nDONE หมายถึงผู้ใช้ตรวจผลจริงแล้ว ไม่ใช่คะแนนรับรองอัตลักษณ์จากคำสั่ง หากบริบทหายให้ขอไฟล์และ MASTER พร้อมระบุรหัสทำต่อใหม่\nปกมีข้อความตามรายการเท่านั้น ซีนไม่มีข้อความ ทุกคำสั่งใช้ขนาดของตนเอง; ครบรายการแล้วหยุด\n")
            else append("\nคัดลอกทีละบล็อกพร้อมภาพ PNG รหัสเดียวกันเป็นเฟรมเริ่มต้น ไฟล์นี้ไม่ใช่การรัน Flow ทั้ง EP อัตโนมัติ; แต่ละบล็อกรวมภาพ การแสดง บทพูดและเสียงแล้ว\n")
            tasks.forEachIndexed { i,t -> append("\n\n${block(t,i,images)}") }
        }
    }
    fun images(story: JSONObject,number: Int=firstEpisode(story))=document(story,number,true)
    fun videos(story: JSONObject,number: Int=firstEpisode(story))=document(story,number,false)
    fun write(output: OutputStream,story: JSONObject) {
        val problems=ProductionCommandCheck.issues(story)
        require(problems.isEmpty()) { problems.take(20).joinToString("\n") }
        val eps=story.getJSONArray("episodes")
        // Build and validate every document before opening the archive.
        val docs=(0 until eps.length()).flatMap { i -> val n=eps.getJSONObject(i).getInt("ep"); listOf(imageName(n) to images(story,n),flowName(n) to videos(story,n)) }
        ZipOutputStream(output).use { zip -> docs.forEach { (name,text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() } }
    }
}
