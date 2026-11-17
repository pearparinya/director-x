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
                    if (castArray.optJSONObject(index) == null)
                        structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: characters ลำดับ ${index + 1} ต้องเป็น object")
                }
                val offscreenArray = s.optJSONArray("offscreen_characters")
                if (offscreenArray != null) for (index in 0 until offscreenArray.length()) {
                    if (offscreenArray.opt(index) !is String)
                        structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: offscreen_characters ลำดับ ${index + 1} ต้องเป็นข้อความ")
                }
                if (s.optString("dialogue").isNotBlank() &&
                    s.optJSONArray("dialogue_turns") == null &&
                    s.optString("speaker").isBlank())
                    structural.add("EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: มีบทพูดแต่ขาด speaker")
                val turns = s.optJSONArray("dialogue_turns")
                val speakers = if (turns != null) (0 until turns.length()).map { index ->
                    val turn = turns.optJSONObject(index)
                    if (turn == null) structural.add(
                        "EP${ep.optInt("ep", i + 1)} SC${s.optInt("scene", j + 1)}: dialogue_turns ลำดับ ${index + 1} ต้องเป็น object")
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

    private fun key(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    fun issues(episodes: List<Episode>): List<String> = buildList {
        if (episodes.isEmpty()) add("ไม่มีข้อมูล EP")
        val seenEpisodes = mutableSetOf<Int>()
        episodes.forEachIndexed { episodeIndex, episode ->
            val ep = "EP${episode.number}"
            if (episode.number <= 0) add("EP ลำดับ ${episodeIndex + 1}: หมายเลข EP ต้องมากกว่า 0")
            if (!seenEpisodes.add(episode.number)) add("$ep: หมายเลข EP ซ้ำ")
            if (episode.scenes.isEmpty()) add("$ep: ไม่มีฉาก")
            val seenScenes = mutableSetOf<Int>()
            episode.scenes.forEachIndexed { sceneIndex, scene ->
                val label = "$ep SC${scene.number}"
                if (scene.number <= 0) add("$ep ฉากลำดับ ${sceneIndex + 1}: หมายเลขฉากต้องมากกว่า 0")
                if (!seenScenes.add(scene.number)) add("$label: หมายเลขฉากซ้ำ")
                if (scene.day.isBlank()) add("$label: ขาด story_day")
                if (scene.location.isBlank()) add("$label: ขาด location")
                if (scene.time.isBlank()) add("$label: ขาด story_time")
                if (scene.startState.isBlank()) add("$label: ขาด start_state")
                if (scene.endState.isBlank()) add("$label: ขาด end_state")
                if (scene.durationSeconds != 8) add("$label: Flow ต้องใช้เวลา 8 วินาที")
                val cast = scene.characterNames.map(::key)
                val offscreen = scene.offscreenNames.map(::key)
                if (cast.isEmpty()) add("$label: ขาดรายชื่อตัวละคร")
                if (cast.any { it.isEmpty() }) add("$label: มีตัวละครที่ขาดชื่อ")
                if (cast.filter { it.isNotEmpty() }.distinct().size != cast.count { it.isNotEmpty() })
                    add("$label: มีชื่อตัวละครซ้ำ")
                if (offscreen.any { it.isEmpty() }) add("$label: มีตัวละครนอกภาพที่ขาดชื่อ")
                if (offscreen.filter { it.isNotEmpty() }.distinct().size != offscreen.count { it.isNotEmpty() })
                    add("$label: มีชื่อตัวละครนอกภาพซ้ำ")
                if (cast.intersect(offscreen.toSet()).any { it.isNotEmpty() })
                    add("$label: ตัวละครอยู่ทั้งในภาพและนอกภาพ")
                scene.dialogueSpeakers.forEachIndexed { turn, speaker ->
                    val normalized = key(speaker)
                    if (normalized.isEmpty()) add("$label: บทพูดลำดับ ${turn + 1} ขาดชื่อผู้พูด")
                    else if (normalized !in cast && normalized !in offscreen)
                        add("$label: บทพูดลำดับ ${turn + 1} ไม่พบผู้พูด $speaker")
                }
            }
        }
    }
}
