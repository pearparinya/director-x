package com.pearparinya.automovie

import org.json.JSONObject

/** One self-contained command per card. Headings are outside the command to avoid image captions. */
internal object SceneCommandCards {
    data class Card(val title: String,val command: String, val visualWarnings: List<String> = emptyList())
    private fun episode(story: JSONObject,number: Int): JSONObject {
        val eps=story.getJSONArray("episodes")
        return (0 until eps.length()).map { eps.getJSONObject(it) }.first { it.getInt("ep")==number }
    }
    fun images(story: JSONObject,number: Int): List<Card> {
        LandscapeProduction.requirePlan(story)
        if(story.optString("content_profile")==AutoDirectorContent.profile) return AutoDirectorContent.imageTasks(story,number).map { task ->
            val orientation=if(task.ratio=="9:16") "แนวตั้ง 9:16" else "แนวนอน 16:9"
            val title=when { task.id.startsWith("STORY_COVER") -> "ปกรวม • $orientation"; task.id.contains("_COVER_") -> "ปก EP.$number • $orientation"; else -> "ซีน ${task.id.substringAfterLast("SC").toInt()} • ภาพแนวตั้ง 9:16" }
            Card(title,task.prompt)
        }
        val ep=episode(story,number); val scenes=ep.getJSONArray("scenes")
        val first=scenes.getJSONObject(0)
        val all=linkedMapOf<String,JSONObject>(); val epCast=linkedMapOf<String,JSONObject>()
        val eps=story.getJSONArray("episodes")
        for(e in 0 until eps.length()) {
            val rows=eps.getJSONObject(e).getJSONArray("scenes")
            for(i in 0 until rows.length()) FlowReferencePrompt.people(rows.getJSONObject(i)).forEach { person ->
                all.putIfAbsent(person.getString("name"),person)
                if(eps.getJSONObject(e).getInt("ep")==number) epCast.putIfAbsent(person.getString("name"),person)
            }
        }
        val refs=FlowReferencePrompt.header(epCast.values.take(3)); val storyRefs=FlowReferencePrompt.header(all.values.take(3))
        return buildList {
            for(ratio in listOf("9x16","16x9")) add(Card("ปกรวม • ${if(ratio=="9x16") "แนวตั้ง 9:16" else "แนวนอน 16:9"}",FlowVisualDirection.storyCover(ratio,storyRefs,story.optString("channel"),story.getString("title"),story.optString("tagline"),first.getString("action"))))
            for(ratio in listOf("9x16","16x9")) add(Card("ปก EP.$number • ${if(ratio=="9x16") "แนวตั้ง 9:16" else "แนวนอน 16:9"}",FlowVisualDirection.cover(ratio,refs,story.optString("channel"),story.getString("title"),number,ep.optString("title"),story.optString("tagline"),first.getString("action"))))
            for(i in 0 until scenes.length()) add(Card("ซีน ${scenes.getJSONObject(i).getInt("scene")} • ภาพแนวตั้ง 9:16",FlowReferencePrompt.scene(scenes.getJSONObject(i),number,"image")))
        }.map { it.copy(command=it.command+"\n"+ChannelCatalog.genre(story)) }
    }
    internal fun videoCard(sceneNumber: Int, spec: FlowSceneSpec, camera: String = "Slow, gentle pan."): Card {
        val warnings = ShortFlowPrompt.visualWarnings(spec, camera)
        val title = "ซีน $sceneNumber • วิดีโอแนวตั้ง 9:16 • 8 วินาที" +
            if (warnings.isNotEmpty()) " • ⚠ ตรวจสอบภาพเริ่มต้น ${warnings.size} จุด" else ""
        return Card(title, ShortFlowPrompt.build(spec, camera), warnings)
    }
    fun videos(story: JSONObject,number: Int): List<Card> {
        LandscapeProduction.requirePlan(story)
        val rows=episode(story,number).getJSONArray("scenes")
        return (0 until rows.length()).map { i ->
            val s=rows.getJSONObject(i)
            val spec=FlowSceneSpec(FlowReferencePrompt.people(s).map { FlowPerson(it.getString("name"),it.getString("visual_description"),it.getString("screen_position"),it.getString("outfit"),PerformanceDirection.text(it)) },s.optString("speaker"),s.optString("dialogue"),s.optString("narration"),s.optString("flow_action").ifBlank { s.getString("action") },s.getString("location"),s.getString("start_state"),s.getString("end_state"),8,"9:16",SoundDesign.notes(s),ProductionCommandCheck.offscreen(s),ProductionCommandCheck.dialogueTurns(s))
            videoCard(s.getInt("scene"), spec)
        }
    }
    internal data class EpisodeWarningSummary(val flaggedScenes: Int, val warningCount: Int, val details: String)
    internal fun episodeWarningSummary(cards: List<Card>): EpisodeWarningSummary {
        val flagged = cards.filter { it.visualWarnings.isNotEmpty() }
        return EpisodeWarningSummary(
            flaggedScenes = flagged.size,
            warningCount = flagged.sumOf { it.visualWarnings.size },
            details = flagged.joinToString("\n\n") { card ->
                "${card.title}\n" + card.visualWarnings.mapIndexed { i, note -> "${i + 1}. $note" }.joinToString("\n")
            }
        )
    }
    internal fun exportVideoEpisode(cards: List<Card>): String {
        val summary = episodeWarningSummary(cards)
        val preflight = if(summary.flaggedScenes == 0) {
            "FLOW VISUAL PREFLIGHT: No text-based continuity warnings detected. Verify every first-frame image manually."
        } else {
            "FLOW VISUAL PREFLIGHT: ${summary.flaggedScenes} scene(s), ${summary.warningCount} warning(s). Review first-frame images before use.\n" +
                summary.details
        }
        return "=== FLOW VISUAL PREFLIGHT REPORT (NOT PART OF VIDEO PROMPTS) ===\n" +
            preflight + "\n=== END PREFLIGHT REPORT ===\n\n" +
            "=== VIDEO COMMANDS: COPY EACH SCENE BLOCK SEPARATELY ===\n" + join(cards)
    }
    fun join(cards: List<Card>) = cards.joinToString("\n\n") { "=== ${it.title} ===\n${it.command}" }
}
