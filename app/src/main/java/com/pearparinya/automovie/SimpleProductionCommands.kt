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
    private const val identity="REFERENCE ROLE MAPPING: filenames or upload order alone may not reliably identify an image. If unclear, ask. Preserve each person's face and exact hairstyle from their own MASTER; use the planned outfit."
    /** The MASTER remains authoritative: stored descriptions are only hints, not verified facts. */
    internal fun referenceBindings(people: List<JSONObject>): String {
        if (people.isEmpty()) return "REFERENCE BINDING: No visible characters."
        val rows = people.mapIndexed { i, person ->
            val name = person.getString("name")
            val master = referenceId(person.optString("master").trim())
            "Reference ${i + 1}: $name | MASTER ID: ${master.ifBlank { "not specified" }} | Identity only."
        }.joinToString("\n")
        return """REFERENCE BINDING — match each named person to their own original attached MASTER:
$rows
Use the clear front view for face and hair. If the image-to-name mapping is unclear, ask. A front-view crop can supplement the original MASTER. Scene Outfit overrides MASTER clothing."""
    }
    private const val wardrobeContinuity="WARDROBE CONTINUITY: Use each approved Scene Outfit, not MASTER clothing. Same day: identical outfit, including neckline, collar, sleeves, closures, shoes and accessories. Shoes: none means barefoot; confirm intent. Ask if details or the spirit's outfit source are unclear. A new story day can use the outfit planned for that day. The three views in each MASTER represent ONE person; their own MASTER controls face, hair and body. Never mix characters. Review results."
    const val faceOnlyMarker = "FACE-ONLY MASTER REFERENCE"
    /** Apply only to explicit user-selected face references; keep the original scene and wardrobe. */
    fun faceOnly(command: String): String {
        val instruction="""$faceOnlyMarker — For each human character, use only that person's own attached MASTER as the reference for facial identity and natural skin tone. The three views show one person; use the large clear front view for facial details. Preserve recognizable face shape, eyes, eyebrows, nose, lips and jaw. Do not invent a different face or blend identities. The scene controls clothes, hairstyle, body, pose and setting; do not copy MASTER clothing. For a nonhuman, preserve the MASTER's species, distinctive face, markings and anatomy. If the reference is missing or ambiguous, ask. Compare the generated result with the MASTER; similarity is a target, not a guarantee."""
        return command.replace(identity,instruction)
            .replace(BodyReferencePolicy.imageIdentityLock,"")
            .replace("Use the clear front view for face and hair. If the image-to-name mapping is unclear, ask. A front-view crop can supplement the original MASTER. Scene Outfit overrides MASTER clothing.","Use the clear front view for facial identity. If the image-to-name mapping is unclear, ask. A front-view crop from the same MASTER may supplement it. Scene Outfit overrides MASTER clothing.")
            .replace("their own MASTER controls face, hair and body", "their own MASTER controls facial identity for humans; nonhuman species and anatomy remain consistent")
    }
    /** Explicit per-character opt-in takes precedence over generic scene wardrobe rules. */
    fun masterAppearanceLock(people: List<Pair<String,String>>): String {
        if (people.isEmpty()) return ""
        val bindings=people.joinToString("\n") { (name,id) -> "Character: $name | attached MASTER ID: $id" }
        return """

HIGHEST PRIORITY — ORIGINAL MASTER APPEARANCE FOR THE CHARACTERS LISTED BELOW:
$bindings
For each listed character, use only their own attached MASTER for their identity, visible body, hairstyle, outfit, armor, headwear and accessories. Reproduce the distinctive colors, silhouette and details shown in that MASTER. If a helmet or mask covers the face, keep it closed: do not remove it, invent a visible face, or substitute an unhelmeted person. A covered face does not need a readable expression. Each three-view image shows ONE character, not multiple people. Never exchange identities or clothing between characters. This per-character instruction overrides generic face-only, scene outfit, wardrobe and expression instructions for the listed characters only. Follow the scene for action and pose without changing those appearances. If the attachment or identity mapping is unclear, ask before generating. Visually compare each character to their own reference; similarity cannot be guaranteed."""
    }
    /** Resolve each character to one wardrobe source without changing the stored story. */
    fun withWardrobeSources(command: String, people: List<JSONObject>, masterIds: Set<String>): String {
        if(masterIds.isEmpty()) return command
        var output=command.replace("Scene Outfit overrides MASTER clothing.","Use the per-character wardrobe source specified below.")
            .replace("The scene controls clothes, hairstyle, body, pose and setting; do not copy MASTER clothing.","The selected wardrobe source controls clothing; the scene controls pose and setting.")
            .replace("Use each approved Scene Outfit, not MASTER clothing.","Use the wardrobe source specified for each character below.")
            .replace(WardrobeContext.image,"CONTEXTUAL WARDROBE: Follow each character's selected wardrobe source. Flag unclear or unsuitable clothes and footwear for review; never silently change them.")
        val locked=mutableListOf<Pair<String,String>>()
        for(person in people) {
            val name=person.getString("name")
            val id=referenceId(person.getString("master"))
            if(masterIds.none { it.equals(id,true) }) continue
            val pattern=Regex("(?m)^(${Regex.escape(name)} \\([^\\n]*\\) \\| MASTER ID: ${Regex.escape(id)} \\| position: [^\\n]*\\n)Outfit: [^\\n]*",RegexOption.IGNORE_CASE)
            require(pattern.containsMatchIn(output)) { "ตรวจแหล่งชุดของ $name ไม่สำเร็จ กรุณากลับไปตรวจ MASTER" }
            output=pattern.replace(output) { it.groupValues[1]+"Outfit: Use this character's own attached MASTER outfit; do not use the scene outfit for this character." }
            val reference=Regex("(?m)^(Reference [0-9]+: ${Regex.escape(name)} \\| MASTER ID: ${Regex.escape(id)} \\| )Identity only[.]$",RegexOption.IGNORE_CASE)
            output=reference.replace(output) { it.groupValues[1]+"Identity and clothing reference." }
            locked.add(name to id)
        }
        require(locked.size==masterIds.size) { "แหล่งชุดไม่ตรงกับตัวละครที่เลือก กรุณาตรวจใหม่" }
        return output+masterAppearanceLock(locked)
    }
    private fun cast(people: List<JSONObject>,poster: Boolean=false)=people.mapIndexed { i,p ->
        val position=if(!poster) p.optString("screen_position") else when(people.size) { 1 -> "center"; 2 -> if(i==0) "left" else "right"; 3 -> listOf("left","center","right")[i]; else -> "distinct ensemble position ${i+1}" }
        val alias=when(FlowCast.id(p)) { "gawin" -> "Kawin / Gawin"; "rinlada" -> "Rinrada / Rinlada"; "mind" -> "May / Mind"; else -> p.getString("name") }
        "${p.getString("name")} ($alias) | MASTER ID: ${referenceId(p.getString("master"))} | position: $position\nOutfit: ${p.getString("outfit")}"
    }.joinToString("\n")
    private fun acting(scene: JSONObject)=FlowReferencePrompt.people(scene).mapNotNull { person ->
        person.optJSONObject("performance")?.let { p ->
            person.getString("name")+": "+listOf("emotion","objective","subtext","gaze","body_language","voice","emotional_arc").mapNotNull { k -> p.optString(k).takeIf { it.isNotBlank() }?.let { "$k=$it" } }.joinToString("; ")
        }
    }.joinToString("\n")
    private const val light = "Bright neutral-white 5500K, soft shadows; no dark or yellow cast. Keep night scenes bright with neutral artificial light."
    fun coverCandidates(story: JSONObject,number: Int,overall: Boolean): List<JSONObject> {
        val eps=story.getJSONArray("episodes")
        val selected=if(overall) (0 until eps.length()).map { eps.getJSONObject(it) } else listOf(episode(story,number))
        return selected.flatMap { people(it) }.distinctBy { referenceId(it.getString("master")).lowercase() }
    }
    fun imageTasks(story: JSONObject,number: Int,coverSelection: Map<String,Set<String>> = emptyMap()): List<Task> = buildList {
        LandscapeProduction.requirePlan(story)
        val ep=episode(story,number); val rows=ep.getJSONArray("scenes"); val single=story.getJSONArray("episodes").length()==1
        fun poster(overall: Boolean,ratio: String) {
            val scope=if(overall) "STORY" else "EP%02d".format(number)
            val id="${scope}_COVER_${ratio.replace(':','x')}"
            val label=if(overall) (if(single) "ปกเรื่อง" else "ปกรวมทั้งเรื่อง") else "ปก EP.$number"
            val candidates=coverCandidates(story,number,overall)
            val chosen=coverSelection[scope]
            val selected=if(chosen==null) candidates.take(9) else candidates.filter { referenceId(it.getString("master")) in chosen }
            if(chosen!=null) require(selected.isNotEmpty() && selected.size<=9 && selected.size==chosen.size) { "ตัวละครปกเปลี่ยนหรือเกิน 9 ภาพ กรุณาเลือกรายการปกใหม่" }
            val posterContext=(if(overall) (0 until story.getJSONArray("episodes").length()).map { story.getJSONArray("episodes").getJSONObject(it) } else listOf(ep))
                .map { listOf(it.optString("synopsis"),it.optString("cover_incident")).filter(String::isNotBlank).joinToString(" • ") }.filter(String::isNotBlank).joinToString(" | ").take(1500)
            val text=listOf(ChannelCatalog.currentChannel(story.getString("channel")),story.getString("title"),if(overall) story.getString("tagline") else "ตอนที่ $number ${ep.getString("title")}")
            val size=if(ratio=="9:16") "1080 x 1920" else "1920 x 1080"
            val note=(if(overall) story else ep).optString("production_note_$id")
            val command="""Generate ONE $ratio ${if(ratio=="9:16") "vertical" else "horizontal"} movie poster, $size. Prestige theatrical key art with genre-specific world, cinematic depth, dramatic motivated light, premium production design and a clear focal character. Preserve identities and the planned outfit; expressions and visual effects must fit ${DisplayText.category(story.optString("category"))} and the actual story. Recompose independently for this ratio; no stretching or collage.
$identity
${referenceBindings(selected)}
CHARACTERS AND OUTFITS:
${cast(selected,true)}
$wardrobeContinuity
${WardrobeContext.image}
Show exactly ${selected.size} ${if(selected.size==1 && selected.first().optString("character_type").let { it.isBlank() || it=="human" }) "person" else "named characters"} from the listed references, each with their own MASTER; do not replace, merge, duplicate or invent named characters. Preserve nonhuman species, anatomy and markings. Include anonymous background crowds only when the story context explicitly describes them; do not invent unrelated groups or events.
STORY CONTEXT FOR COMPOSITION ONLY — do not print this as visible text: $posterContext
${if(candidates.size>selected.size) "Only the ${selected.size} selected reference characters are included; review the cast before use." else ""}
Bright neutral-white editorial light; realistic materials. Keep any reference helmet or mask closed when instructed. Leave the text area clear.
EXACT VISIBLE THAI TEXT (three entries, no other lettering):
${text.joinToString("\n")}
${BodyReferencePolicy.imageIdentityLock}
${ImagePromptPolicy.skinTone}
TEXT LAYOUT: ${CoverTypography.layout} Treat requested pixel dimensions as a target; check downloaded pixel size. Inspect each face, garment and all Thai text; missing or incorrect elements remain pending for correction.
${if(note.isBlank()) "" else "Local design note (cannot override lighting, exact text, safe area, identity or wardrobe): $note"}""".trim()
            add(Task(id,"$label | ${if(ratio=="9:16") "แนวตั้ง" else "แนวนอน"} $ratio",ratio,command,selected))
        }
        if(number==firstEpisode(story)) for(ratio in listOf("9:16","16:9")) poster(true,ratio)
        if(!single) for(ratio in listOf("9:16","16:9")) poster(false,ratio)
        for(i in 0 until rows.length()) {
            val scene=rows.getJSONObject(i); val n=scene.getInt("scene"); val id="EP%02d_SC%03d".format(number,n)
            val visiblePeople=FlowReferencePrompt.people(scene)
            val command="""Generate ONE 9:16 vertical film-directed photorealistic Thai-drama scene image, 1080 x 1920. No text, captions or watermark. Depict the START STATE as a clear first frame for an 8-second video; do not show a sequence or jump to the completed action.
$identity
${referenceBindings(visiblePeople)}
CHARACTERS AND OUTFITS:
${cast(visiblePeople)}
$wardrobeContinuity
${WardrobeContext.image}
${FlowReferencePrompt.visibleCastLock(visiblePeople)}
Style: ${DisplayText.category(story.optString("category"))} — follow the written action, no new plot.
Story day: ${scene.getString("story_day")} — exact wardrobe remains locked across locations and EPs.
Location: ${scene.getString("location")}
Start frame: ${scene.getString("start_state")}
${scene.optString("props").trim().takeIf { it.isNotBlank() }?.let { "Objects and holders specified in the story: $it. Include only those intended to be visible at the start; do not invent missing details." }.orEmpty()}
If start-frame names conflict with visible cast, stop and ask; never invent or replace people.
Action planned next (not a montage): ${scene.optString("flow_action").ifBlank { scene.getString("action") }}
Framing: ${scene.optString("framing").ifBlank { "Show all listed people, relevant hands and props with natural perspective." }}
Lighting: $light
Acting: ${acting(scene).ifBlank { "Individual restrained expressions consistent with the stated action and start state." }}
${BodyReferencePolicy.imageIdentityLock}
${ImagePromptPolicy.skinTone}
${ReadingPropPolicy.forScene(scene.optString("action"), scene.optString("flow_action"), scene.optString("start_state"), scene.optString("end_state"))}
Mouths closed in this still. Preserve all props and screen positions for the matching video.
${scene.optString("production_image_note").takeIf { it.isNotBlank() }?.let { "Local visual correction (keep bright lighting, identity, outfit, cast and start state): $it" }.orEmpty()}""".trim()
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
            val camera=when (scene.optString("camera_movement")) {
                "track" -> "Gentle tracking movement following the action."
                "locked" -> "Locked camera."
                "push_in" -> "Slow, gentle push-in."
                else -> "Slow, gentle pan."
            }
            val command=runCatching { ShortFlowPrompt.build(spec,camera) }.getOrElse { "Flow prompt needs review: non-English scene direction." }
            Task(id,"EP.$number | ซีน %03d | วิดีโอ 9:16 | 8 วินาที".format(n),"9:16",command)
        }
    }
    fun groupScenes(tasks: List<Task>): List<List<Task>> = tasks.filter { it.id.contains("_SC") }.chunked(5)
    fun flowGroupPath(ep: Int, index: Int) = "Flow_EP%02d/EP%02d_FLOW_%02d.txt".format(ep,ep,index+1)
    fun flowGroupDocument(tasks: List<Task>): String = "คำสั่ง Flow ชุดนี้: แนบภาพรหัสเดียวกับซีนเป็น Start frame แล้วคัดลอกคำสั่งทีละซีน\n" + tasks.mapIndexed { i,task -> block(task,i,false) }.joinToString("\n\n")
    fun block(task: Task,index: Int,images: Boolean)="[${if(images) "รูปที่ %02d".format(index+1) else "วิดีโอ"} | ${task.title}]\nรหัส: ${task.id}\n${if(images) "ชื่อไฟล์ภาพ" else "ชื่อไฟล์วิดีโอ"}: ${task.id}.${if(images) "png" else "mp4"}\n${if(images) "" else "ภาพซีนที่ต้องแนบเป็นเฟรมแรก: ${task.id} (ใช้ไฟล์ภาพซีนรหัสนี้ เช่น ${task.id}.png)\n"}${task.command}"
    fun document(story: JSONObject,number: Int,images: Boolean,coverSelection: Map<String,Set<String>> = emptyMap(), wardrobeMasterIds: Set<String> = emptySet()): String {
        val tasks=if(images) imageTasks(story,number,coverSelection).map { task ->
            val selected=task.references.map { referenceId(it.optString("master")) }.filter { it in wardrobeMasterIds }.toSet()
            if(selected.isEmpty()) task else task.copy(command=withWardrobeSources(task.command,task.references,selected))
        } else videoTasks(story,number)
        val coverCount=tasks.count { it.id.contains("_COVER_") }
        return buildString {
            append("${if(images) "คำสั่งสร้างรูป ChatGPT / Gemini" else "คำสั่งวิดีโอ Flow"} | EP.$number | ${story.getString("title")}\n")
            append("${if(images) "${tasks.size} ภาพ: ปก $coverCount + ซีน ${tasks.size-coverCount}" else "${tasks.size} ซีน × 8 วินาที = ${tasks.size*8} วินาที (ไม่รวมปกเปิด)"}\n")
            append("สารบัญ — หัวข้อและรหัสเป็นป้ายกำกับ ห้ามวาดลงซีน:\n")
            tasks.forEachIndexed { i,t -> append("${i+1}. ${t.id} | ${t.title}\n") }
            if(images) append("\nแนบ MASTER ต้นฉบับของตัวละครแต่ละคน ตรวจชื่อและลักษณะกับภาพจริงก่อนสร้าง หากมีภาพครอปหน้าตรงจาก MASTER เดียวกันให้แนบเพิ่มได้ ไม่อิงลำดับอัปโหลดอย่างเดียว ไม่ต้องเปลี่ยนชื่อรูปจริง เริ่มภาพแรกเท่านั้น; next = ภาพถัดไปหลังภาพปัจจุบันสำเร็จและตรวจแล้ว\nแก้ภาพ <รหัส> / retry = แก้งานนั้น ไม่เลื่อนคิว ไม่เปลี่ยนอัตลักษณ์หรือชุดประจำวัน; ไปที่ <รหัส> = ทำต่อจากรายการที่มีในสารบัญ\nทุกครั้งแจ้ง ล่าสุด: <รหัส> | สถานะ DONE/PENDING | ถัดไป: <รหัส>; หากไม่สำเร็จ ข้อความปกผิด หรือขาดอ้างอิงให้คง PENDING ห้ามข้าม\nDONE หมายถึงผู้ใช้ตรวจผลจริงแล้ว ไม่ใช่คะแนนรับรองอัตลักษณ์จากคำสั่ง หากบริบทหายให้ขอไฟล์และ MASTER พร้อมระบุรหัสทำต่อใหม่\nปกมีข้อความตามรายการเท่านั้น ซีนไม่มีข้อความ ทุกคำสั่งใช้ขนาดของตนเอง; ครบรายการแล้วหยุด\n")
            else append("\nใน Flow เลือก Video → Frames และแนบภาพซีนรหัสเดียวกันใน Add start frame จากนั้นคัดลอกเฉพาะคำสั่งของซีน ตั้ง 9:16 และ 8 วินาที แล้วกดสร้าง ภาพซีนต้องเตรียมหรืออัปโหลดเอง ไฟล์นี้มีคำสั่งข้อความ ไม่ได้แนบภาพหรือสร้างวิดีโออัตโนมัติ\n")
            tasks.forEachIndexed { i,t -> append("\n\n${block(t,i,images)}") }
        }
    }
    fun images(story: JSONObject,number: Int=firstEpisode(story),coverSelection: Map<String,Set<String>> = emptyMap(),wardrobeMasterIds: Set<String> = emptySet())=document(story,number,true,coverSelection,wardrobeMasterIds)
    fun videos(story: JSONObject,number: Int=firstEpisode(story))=document(story,number,false)
    fun write(output: OutputStream,story: JSONObject,coverSelection: Map<String,Set<String>> = emptyMap(),wardrobeMasterIds: Set<String> = emptySet()) {
        val problems=ProductionCommandCheck.issues(story)
        require(problems.isEmpty()) { problems.take(20).joinToString("\n") }
        val eps=story.getJSONArray("episodes")
        // Build and validate every document before opening the archive.
        val docs=(0 until eps.length()).flatMap { i ->
            val n=eps.getJSONObject(i).getInt("ep")
            val groups=groupScenes(videoTasks(story,n))
            listOf(imageName(n) to images(story,n,coverSelection,wardrobeMasterIds),flowName(n) to videos(story,n)) +
                groups.mapIndexed { index,group -> flowGroupPath(n,index) to flowGroupDocument(group) }
        }
        ZipOutputStream(output).use { zip -> docs.forEach { (name,text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() } }
    }
}
