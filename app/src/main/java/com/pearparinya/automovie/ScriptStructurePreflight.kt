package com.pearparinya.automovie

import org.json.JSONObject

/**
 * Pure structural preflight for episode/scene metadata. Does not alter a script,
 * translate dialogue, or infer missing plot details.
 */
internal object ScriptStructurePreflight {
    data class Scene(
        val number: Int,
        val day: String,
        val location: String,
        val time: String,
        val startState: String,
        val endState: String,
        val durationSeconds: Int,
        val characterNames: List<String>,
        val dialogueSpeakers: List<String>,
        val offscreenNames: List<String> = emptyList()
    )
    data class Episode(val number: Int, val scenes: List<Scene>)

    /** Opt-in adapter for the existing story.json schema; never mutates the input. */
    fun storyIssues(story: JSONObject): List<String> {
        val rows = story.optJSONArray("episodes") ?: return listOf("ข้อมูล episodes ต้องเป็น array")
        val episodes = mutableListOf<Episode>()
        val structural = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val ep = rows.optJSONObject(i)
            if (ep == null) {
                structural.add("EP ลำดับ ${i + 1}: ต้องเป็น object")
                continue
            }
            if (ep.has("ep") && (ep.opt("ep") !is Number ||
                    ep.opt("ep").toString().toLongOrNull() == null))
                structural.add("EP ลำดับ ${i + 1}: ep ต้องเป็นจำนวนเต็ม")
            val scenesArray = ep.optJSONArray("scenes")
            if (scenesArray == null) {
                structural.add("EP${ep.optInt("ep", i + 1)}: ข้อมูล scenes ต้องเป็น array")
                continue
            }
            val scenes = mutableListOf<Scene>()
            for (j in 0 until scenesArray.length()) {
                val s = scenesArray.optJSONObject(j)
                if (s == null) {
                    structural.add("EP${ep.optInt("ep", i + 1)} SC ลำดับ ${j + 1}: ต้องเป็น object")
                    continue
                }
                fun names(field: String): List<String> {
                    val a = s.optJSONArray(field) ?: return emptyList()
                    return (0 until a.length()).map { index ->
                        if (field == "characters") a.optJSONObject(index)?.optString("name").orEmpty()
                        else a.optString(index)
                    }
                }
                for (field in listOf("characters", "offscreen_characters", "dialogue_turns")) {
                    if (s.has(field) && s.optJSONArray(field) == null)
                        structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: $field ต้องเป็น array")
                }
                val castArray = s.optJSONArray("characters")
                if (castArray != null) for (index in 0 until castArray.length()) {
                    val person = castArray.optJSONObject(index)
                    if (person == null)
                        structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: characters ลำดับ ${index + 1} ต้องเป็น object")
                    else if (person.opt("name") !is String)
                        structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: characters ลำดับ ${index + 1} name ต้องเป็นข้อความ")
                }
                val offscreenArray = s.optJSONArray("offscreen_characters")
                if (offscreenArray != null) for (index in 0 until offscreenArray.length()) {
                    if (offscreenArray.opt(index) !is String)
                        structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: offscreen_characters ลำดับ ${index + 1} ต้องเป็นข้อความ")
                }
                if (s.optString("dialogue").isNotBlank() &&
                    (s.optJSONArray("dialogue_turns")?.length() ?: 0) == 0 &&
                    s.optString("speaker").isBlank())
                    structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: มีบทพูดแต่ขาด speaker")
                val singleSpeaker = s.opt("speaker")
                if (singleSpeaker != null && singleSpeaker !is String)
                    structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: speaker ต้องเป็นข้อความ")
                val singleDialogue = s.opt("dialogue")
                if (singleDialogue != null && singleDialogue !is String)
                    structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: dialogue ต้องเป็นข้อความ")
                val narration = s.opt("narration")
                if (narration != null && narration !is String)
                    structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: narration ต้องเป็นข้อความ")
                val turns = s.optJSONArray("dialogue_turns")
                val hasTurns = turns != null && turns.length() > 0
                val label = "EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}"
                for (field in listOf("story_day", "location", "story_time", "start_state", "end_state")) {
                    if (s.has(field) && s.opt(field) !is String)
                        structural.add("$label: $field ต้องเป็นข้อความ")
                }
                for (field in listOf("scene", "duration_seconds")) {
                    if (s.has(field) && (s.opt(field) !is Number ||
                            s.opt(field).toString().toLongOrNull() == null))
                        structural.add("$label: $field ต้องเป็นจำนวนเต็ม")
                }
                if (hasTurns && (s.optString("dialogue").isNotBlank() || s.optString("speaker").isNotBlank()))
                    structural.add("$label: dialogue_turns ขัดแย้งกับ speaker/dialogue แบบเดี่ยว")
                if (s.optString("narration").isNotBlank() &&
                    (hasTurns || s.optString("dialogue").isNotBlank()))
                    structural.add("$label: narration และบทพูดไม่ควรอยู่ในฉากเดียวกัน")
                val speakers = if (hasTurns) (0 until turns!!.length()).map { index ->
                    val turn = turns.optJSONObject(index)
                    if (turn == null) structural.add(
                        "EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: dialogue_turns ลำดับ ${index + 1} ต้องเป็น object")
                    else {
                        if (turn.opt("speaker") !is String)
                            structural.add("$label: dialogue_turns ลำดับ ${index + 1} speaker ต้องเป็นข้อความ")
                        if (turn.opt("text") !is String)
                            structural.add("$label: dialogue_turns ลำดับ ${index + 1} text ต้องเป็นข้อความ")
                        else if (turn.optString("text").isBlank())
                            structural.add("$label: dialogue_turns ลำดับ ${index + 1} ขาดข้อความบทพูด")
                    }
                    turn?.optString("speaker").orEmpty()
                } else listOfNotNull(s.optString("speaker").takeIf { it.isNotBlank() })
                scenes.add(Scene(
                    number = if (s.has("scene")) s.optInt("scene", 0) else j + 1,
                    day = s.optString("story_day"), location = s.optString("location"),
                    time = s.optString("story_time"), startState = s.optString("start_state"),
                    endState = s.optString("end_state"),
                    durationSeconds = if (s.has("duration_seconds")) s.optInt("duration_seconds", 0) else 8,
                    characterNames = names("characters"), dialogueSpeakers = speakers,
                    offscreenNames = names("offscreen_characters")
                ))
            }
            episodes.add(Episode(if (ep.has("ep")) ep.optInt("ep", 0) else i + 1, scenes))
        }
        return structural + issues(episodes)
    }

    private val whitespace = Regex("\\s+")
    private fun key(value: String): String =
        value.trim().lowercase().replace(whitespace, " ")

    fun issues(episodes: List<Episode>): List<String> = buildList {
        if (episodes.isEmpty()) add("ไม่มีข้อมูล EP")
        val seenEpisodes = mutableSetOf<Int>()
        var previousEpisodeNumber: Int? = null
        episodes.forEachIndexed { episodeIndex, episode ->
            val ep = "EP${episode.number}"
            if (episode.number <= 0) add("EP ลำดับ ${episodeIndex + 1}: หมายเลข EP ต้องมากกว่า 0")
            if (!seenEpisodes.add(episode.number)) add("$ep: หมายเลข EP ซ้ำ")
            if (previousEpisodeNumber != null && episode.number < previousEpisodeNumber!!)
                add("$ep: ลำดับ EP ย้อนกลับจาก EP$previousEpisodeNumber")
            previousEpisodeNumber = episode.number
            if (episode.scenes.isEmpty()) add("$ep: ไม่มีฉาก")
            val seenScenes = mutableSetOf<Int>()
            var previousSceneNumber: Int? = null
            episode.scenes.forEachIndexed { sceneIndex, scene ->
                val label = "$ep SC${scene.number}"
                if (scene.number <= 0) add("$ep ฉากลำดับ ${sceneIndex + 1}: หมายเลขฉากต้องมากกว่า 0")
                if (!seenScenes.add(scene.number)) add("$label: หมายเลขฉากซ้ำ")
                if (previousSceneNumber != null && scene.number < previousSceneNumber!!)
                    add("$label: ลำดับฉากย้อนกลับจาก SC$previousSceneNumber")
                previousSceneNumber = scene.number
                if (scene.day.isBlank()) add("$label: ขาด story_day")
                if (scene.location.isBlank()) add("$label: ขาด location")
                if (scene.time.isBlank()) add("$label: ขาด story_time")
                if (scene.startState.isBlank()) add("$label: ขาด start_state")
                if (scene.endState.isBlank()) add("$label: ขาด end_state")
                if (scene.durationSeconds != 8) add("$label: Flow ต้องใช้เวลา 8 วินาที")
                val cast = scene.characterNames.map(::key)
                val offscreen = scene.offscreenNames.map(::key)
                val castSet = cast.toHashSet()
                val offscreenSet = offscreen.toHashSet()
                if (cast.isEmpty()) add("$label: ขาดรายชื่อตัวละคร")
                if (cast.any { it.isEmpty() }) add("$label: มีตัวละครที่ขาดชื่อ")
                if (castSet.count { it.isNotEmpty() } != cast.count { it.isNotEmpty() })
                    add("$label: มีชื่อตัวละครซ้ำ")
                if (offscreen.any { it.isEmpty() }) add("$label: มีตัวละครนอกภาพที่ขาดชื่อ")
                if (offscreenSet.count { it.isNotEmpty() } != offscreen.count { it.isNotEmpty() })
                    add("$label: มีชื่อตัวละครนอกภาพซ้ำ")
                if (castSet.any { it.isNotEmpty() && it in offscreenSet })
                    add("$label: ตัวละครอยู่ทั้งในภาพและนอกภาพ")
                scene.dialogueSpeakers.forEachIndexed { turn, speaker ->
                    val normalized = key(speaker)
                    if (normalized.isEmpty()) add("$label: บทพูดลำดับ ${turn + 1} ขาดชื่อผู้พูด")
                    else if (normalized !in castSet && normalized !in offscreenSet)
                        add("$label: บทพูดลำดับ ${turn + 1} ไม่พบผู้พูด $speaker")
                }
            }
        }
    }
}
