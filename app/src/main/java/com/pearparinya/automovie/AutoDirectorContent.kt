package com.pearparinya.automovie

import org.json.JSONObject

/** Portable production documents. XML organizes prompts; it is not a Flow API. */
internal object AutoDirectorContent {
    class AwaitingOutline : IllegalStateException("โครงเรื่องพร้อมแล้ว • ดูและยืนยันก่อนเตรียมคำสั่งภาพ Gemini")
    const val profile = "autodirector_v18"
    const val dailyWardrobe = "DAILY WARDROBE RULE — higher priority than generic activity/event styling: Assign one exact four-part outfit per character per canonical story_day (DAY01, DAY02, ...). The same day keeps identical Top, Bottom, Shoes and Accessories across locations, activities, times, event/group IDs, temporary absences and EP boundaries. Only a new story_day permits a new outfit; plan a different top AND trousers/skirt, or a different one-piece dress with Bottom: none. Shoes and accessories may repeat across days if explicitly listed. Repeat each day's complete garment/accessory descriptions verbatim. Never use reference photos as a wardrobe source: reference images define facial identity, hair and body only. Design new story clothes; do not copy any attached shirt, blouse, trousers, skirt, dress or outfit. If clothes are unclear, ask rather than inheriting reference clothing."
    const val femaleFashion = "ADULT WOMEN'S FASHION DIRECTION: Sexy, confident adult-drama editorial styling for fictional women age 25+. Plan figure-hugging clothes that visibly define the reference waist, hips and natural curves. Specify a deep V-neck, plunging neckline or low-cut sweetheart neckline with visible natural cleavage; use fitted blouses, fitted tops or bodycon dresses with opaque, fully lined bust coverage. Do not default to high-neck tops, buttoned-to-the-neck shirts, loose shapeless garments or closed jackets that cover the specified neckline. The breasts remain covered except for the neckline/cleavage: no visible nipples or areolae, sheer panels, underbust exposure or nudity. Keep the original reference anatomy and proportions; do not enlarge the bust or reshape the body to achieve the style. Select the exact garment, neckline and four-part outfit during script planning, then lock that same outfit and every accessory throughout the entire story_day. Do not borrow any garment from the attached identity images."
    const val generationRules = """AutoDirector v18 production profile. All characters are fictional adults (age 25+).
The Man is the husband, Woman 1 the wife, Woman 2 the affair partner. Use the existing internal canonical names and MASTER IDs in JSON only; the app maps them to these external aliases.
Write a new dramatic plot in the selected category with 24 complete scenes per episode. Vary conversation, argument, monologue, silent reaction and external narration across the story. Alternate speakers across scenes, never overlap speakers within one 8-second shot.
Every character outfit MUST explicitly list four labeled parts in English: Top: ...; Bottom: ...; Shoes: ...; Accessories: ... (write none if absent). Keep the same four parts for the entire story_day; change the top and trousers/skirt only when story_day changes. Match each reference's actual proportions without enlarging or reducing anatomy.
WARDROBE DETAIL AND LOCK: Top must specify exact garment type, color, fabric, neckline, sleeve length, fit and pattern/fastenings. Bottom must specify garment type, color, fabric, length and fit. Shoes must specify type, color and heel/sole. Accessories must enumerate each item, count, material/color, shape and exact placement (left/right ear, neck, wrist, finger); explicitly state absent items or none. Do not abbreviate as same outfit or same jewelry. The first wardrobe for a character on each story_day is the immutable authority: repeat the complete four-part string verbatim for that person in later shots, across absences and EP cuts. Keep wardrobe_event and continuity_group stable for continuous scenes, regardless of camera framing, emotion or who speaks. A wardrobe change requires a new canonical story_day. New wardrobe_event, continuity_group, location, activity, time, framing or mood within the same day cannot change clothing. Never copy jewelry from MASTER when it differs from the exact scene Accessories list.
Preserve exact reference facial geometry, hairstyle, hair length and hair color; never mix identities or bleach natural skin. Image framing and positions must be explicit, left/center/right for group shots. For narrated or silent scenes all mouths closed, lips sealed. For dialogue scenes only the named speaker may open their mouth.
Images are generated externally in Gemini; video is generated externally in Flow from a reviewed scene image. Bright cool-neutral white, 5500K daylight-balanced lighting, no yellow cast or dark shadows, preserve detail and highlights. Night scenes stay at night with bright neutral artificial light. No text on scene images. Do not claim a measured or guaranteed 100% identity match."""
    private val labels = mapOf("gawin" to "The Man", "rinlada" to "Woman 1", "mind" to "Woman 2")
    fun alias(person: JSONObject): String = labels[FlowCast.id(person)] ?: "Supporting " +
        (FlowCast.id(person) ?: error("Missing reference identity")).removePrefix("support_").uppercase()

    private fun normalize(text: String, story: JSONObject): String {
        var result = FlowReferencePrompt.normalize(text)
        FlowCast.definitions(story).sortedByDescending { it.getString("name").length }.forEach { p ->
            val id = FlowCast.id(p)!!; val label = alias(p)
            result = result.replace(FlowReferencePrompt.alias(id), label, true)
                .replace(p.getString("name"), label)
        }
        // Historic generated prose may contain the English lead names as well.
        mapOf("Gawin" to "The Man", "Kawin" to "The Man", "Rinrada" to "Woman 1", "Rinlada" to "Woman 1", "May" to "Woman 2", "Mind" to "Woman 2").forEach { (name,label) ->
            result = result.replace(Regex("\\b${Regex.escape(name)}\\b"), label)
        }
        return result
    }
    private fun speech(raw: String) = raw.trim().takeUnless { it.lowercase() in listOf("none", "ไม่มี", "n/a") }.orEmpty()
    fun validateWardrobes(batch: JSONObject) {
        val scenes=batch.getJSONArray("scenes")
        for(i in 0 until scenes.length()) FlowReferencePrompt.people(scenes.getJSONObject(i)).forEach { person ->
            val outfit=person.getString("outfit")
            AutoDirectorWardrobe.parts(outfit)
        }
    }
    private fun speaker(scene: JSONObject): String = FlowReferencePrompt.people(scene).firstOrNull {
        it.optString("name") == scene.optString("speaker") ||
            StoryIdentity.keyFor(scene.optString("speaker"))?.let { key -> FlowCast.id(it) == key } == true
    }?.let { alias(it) } ?: ""

    private fun binding(people: List<JSONObject>, story: JSONObject): String = people.joinToString("\n") { p ->
        val appearance = p.optString("visual_description").ifBlank { FlowProductionPack.dna[FlowCast.id(p)].orEmpty() }
        "${alias(p)}: attached original reference only; ${normalize(appearance,story)}\n" +
            "EXACT WARDROBE — ${normalize(p.optString("outfit"),story)}. These four parts override any stale clothing/jewelry in appearance or reference images. Preserve every listed garment and accessory exactly; no added/removed jewelry, no color, material, shape, count, side or placement changes.\n" +
            "Position: ${p.optString("screen_position").ifBlank { "compose around the reserved text area" }}."
    }
    private const val identity = dailyWardrobe + "\nRequest exact reference facial identity, hairstyle, hair length and hair color. Match each adult woman's reference bust volume and natural silhouette 1:1 relative to shoulders, waist and hips; no enlargement, reduction, blending or anatomical redesign. Adult-drama fashion target: sexy fitted clothes, clearly defined waist/hips and natural curves, deep or low-cut necklines with visible natural cleavage as specified by the script. Preserve the exact locked scene outfit and neckline; do not add a camisole, scarf, high collar, closed jacket or extra fabric that hides the planned neckline. Do not change an already locked outfit to apply a new style while rendering, retrying or editing a scene. Opaque lined clothes cover nipples and areolae; no nudity or transparent bust fabric. Preserve each reference's natural skin tone and visible skin texture. Compare actual outputs with the original references; identity lock is a prompt target, not a verified percentage."
    private fun ending(ratio: String) = "Generate a ${if(ratio=="9:16") "9:16 vertical" else "16:9 horizontal"} image.\nMasterpiece, 8k, 5500K daylight, pure bright white studio lighting, strict cool white tone, zero yellow light, extremely bright, absolutely no dark shadows. Preserve highlight detail and skin texture; daylight-balanced artificial lighting for night interiors."
    private fun episode(story: JSONObject, number: Int): JSONObject {
        val eps=story.getJSONArray("episodes")
        return (0 until eps.length()).map { eps.getJSONObject(it) }.first { it.getInt("ep")==number }
    }
    fun outline(story: JSONObject): String {
        val eps=story.getJSONArray("episodes")
        return buildString {
            append("1. ชื่อช่อง: ${story.optString("channel")}\n2. หมวดหมู่: ${story.optString("category")}\n3. ชื่อเรื่อง: ${story.getString("title")}\n4. คำโปรย: ${story.optString("tagline")}\n5. เรื่องย่อ:\n")
            for(i in 0 until eps.length()) {
                val ep=eps.getJSONObject(i)
                append("EP.${ep.getInt("ep")}: ${ep.optString("synopsis").ifBlank { ep.optString("tagline") }}\n")
            }
            append("6. จำนวนตอน: ${eps.length()}\n")
            for(i in 0 until eps.length()) { val ep=eps.getJSONObject(i); append("ตอนที่ ${ep.getInt("ep")} ${ep.optString("title")} • ${ep.optJSONArray("scenes")?.length() ?: 24} ฉาก\n") }
            append("\nกรุณาพิมพ์ 'สร้างภาพ EP.1' เพื่อดำเนินการต่อ\nในแอปให้เลือกปุ่มสร้างภาพของ EP ที่ต้องการ • ระบบเตรียมคำสั่ง ยังไม่ได้สร้างภาพจริง")
        }
    }
    private fun poster(story: JSONObject, people: List<JSONObject>, ratio: String, number: Int? = null): String {
        val ep=number?.let { episode(story,it) }
        val heading=if(number==null) "หน้าปกรวม" else "หน้าปกตอน EP.$number"
        val lines=if(ep==null) listOf(story.optString("channel"), story.getString("title"), story.optString("tagline"))
            else listOf(story.optString("channel"), "ตอนที่ $number ${ep.optString("title")}")
        val cue=ep?.optString("cover_incident")?.ifBlank { ep.optString("tagline") } ?: story.optString("tagline")
        return "[$heading $ratio] [แนบรูป: ${people.joinToString(", ") { alias(it) }}]\n" +
            binding(people,story)+"\n$identity\nBoth cover ratios use these identical full outfits and accessory lists. Recompose the camera only; do not redesign clothes or jewelry.\nCinematic waist-up medium close-up, zoomed in closely, dynamic dramatic editorial poses, intense eye contact, natural three-quarter body angles and reference proportions. NO full body, NO stiff standing. No collage. Story cue: ${normalize(cue,story)}\n"+
            FlowVisualDirection.layout(ratio.replace(":","x"))+"\nFINAL TEXT PLACEMENT: one compact group centered at x=50%, within x=15-85% and y=52-78% of the canvas. All text stacked tightly and perfectly centered horizontally in the lower-middle safe zone. Keep faces, hands and important props clear. Include only these exact Thai text lines:\n"+
            lines.joinToString("\n")+"\nNo extra text or watermark.\n"+ending(ratio)
    }
    private fun storyCoverPeople(story: JSONObject): List<JSONObject> =
        FlowCast.definitions(story).take(3).map { p ->
            val copy=JSONObject(p.toString())
            val eps=story.getJSONArray("episodes")
            var found: JSONObject?=null
            for(i in 0 until eps.length()) {
                val scenes=eps.getJSONObject(i).getJSONArray("scenes")
                for(j in 0 until scenes.length()) if(found==null) found=FlowReferencePrompt.people(scenes.getJSONObject(j)).firstOrNull { FlowCast.id(it)==FlowCast.id(p) }
            }
            found ?: copy.put("outfit",if(FlowCast.id(p)=="gawin")
                "Top: charcoal opaque cotton fitted open-collar long-sleeve shirt, plain, black buttons; Bottom: black tailored full-length wool trousers; Shoes: black leather dress shoes, flat sole; Accessories: none"
                else "Top: ${if(FlowCast.id(p)=="rinlada") "white" else "burgundy"} opaque lined silk fitted long-sleeve blouse, deep V-neck with visible natural cleavage, tailored waist, plain fabric; Bottom: black high-waisted knee-length fitted pencil skirt; Shoes: black leather closed-toe 5 cm heels; Accessories: none")
        }
    fun covers(story: JSONObject): String {
        val people=storyCoverPeople(story)
        return listOf("9:16","16:9").joinToString("\n\n") { poster(story,people,it) }
    }
    data class ImageTask(val id: String, val ratio: String, val people: List<JSONObject>, val prompt: String)
    fun imageTasks(story: JSONObject, number: Int? = null): List<ImageTask> = buildList {
        val leads=storyCoverPeople(story)
        for(ratio in listOf("9:16","16:9")) add(ImageTask("STORY_COVER_${ratio.replace(":","x")}",ratio,leads,poster(story,leads,ratio)))
        val eps=story.getJSONArray("episodes")
        if(number!=null) episode(story,number) // Reject a missing EP instead of silently exporting only overall covers.
        for(i in 0 until eps.length()) {
            val ep=eps.getJSONObject(i); val epNumber=ep.getInt("ep")
            if(number!=null && epNumber!=number) continue
            val scenes=ep.getJSONArray("scenes"); val cast=FlowReferencePrompt.people(scenes.getJSONObject(0)).take(2)
            for(ratio in listOf("9:16","16:9")) add(ImageTask("EP%02d_COVER_%s".format(epNumber,ratio.replace(":","x")),ratio,cast,poster(story,cast,ratio,epNumber)))
            for(j in 0 until scenes.length()) {
                val scene=scenes.getJSONObject(j)
                add(ImageTask("EP%02d_SC%03d".format(epNumber,scene.getInt("scene")),"9:16",FlowReferencePrompt.people(scene),sceneImage(story,scene)))
            }
        }
    }
    fun geminiNext(story: JSONObject, number: Int? = null): String {
        val wardrobeIssues=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(story))
        require(wardrobeIssues.isEmpty()) { wardrobeIssues.take(12).joinToString("\n") }
        val tasks=imageTasks(story,number)
        val refs=tasks.flatMap { it.people }.distinctBy { FlowCast.id(it) }
        return buildString {
            append("DIRECTOR X — GEMINI NEXT • คัดลอกคำสั่งทั้งหมดนี้ครั้งเดียว\nเรื่อง: ${story.getString("title")}\nขอบเขต: ${if(number==null) "ทุก EP" else "EP.$number"} • ${tasks.size} ภาพ\n\n")
            append("คุณคือผู้สร้างภาพซีรีส์จากคิวด้านล่าง ใช้เครื่องมือสร้างภาพที่มีจริง สร้างภาพจริงทีละ 1 ภาพ ไม่ตอบเป็นคำสั่งแทนภาพ ไม่สร้างหลายภาพรวมกันหรือ contact sheet และไม่สร้างวิดีโอหรือบทพูด\n")
            append("แนบภาพอ้างอิงทั้งหมดในรายการนี้พร้อมคำสั่งครั้งแรก จับคู่แต่ละภาพกับชื่อให้ชัดเจน ไม่อิงลำดับอัปโหลด หากจับคู่ไม่ได้ให้ถามก่อน:\n")
            refs.forEach { person -> append("- ${alias(person)} = ภาพต้นฉบับ ${FlowCast.id(person)!!.uppercase()}_MASTER (ไฟล์จริงใน MASTERS อาจเป็น png/jpg/webp ไม่ต้องเปลี่ยนชื่อ)\n") }
            append("\nWARDROBE LEDGER — คัดลอกชุดและเครื่องประดับเต็มทุกซีน ห้ามเปลี่ยนระหว่างสร้างภาพ:\n")
            append(normalize(AutoDirectorWardrobe.catalog(AutoDirectorWardrobe.shots(story)),story)).append("\n")
            append("กฎชุดรายวัน: วันเดียวกันต้องใช้เสื้อและกางเกง/กระโปรง รวมรองเท้าและเครื่องประดับเดิม แม้เปลี่ยนสถานที่ กิจกรรม หรือ EP วันใหม่ต้องวางเสื้อและกางเกง/กระโปรงชุดใหม่ ไฟล์แนบใช้เฉพาะใบหน้า ผม รูปร่าง ห้ามยืมเสื้อผ้าในไฟล์แนบมาใช้ next / retry / แก้ภาพ ต้องรักษาชุดและเครื่องประดับตามรายการเดิมทุกชิ้น รวมสี วัสดุ จำนวน รูปทรง ข้างและตำแหน่งที่สวม ไม่ออกแบบใหม่ คำขอแก้ชุดหมายถึงคืนชุดให้ตรงรายการล็อก ไม่เปลี่ยนเป็นชุดอื่น ถ้ารายละเอียดไม่พอให้ถาม ไม่เดา และการเปลี่ยนมุมภาพไม่ใช่เหตุให้เปลี่ยนชุด หลังผู้ใช้ตรวจภาพว่าตรงบัญชีชุดแล้วพิมพ์ next ให้ใช้ภาพที่ยอมรับล่าสุดของเหตุการณ์เดียวกันช่วยคงชุดและเครื่องประดับ คง MASTER ต้นฉบับเป็นหลักของใบหน้าและรูปร่าง หากภาพก่อนผิดชุดห้ามใช้เป็นต้นแบบ; เมื่อแก้สำเร็จให้ใช้ฉบับแก้ที่ยอมรับแล้วแทนฉบับเดิม\n")
            append("\nกติกา NEXT — ควบคุมคิวในแชตนี้:\n")
            append("1. อ่านคิวให้ครบ แล้วเริ่มสร้างภาพแรก ${tasks.first().id} ทันทีเมื่อมีภาพอ้างอิงของงานนั้นครบ หากขาดให้ระบุชื่อที่ขาดและหยุดรอ ไม่เดาหน้าตาหรือข้ามงาน ใช้ต้นฉบับเดิมเป็นฐานอัตลักษณ์เสมอ ไม่ใช้ภาพที่สร้างใหม่แทน MASTER\n")
            append("2. หลังสร้างและแสดงภาพจริงสำเร็จหนึ่งภาพ ให้หยุดรอคำว่า next (หรือ ถัดไป) เสมอ คำสั่ง next แต่ละครั้งสร้างงานถัดไปเพียงหนึ่งภาพ ห้ามทำงานถัดไปอัตโนมัติ\n")
            append("3. ใต้ภาพแสดงสถานะสั้น ๆ นอกภาพ: DONE รหัสภาพ | ลำดับ i/${tasks.size} | NEXT รหัสถัดไป ระบุชื่อไฟล์ที่แนะนำเป็น รหัสภาพ.png เช่น EP01_SC001.png ไม่อ้างว่าบันทึกหรือสร้างลิงก์ดาวน์โหลดแล้วหากยังไม่ได้ทำจริง ข้อความสถานะห้ามวาดทับลงในภาพ\n")
            append("4. ถ้าสร้างไม่สำเร็จ ถูกจำกัดโควตา หรือเครื่องมือไม่พร้อม ให้บอกตามจริงและเก็บงานนั้นเป็น PENDING ห้ามรายงาน DONE หรือเพิ่มตัวนับ คำว่า next หลังความล้มเหลวให้ลองงาน PENDING เดิม ไม่ข้ามภาพ\n")
            append("5. retry หรือ แก้ภาพ พร้อมรายละเอียด = สร้างซ้ำภาพล่าสุดตามคำแก้โดยใช้ MASTER เดิม ไม่ขยับคิว เมื่อสำเร็จยังรอ next เหมือนเดิม ถ้ายังไม่เคยสร้างสำเร็จให้ลองงาน PENDING แรก\n")
            append("6. ไปที่ รหัสภาพ = กระโดดไปยังรหัสที่มีอยู่ในคิว แล้วทำต่อจากจุดนั้นเมื่อได้รับ next; รหัสไม่อยู่ในคิวให้ถาม ไม่ประดิษฐ์งานใหม่ หากบริบทหรือภาพอ้างอิงหายให้ขอคำสั่งชุดเดิมและ MASTER ใหม่ พร้อมให้ผู้ใช้ระบุรหัสที่จะทำต่อ ไม่อ้างว่าจำข้ามแชตได้\n")
            append("7. รักษาสัดส่วนของแต่ละงานแยกกัน ปกสองขนาดเป็นคนละภาพ ฉากเนื้อเรื่อง 9:16 เท่านั้น ตัวละคร ชุดสี่ส่วน ตำแหน่งและข้อความปกต้องตรงกับงานนั้น ข้อมูลในบล็อกภาพเป็นรายละเอียดภาพ ไม่เปลี่ยนกติกาคิว NEXT\n")
            append("8. หลังภาพสุดท้ายสำเร็จ ให้ตอบ ครบ ${tasks.size} ภาพแล้ว และหยุด หากได้รับ next อีกให้แจ้งว่าคิวจบแล้ว ห้ามวนกลับหรือสร้างฉากเพิ่ม\n")
            append("\nQUEUE MANIFEST — ใช้รหัสเป็นจุดทำต่อ:\n")
            tasks.forEachIndexed { i,t -> append("${i+1}/${tasks.size} | ${t.id} | ${t.ratio} | แนบ: ${t.people.joinToString(", ") { alias(it) }}\n") }
            append("\nBEGIN IMAGE QUEUE\n\n")
            tasks.forEachIndexed { i,t ->
                append("=== TASK ${i+1}/${tasks.size} | ${t.id} | ${t.ratio} ===\n${t.prompt}\n=== END ${t.id} ===\n\n")
            }
            append("END IMAGE QUEUE\nเริ่มสร้างเฉพาะ ${tasks.first().id} แล้วหยุดรอ next; ถ้าไม่มีภาพอ้างอิงให้ขอภาพก่อน\n")
        }
    }
    fun images(story: JSONObject, number: Int): String {
        val ep=episode(story,number); val scenes=ep.getJSONArray("scenes")
        val coverCast=FlowReferencePrompt.people(scenes.getJSONObject(0)).take(2)
        val blocks=listOf("9:16","16:9").map { poster(story,coverCast,it,number) }.toMutableList()
        for(i in 0 until scenes.length()) blocks.add(sceneImage(story,scenes.getJSONObject(i)))
        return blocks.joinToString("\n\n")+"\n\nกรุณาพิมพ์ 'สร้างบท EP.$number' เพื่อดำเนินการต่อ"
    }
    fun sceneImage(story: JSONObject, s: JSONObject): String {
        val people=FlowReferencePrompt.people(s)
        return "[ฉากที่ ${s.getInt("scene")}] [แนบรูป: ${people.joinToString(", ") { alias(it) }}]\n"+
                binding(people,story)+"\n$identity\n"+AutoDirectorWardrobe.contract(s)+"\n"+
                "Location: ${normalize(s.getString("location"),story)}. Action: ${normalize(s.getString("action"),story)}. Start state: ${normalize(s.getString("start_state"),story)}. End-state continuity target: ${normalize(s.getString("end_state"),story)}.\n"+
                "${normalize(s.getString("image_prompt"),story)}\nFINAL OVERRIDES: Use only the attached identities and current four-part wardrobes above. Explicit left/center/right placement for all listed people. "+
                (if(speech(s.optString("dialogue")).isEmpty()) "All characters: mouth closed, lips sealed. " else "Only ${speaker(s)} is speaking; all others mouth closed, lips sealed. ")+
                "No captions, logos or watermark.\n"+ending("9:16")
    }
    fun externalSpec(story: JSONObject, spec: FlowSceneSpec): FlowSceneSpec {
        fun label(name: String): String = FlowCast.definitions(story).firstOrNull {
            it.optString("name")==name || StoryIdentity.keyFor(name)?.let { key -> FlowCast.id(it)==key }==true
        }?.let { alias(it) } ?: name
        return spec.copy(cast=spec.cast.map { it.copy(name=label(it.name),appearance=normalize(it.appearance,story),outfit=normalize(it.outfit,story)) },
            speaker=label(spec.speaker),action=normalize(spec.action,story),location=normalize(spec.location,story),startState=normalize(spec.startState,story),endState=normalize(spec.endState,story))
    }
    fun dialogue(story: JSONObject, number: Int): String {
        val scenes=episode(story,number).getJSONArray("scenes")
        return (0 until scenes.length()).joinToString("\n\n") { i ->
            val s=scenes.getJSONObject(i); val line=speech(s.optString("dialogue")); val vo=speech(s.optString("narration"))
            "[ฉากที่ ${s.getInt("scene")}] 8 วินาที\nการแสดง: ${normalize(s.getString("action"),story)}\n"+
                when { line.isNotEmpty() -> "${speaker(s)}: $line\nคนอื่นปิดปาก ไม่พูดแทรก"; vo.isNotEmpty() -> "VO (ผู้บรรยายภายนอก): $vo\nทุกคน mouth closed, lips sealed"; else -> "ไม่มีบทพูด • ทุกคน mouth closed, lips sealed" }
        }+"\n\nกรุณาพิมพ์ 'สร้าง XML EP.$number'"
    }
    private fun xml(value: String) = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    fun flowXml(story: JSONObject, number: Int): String {
        val scenes=episode(story,number).getJSONArray("scenes")
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<flow episode=\"$number\" status=\"template_requires_frame_review\" format=\"prompt_document_not_flow_api\">\n")
            append("  <instructions>XML เป็นเอกสารจัดบท คัดลอก motion_prompt และ dialogue ไปใช้ใน Flow ทีละฉาก พร้อมแนบภาพฉากที่ตรวจแล้ว ไม่ใช่ไฟล์รันหรือ API ของ Flow ตรวจภาพ ผู้พูด และตำแหน่งจริงก่อนใช้</instructions>\n")
            for(i in 0 until scenes.length()) {
                val s=scenes.getJSONObject(i); val id="EP%02d_SC%03d".format(number,s.getInt("scene")); val line=speech(s.optString("dialogue")); val vo=speech(s.optString("narration"))
                append("\n  <scene id=\"$id\" duration_seconds=\"8\" aspect_ratio=\"9:16\">\n    <start_frame>$id — ภาพฉากที่ตรวจเทียบ MASTER และยืนยันผู้พูดแล้วเท่านั้น</start_frame>\n")
                val audio=when { line.isNotEmpty() -> "Only ${speaker(s)} speaks this exact Thai line: $line. All others mouth closed, lips sealed."; vo.isNotEmpty() -> "External male VO: $vo. All visible characters mouth closed, lips sealed."; else -> "Silent acting, no dialogue or narration; all mouths closed, lips sealed." }
                val spec=FlowSceneSpec(FlowReferencePrompt.people(s).map { FlowPerson(it.getString("name"),it.getString("visual_description"),it.getString("screen_position"),it.getString("outfit")) },s.optString("speaker"),line,vo,s.optString("flow_action").ifBlank { s.getString("action") },s.getString("location"),s.getString("start_state"),s.getString("end_state"),8,"9:16")
                val motion=FlowScenePrompt.build(externalSpec(story,spec))+" FINAL AUDIO: $audio"
                append("    <motion_prompt>${xml(motion)}</motion_prompt>\n    <dialogue speaker=\"${xml(if(line.isNotEmpty()) speaker(s) else if(vo.isNotEmpty()) "VO" else "") }\">${xml(line.ifEmpty { vo })}</dialogue>\n")
                append("    <lighting temperature=\"5500K\" tone=\"cool_white\" source=\"reviewed_start_frame\">Preserve reviewed frame lighting; no yellow cast, no dark shadows.</lighting>\n    <typography_safe_zone position=\"lower_middle\" enabled=\"false\">Scene video has no typography; cover text belongs on separate cover images.</typography_safe_zone>\n  </scene>\n")
            }
            val hasNext=story.getJSONArray("episodes").let { eps -> (0 until eps.length()).any { eps.getJSONObject(it).getInt("ep")>number } }
            append("  <next_step>${if(hasNext) "หากมีตอนต่อไปพิมพ์ 'สร้างภาพ EP.${number+1}'" else "หากจบพิมพ์ 'เสร็จสิ้น'"}</next_step>\n</flow>\n")
        }
    }
}
