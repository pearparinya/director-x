package com.pearparinya.automovie

import org.json.JSONObject

/** Deterministic checks only: never invent missing actors, wardrobe or plot repairs. */
internal object ProductionCommandCheck {
    const val planning="COMMAND CONSISTENCY: Every person physically participating in action/start_state/end_state must be listed in characters with a distinct screen_position. A solo shot cannot step between two people or touch an unlisted person. Use a smile or silent amused expression instead of audible laughter; sound fields never add human voices. Keep action, start/end state, cast and exact speech mutually consistent. Use mentioned_characters for absent people discussed and offscreen_characters for people outside the frame; only the named speaker may speak; neither belongs in the visible characters array."
    private fun people(scene: JSONObject)=scene.optJSONArray("characters")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
    private fun speech(value: String)=value.trim().takeUnless { it.lowercase() in setOf("", "none", "n/a", "ไม่มี", "ไม่มีเสียงบรรยาย") }.orEmpty()
    fun offscreen(scene: JSONObject): List<String> = scene.optJSONArray("offscreen_characters")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    fun spec(scene: JSONObject) = FlowSceneSpec(people(scene).map {
        FlowPerson(it.optString("name"),it.optString("visual_description"),it.optString("screen_position"),it.optString("outfit"),PerformanceDirection.text(it))
    },speech(scene.optString("speaker")),speech(scene.optString("dialogue")),speech(scene.optString("narration")),scene.optString("flow_action").ifBlank { scene.optString("action") },scene.optString("location"),scene.optString("start_state"),scene.optString("end_state"),8,"9:16",SoundDesign.notes(scene),offscreen(scene))
    private fun position(text: String): String? {
        val value=text.trim().lowercase().substringBefore(',').trim()
        return when(value) { "left","ซ้าย" -> "left"; "right","ขวา" -> "right"; "center","centre","middle","กลาง" -> "center"; else -> null }
    }
    fun sceneIssues(scene: JSONObject): List<String> = buildList {
        addAll(FlowScenePrompt.issues(spec(scene)))
        val people=people(scene)
        if(people.isEmpty()) add("ขาดตัวละครในซีน")
        if(scene.optInt("duration_seconds",8)!=8) add("ซีนต้องใช้ 8 วินาที")
        val camera=scene.optString("camera_movement").trim()
        if(camera.isNotEmpty() && camera !in setOf("push_in","pan","track","locked")) add("การเคลื่อนกล้องไม่รองรับ: เลือกซูม แพน ติดตาม หรือกล้องนิ่ง")
        val positions=people.mapNotNull { position(it.optString("screen_position")) }
        if(positions.distinct().size!=positions.size) add("ตำแหน่งตัวละครซ้ำ ต้องระบุซ้าย/กลาง/ขวาหรือระยะหน้า-หลังแยกกัน")
        val action=scene.optString("flow_action").ifBlank { scene.optString("action") }
        if(people.size<3 && Regex("(?i)(between (them|the two|both)|ระหว่างทั้งสอง|แทรกกลางทั้งสอง)").containsMatchIn(action)) add("การกระทำระหว่างสองคนต้องมีตัวละครครบ 3 คน หรือแก้เป็นการกระทำของคนในภาพ")
        if(!Regex("(?i)(silent|inaudible|no audible|ไม่ออกเสียง|ไร้เสียง)").containsMatchIn(action) && Regex("(?i)(\\blaugh(?:s|ing|ed)?\\b|หัวเราะ)").containsMatchIn(action)) add("การหัวเราะมีโอกาสเพิ่มเสียงนอกบท ให้ระบุยิ้มหรือหัวเราะแบบไม่ออกเสียง")
        val sound=SoundDesign.notes(scene)
        if(Regex("(?i)(audience laughter|canned laugh|laugh track|vocal laugh|เสียงหัวเราะ|เสียงพูด)").containsMatchIn(sound) && !Regex("(?i)(no |without |ไม่มี|ห้าม)").containsMatchIn(sound)) add("แผนเสียงมีเสียงคนเพิ่มเติมนอกบท")
        for(field in listOf("action","location","start_state","end_state","story_day")) if(scene.optString(field).isBlank()) add("ขาด $field")
        people.forEach { p -> runCatching { AutoDirectorWardrobe.parts(p.optString("outfit")) }.exceptionOrNull()?.let { add("${p.optString("name")}: ${it.message}") } }
    }
    fun namedIssues(scene: JSONObject,definitions: List<JSONObject>): List<String> = buildList {
        val aliases=mapOf("gawin" to listOf("กวิน","Kawin","Gawin","The Man"),"rinlada" to listOf("รินรดา","Rinrada","Rinlada","Woman 1"),"mind" to listOf("เมย์","May","Mind","Woman 2"))
        val present=people(scene).map { it.optString("name") }.toSet()
        fun references(field: String): Set<String> = scene.optJSONArray(field)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
        val mentioned=references("mentioned_characters"); val offscreen=references("offscreen_characters")
        val known=definitions.map { it.optString("name") }.toSet()
        if((mentioned+offscreen).any { it !in known }) add("ชื่อคนนอกภาพหรือคนที่กล่าวถึงไม่อยู่ในทะเบียนตัวละคร")
        if(offscreen.intersect(present).isNotEmpty() || mentioned.intersect(present).isNotEmpty() || offscreen.intersect(mentioned).isNotEmpty()) add("คนในภาพ คนนอกภาพ และคนที่กล่าวถึงต้องไม่ซ้ำกัน")
        val cues=Regex("(?i)(talks? about|speaks? about|mentions?|photo|picture|portrait|off[- ]screen|outside the frame|on the phone|พูดถึง|เล่าถึง|นึกถึง|รูปถ่าย|ภาพของ|นอกภาพ|นอกเฟรม|ปลายสาย)")
        for(field in listOf("action","flow_action","start_state","end_state")) {
            val action=scene.optString(field)
            for(clause in action.split(Regex("[.;\n]"))) {
                definitions.forEach { person ->
                    val id=FlowCast.id(person); val name=person.optString("name")
                    if(name in present) return@forEach
                    val names=aliases[id] ?: listOf(name)
                    val found=names.any { alias ->
                        if(alias.isBlank()) false
                        else if(Regex("[ก-๙]").containsMatchIn(alias)) Regex("(?<![ก-๙])"+Regex.escape(alias)+"(?![ะ-์])").containsMatchIn(clause)
                        else Regex("\\b${Regex.escape(alias)}\\b").containsMatchIn(clause)
                    }
                    if(found && !cues.containsMatchIn(clause) && name !in mentioned && name !in offscreen)
                        add("$field กล่าวถึง $name แต่ไม่มีในรายการตัวละคร ระบุว่าอยู่ในภาพ นอกภาพ หรือถูกกล่าวถึง แล้วแก้การกระทำให้ตรงกัน")
                    if(found && name in mentioned+offscreen && Regex("(?i)(hands? .* to|touches?|hugs?|shakes? hands|จับมือ|กอด|สัมผัส|ส่ง.*ให้)").containsMatchIn(clause) && !cues.containsMatchIn(clause))
                        add("$field ให้ $name สัมผัสคนในภาพ แต่ระบุว่าไม่อยู่ในภาพ")
                }
            }
        }
    }

    fun issues(story: JSONObject): List<String> = buildList {
        for(field in listOf("channel","title","tagline")) if(story.optString(field).isBlank()) add("ปกรวม: ขาด $field")
        if(story.optString("aspect_ratio")!="9:16") add("ภาพฉากและวิดีโอต้องเป็น 9:16")
        addAll(FlowCast.issues(story))
        val eps=story.optJSONArray("episodes")
        if(eps==null || eps.length()==0) { add("ไม่มี EP"); return@buildList }
        val numbers=mutableSetOf<Int>()
        for(i in 0 until eps.length()) {
            val ep=eps.getJSONObject(i); val number=ep.optInt("ep")
            if(number!=i+1 || !numbers.add(number)) add("เลข EP ไม่ถูกต้องหรือซ้ำ: $number")
            if(ep.optString("title").isBlank()) add("EP$number ปกตอน: ขาดชื่อตอน")
            val rows=ep.optJSONArray("scenes")
            if(rows==null || rows.length()==0) { add("EP$number ไม่มีซีน"); continue }
            val scenes=mutableSetOf<Int>()
            for(j in 0 until rows.length()) {
                val s=rows.getJSONObject(j); val n=s.optInt("scene")
                val key="EP%02d_SC%03d".format(number,n)
                if(n!=j+1 || !scenes.add(n)) add("$key เลขซีนไม่ถูกต้องหรือซ้ำ")
                addAll(CharacterAgeCheck.issues(s,FlowCast.definitions(story)).map { "$key: $it" })
                addAll(sceneIssues(s).map { "$key: $it" })
                if(story.optInt("audio_version")>=1) runCatching { SoundDesign.validate(s) }.exceptionOrNull()?.let { add("$key: ${it.message}") }
                if(story.optInt("performance_version")>=1) runCatching { PerformanceDirection.validate(s) }.exceptionOrNull()?.let { add("$key: ${it.message}") }
                people(s).forEach { person -> if(!FlowCast.matches(story,person)) add("$key: MASTER ไม่ตรงตัวละคร ${person.optString("name")}") }
            }
        }
        AutoDirectorWardrobe.shots(story).forEach { shot -> addAll(namedIssues(shot.scene,FlowCast.definitions(story)).map { "EP%02d_SC%03d: %s".format(shot.ep,shot.scene.optInt("scene"),it) }) }
        addAll(AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(story)))
    }.distinct()
}
