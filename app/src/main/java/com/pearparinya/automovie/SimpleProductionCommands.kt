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
    private const val identity="REFERENCE ROLE MAPPING: Match each name to its attached MASTER; filenames or upload order alone may not reliably identify an image. If unclear, ask. Multi-view panels depict one person; use the clear front view for identity. Preserve each person's face, hair, adult age and natural proportions without blending or generic beautification. MASTER is for identity only; use the planned outfits, not reference clothing. Fictional adults 25+; natural anatomy and opaque clothing."
    private const val wardrobeContinuity="WARDROBE CONTINUITY: Keep each listed person's planned outfit identical across covers and scenes on the same story day: garment type, fabric, color, neckline, collar, sleeves, closures, hem and accessories. Do not invent missing details. If an approved outfit image is explicitly attached, use it only for that person's clothes; their own MASTER controls face, hair and body. Compare actual results before approval."
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
CHARACTERS AND OUTFITS:
${cast(selected,true)}
$wardrobeContinuity
${FlowReferencePrompt.visibleCastLock(selected)}
Bright neutral-white editorial light; realistic skin and fabric, readable expressions. Leave the text area clear.
EXACT VISIBLE THAI TEXT (three entries, no other lettering):
${text.joinToString("\n")}
${BodyReferencePolicy.imageIdentityLock}
${ImagePromptPolicy.skinTone}
TEXT LAYOUT: ${CoverTypography.layout} Treat requested pixel dimensions as a target; check downloaded pixel size. Inspect each face, garment and all Thai text; missing or incorrect elements remain pending for correction.
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
CHARACTERS AND OUTFITS:
${cast(visiblePeople)}
$wardrobeContinuity
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
    fun coverPair(vertical: Task, horizontal: Task): String {
        require(vertical.id.endsWith("_COVER_9x16") && horizontal.id.endsWith("_COVER_16x9"))
        require(vertical.id.substringBeforeLast("_")==horizontal.id.substringBeforeLast("_"))
        require(vertical.references.map { referenceId(it.getString("master")).lowercase() } == horizontal.references.map { referenceId(it.getString("master")).lowercase() })
        return """
Create TWO separate cover images in sequence, not a collage. First create and show the portrait cover below. Then use that portrait as the composition and wardrobe reference for a NEW landscape 16:9 image: expand the setting on both sides, preserve the same faces, hair, apparent age and outfits, and re-layout the text. Do not stretch the portrait or faces. The attached MASTER for each character remains the identity authority. If you can create only one image, report which is missing; do not claim both are complete. Review both actual results.

IMAGE 1 — PORTRAIT:
${vertical.command}

IMAGE 2 — LANDSCAPE:
${horizontal.command}

Return two separate images named ${vertical.id}.png and ${horizontal.id}.png. Requested dimensions are targets; verify downloaded files.
        """.trimIndent()
    }
    private fun xml(value: String): String = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    fun videoTasks(story: JSONObject,number: Int): List<Task> {
        val rows=episode(story,number).getJSONArray("scenes")
        return (0 until rows.length()).map { i ->
            val scene=rows.getJSONObject(i); val n=scene.getInt("scene"); val id="EP%02d_SC%03d".format(number,n)
            val spec=ProductionCommandCheck.spec(scene)
            val visible=FlowReferencePrompt.people(scene)
            val cast=visible.joinToString("; ") { p ->
                p.optString("name")+" ("+p.optString("screen_position").ifBlank { "as in starting frame" }+")"
            }
            val speech=when {
                spec.dialogue.isNotBlank() && spec.speaker in spec.offscreen -> "Off-screen ${spec.speaker} speaks once in Thai: ${spec.dialogue}. Visible mouths stay closed."
                spec.dialogue.isNotBlank() -> "${spec.speaker} speaks once in Thai: ${spec.dialogue}. Only the speaker moves their lips."
                spec.narration.isNotBlank() -> "Off-screen male narrator speaks once in Thai: ${spec.narration}. Visible mouths stay closed."
                else -> "No speech or narration; visible mouths stay closed."
            }
            val performance=visible.mapNotNull { p -> p.optJSONObject("performance")?.let { perf ->
                val cues=listOf("emotion","gaze","body_language").mapNotNull { k -> perf.optString(k).takeIf { it.isNotBlank() }?.let { "$k: $it" } }
                if(cues.isEmpty()) null else p.optString("name")+": "+cues.joinToString(", ")
            } }.joinToString("; ")
            val sound=spec.sound.ifBlank { "Quiet natural ambience." }.replace(Regex("\\b(?:indoor )?murmurs\\b", RegexOption.IGNORE_CASE),"quiet room tone")
            val moveOptions=listOf("push_in","pan","track","locked")
            val cameraMode=scene.optString("camera_movement").takeIf { it in moveOptions } ?: when {
                Regex("(?i)\\b(walks?|walking|runs?|running|moves?|moving|เดิน|วิ่ง)\\b").containsMatchIn(spec.action) -> "track"
                visible.size>1 && spec.dialogue.isNotBlank() -> "push_in"
                visible.size>1 -> "pan"
                else -> "push_in"
            }
            val cameraAction=when(cameraMode) {
                "pan" -> "Slow pan following action and reactions."
                "track" -> "Gentle tracking movement following the action."
                "locked" -> "Locked camera."
                else -> "Slow subtle push-in toward the emotional focus."
            }
            val motion=listOf("Start: ${spec.startState}",spec.action,"End: ${spec.endState}",performance.takeIf { it.isNotBlank() }?.let { "Performance: $it" },scene.optString("production_video_note").takeIf { it.isNotBlank() }?.let { "Direction: $it" }).filterNotNull().joinToString(". ")
            val command="""Use the attached image as the first frame. One continuous 8-second vertical shot.
Characters: $cast. Keep all ${visible.size} visible characters and their positions, faces, hair, bodies, clothes and setting consistent.
Action: $motion
Camera: $cameraAction Keep faces visible and pace movement to dialogue and reactions.
Voice: $speech
Sound: $sound Keep effects and music below speech. No additional voices or subtitles.""".trim()
            Task(id,"EP.$number | ซีน %03d | วิดีโอ 9:16 | 8 วินาที".format(n),"9:16",command)
        }
    }
    fun block(task: Task,index: Int,images: Boolean)="[${if(images) "รูปที่ %02d".format(index+1) else "วิดีโอ"} | ${task.title}]\nรหัส: ${task.id}\n${if(images) "ชื่อไฟล์ภาพ" else "ชื่อไฟล์วิดีโอ"}: ${task.id}.${if(images) "png" else "mp4"}\n${task.command}"
    fun document(story: JSONObject,number: Int,images: Boolean): String {
        val tasks=if(images) imageTasks(story,number) else videoTasks(story,number)
        val coverCount=tasks.count { it.id.contains("_COVER_") }
        return buildString {
            append("${if(images) "คำสั่งสร้างรูป ChatGPT / Gemini" else "คำสั่งวิดีโอ Flow"} | EP.$number | ${story.getString("title")}\n")
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
