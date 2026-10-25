package com.pearparinya.automovie

import org.json.JSONObject

/** One self-contained command per card. Headings are outside the command to avoid image captions. */
internal object SceneCommandCards {
    data class Card(val title: String,val command: String)
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
    fun videos(story: JSONObject,number: Int): List<Card> {
        LandscapeProduction.requirePlan(story)
        val rows=episode(story,number).getJSONArray("scenes")
        return (0 until rows.length()).map { i ->
            val s=rows.getJSONObject(i)
            val spec=FlowSceneSpec(FlowReferencePrompt.people(s).map { FlowPerson(it.getString("name"),it.getString("visual_description"),it.getString("screen_position"),it.getString("outfit"),PerformanceDirection.text(it)) },s.optString("speaker"),s.optString("dialogue"),s.optString("narration"),s.optString("flow_action").ifBlank { s.getString("action") },s.getString("location"),s.getString("start_state"),s.getString("end_state"),8,"9:16",SoundDesign.notes(s))
            val mapped=if(story.optString("content_profile")==AutoDirectorContent.profile) AutoDirectorContent.externalSpec(story,spec) else spec
            Card("ซีน ${s.getInt("scene")} • วิดีโอแนวตั้ง 9:16 • 8 วินาที","TEMPLATE • แนบภาพซีน ${s.getInt("scene")} ที่ตรวจ MASTER ชุด และตำแหน่งผู้พูดแล้วก่อนใช้\n"+FlowScenePrompt.build(mapped)+"\n"+ChannelCatalog.genre(story))
        }
    }
    fun join(cards: List<Card>) = cards.joinToString("\n\n") { "=== ${it.title} ===\n${it.command}" }
}
