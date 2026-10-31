package com.pearparinya.automovie

import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun referenceId(name: String): String = name.replace(Regex("[.](png|jpe?g|webp)$", RegexOption.IGNORE_CASE), "")

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
    private const val identity="Fictional adults 25+. Match each person to the attached MASTER by its complete reference ID, ignoring file extensions and upload order. Match face, hairstyle, skin and natural body proportions. No mixing, enlarging or slimming. MASTER supplies identity only; use the exact planned outfit and accessories below, never reference clothing. Opaque lined editorial fashion; natural anatomy and fabric."
    private fun cast(people: List<JSONObject>,poster: Boolean=false)=people.mapIndexed { i,p ->
        val position=if(!poster) p.optString("screen_position") else when(people.size) { 1 -> "center"; 2 -> if(i==0) "left" else "right"; else -> listOf("left","center","right")[i] }
        val alias=when(FlowCast.id(p)) { "gawin" -> "Kawin / Gawin"; "rinlada" -> "Rinrada / Rinlada"; "mind" -> "May / Mind"; else -> p.getString("name") }
        "${p.getString("name")} ($alias) | MASTER ID: ${referenceId(p.getString("master"))} | position: $position\nOutfit: ${p.getString("outfit")}"
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
${BodyReferencePolicy.imageIdentityLock}
${ImagePromptPolicy.skinTone}
TEXT LAYOUT: ${CoverTypography.layout} This is a composition guide, not a platform overlay guarantee; inspect the actual result. Missing/wrong text stays PENDING for repair.
${if(note.isBlank()) "" else "Local design note (cannot override exact text, safe area, identity or wardrobe): $note"}""".trim()
            add(Task(id,"$label | ${if(ratio=="9:16") "แนวตั้ง" else "แนวนอน"} $ratio",ratio,command,selected))
        }
        if(number==firstEpisode(story)) for(ratio in listOf("9:16","16:9")) poster(true,ratio)
        if(!single) for(ratio in listOf("9:16","16:9")) poster(false,ratio)
        for(i in 0 until rows.length()) {
            val scene=rows.getJSONObject(i); val n=scene.getInt("scene"); val id="EP%02d_SC%03d".format(number,n)
            val visiblePeople=FlowReferencePrompt.people(scene)
            val command="""Generate ONE 9:16 vertical photorealistic Thai-drama scene image, 1080 x 1920. No text, captions or watermark. Depict the START STATE as a clear first frame for an 8-second video; do not show a sequence or jump to the completed action.
$identity
${cast(visiblePeople)}
${FlowReferencePrompt.visibleCastLock(visiblePeople)}
Style: ${story.optString("category")} — follow the written action, no new plot.
Story day: ${scene.getString("story_day")} — exact wardrobe remains locked across locations and EPs.
Location: ${scene.getString("location")}
Start frame: ${scene.getString("start_state")}
Action planned next (not a montage): ${scene.optString("flow_action").ifBlank { scene.getString("action") }}
Framing: ${scene.optString("framing").ifBlank { "Show all listed people, relevant hands and props with natural perspective." }}
Lighting: ${light(scene)}
Acting: ${acting(scene).ifBlank { "Individual restrained expressions consistent with the stated action and start state." }}
${BodyReferencePolicy.imageIdentityLock}
${ImagePromptPolicy.skinTone}
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
            val castLabels=FlowReferencePrompt.people(scene).joinToString("; ") { p ->
                "${p.getString("name")} (${p.optString("screen_position").ifBlank { "as in image" }})"
            }
            val direction=FlowReferencePrompt.people(scene).mapNotNull { person ->
                person.optJSONObject("performance")?.let { p ->
                    val cues=listOf("emotion","gaze","body_language").mapNotNull { k -> p.optString(k).takeIf { it.isNotBlank() } }
                    if(cues.isEmpty()) null else person.getString("name")+": "+cues.joinToString(", ")
                }
            }.joinToString("; ")
            val sound=SoundDesign.prompt(spec.sound).replace(Regex("\\b(?:indoor )?murmurs\\b", RegexOption.IGNORE_CASE), "quiet room tone")
            val command="""ภาพเริ่มต้นที่ต้องแนบ: รหัส $id — ใช้ภาพซีนที่ตรวจแล้ว
Create one continuous 8-second vertical 9:16 video from the attached starting frame, with a locked camera. Preserve the people, faces, hair, body proportions, clothing, positions, lighting, location and props in the image. No cuts, captions or additional people.
Visible cast: $castLabels.
Starting situation: ${spec.startState}
Action: ${spec.action}
End: ${spec.endState}
${if(direction.isBlank()) "" else "Performance: $direction"}
$line
${if(spec.dialogue.isNotBlank() || spec.narration.isNotBlank()) "Deliver the assigned line naturally within 8 seconds." else ""} Any other person mentioned in the action remains silent unless assigned dialogue above.
$sound
${scene.optString("production_video_note").takeIf { it.isNotBlank() }?.let { "Direction: $it" }.orEmpty()}""".trim()
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
            else append("\nคัดลอกทีละบล็อกพร้อมภาพซีนรหัสเดียวกันเป็นเฟรมเริ่มต้น โดยไม่ขึ้นกับนามสกุลไฟล์ ไฟล์นี้ไม่ใช่การรัน Flow ทั้ง EP อัตโนมัติ; แต่ละบล็อกรวมภาพ การแสดง บทพูดและเสียงแล้ว\n")
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
