package com.pearparinya.automovie

import org.json.JSONObject

/** Portable production documents. XML organizes prompts; it is not a Flow API. */
internal object AutoDirectorContent {
    class AwaitingOutline : IllegalStateException("โครงเรื่องพร้อมแล้ว • ดูและยืนยันก่อนเตรียมคำสั่งภาพ Gemini")
    const val profile = "autodirector_v18"
    const val generationRules = """AutoDirector v18 production profile. All characters are fictional adults (age 25+).
The Man is the husband, Woman 1 the wife, Woman 2 the affair partner. Use the existing internal canonical names and MASTER IDs in JSON only; the app maps them to these external aliases.
Write a new dramatic plot in the selected category with 24 complete scenes per episode. Vary conversation, argument, monologue, silent reaction and external narration across the story. Alternate speakers across scenes, never overlap speakers within one 8-second shot.
Every character outfit MUST explicitly list four labeled parts in English: Top: ...; Bottom: ...; Shoes: ...; Accessories: ... (write none if absent). Keep the same four parts in continuous events; change them for a new day or activity. Use elegant fitted editorial fashion and scene-appropriate low necklines for adult women, fully clothed, SFW, no visible nipples or transparent fabric. Match each reference's actual proportions without enlarging or reducing anatomy.
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
            require(listOf("Top", "Bottom", "Shoes", "Accessories").all { part ->
                Regex("(?i)\\b$part\\s*:\\s*[^;\\n]+").containsMatchIn(outfit)
            }) { "AutoDirector: ชุดของ ${person.optString("name")} ต้องระบุ Top / Bottom / Shoes / Accessories ครบ 4 ส่วน" }
        }
    }
    private fun speaker(scene: JSONObject): String = FlowReferencePrompt.people(scene).firstOrNull {
        it.optString("name") == scene.optString("speaker") ||
            StoryIdentity.keyFor(scene.optString("speaker"))?.let { key -> FlowCast.id(it) == key } == true
    }?.let { alias(it) } ?: ""

    private fun binding(people: List<JSONObject>, story: JSONObject): String = people.joinToString("\n") { p ->
        val appearance = p.optString("visual_description").ifBlank { FlowProductionPack.dna[FlowCast.id(p)].orEmpty() }
        "${alias(p)}: attached original reference only; ${normalize(appearance,story)}\n" +
            "Wardrobe — ${normalize(p.optString("outfit"),story)}. Four parts: Top / Bottom / Shoes / Accessories; keep the stated outfit, do not copy reference clothing.\n" +
            "Position: ${p.optString("screen_position").ifBlank { "compose around the reserved text area" }}."
    }
    private const val identity = "Request exact reference facial identity, hairstyle, hair length and hair color. Match each adult woman's reference bust volume and natural silhouette 1:1 relative to shoulders, waist and hips; no enlargement, reduction, blending or anatomical redesign. Elegant fitted editorial fashion, scene-appropriate neckline, strictly SFW, opaque clothes, absolutely no visible nipples. Preserve each reference's natural skin tone and visible skin texture. Compare actual outputs with the original references; identity lock is a prompt target, not a verified percentage."
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
            binding(people,story)+"\n$identity\nCinematic waist-up medium close-up, zoomed in closely, dynamic dramatic editorial poses, intense eye contact, natural three-quarter body angles and reference proportions. NO full body, NO stiff standing. No collage. Story cue: ${normalize(cue,story)}\n"+
            FlowVisualDirection.layout(ratio.replace(":","x"))+"\nFINAL TEXT PLACEMENT: one compact group centered at x=50%, within x=15-85% and y=52-78% of the canvas. All text stacked tightly and perfectly centered horizontally in the lower-middle safe zone. Keep faces, hands and important props clear. Include only these exact Thai text lines:\n"+
            lines.joinToString("\n")+"\nNo extra text or watermark.\n"+ending(ratio)
    }
    fun covers(story: JSONObject): String {
        val people=FlowCast.definitions(story).take(3).map { p ->
            val copy=JSONObject(p.toString())
            val eps=story.getJSONArray("episodes")
            var found: JSONObject?=null
            for(i in 0 until eps.length()) {
                val scenes=eps.getJSONObject(i).getJSONArray("scenes")
                for(j in 0 until scenes.length()) if(found==null) found=FlowReferencePrompt.people(scenes.getJSONObject(j)).firstOrNull { FlowCast.id(it)==FlowCast.id(p) }
            }
            found ?: copy.put("outfit","Top: elegant opaque fitted shirt; Bottom: tailored trousers; Shoes: dress shoes; Accessories: none")
        }
        return listOf("9:16","16:9").joinToString("\n\n") { poster(story,people,it) }
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
                binding(people,story)+"\n$identity\n"+
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
