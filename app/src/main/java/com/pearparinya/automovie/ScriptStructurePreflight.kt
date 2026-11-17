package com.pearparinya.automovie

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
