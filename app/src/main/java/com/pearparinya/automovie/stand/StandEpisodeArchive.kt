package com.pearparinya.automovie.stand

import org.json.JSONObject
import org.json.JSONArray
import android.content.SharedPreferences

class StandEpisodeArchive(private val prefs: SharedPreferences) {
    private fun key(project: StandProject) = "episodes:" + project.channel.id + ":" + project.projectId
    fun history(project: StandProject): List<String> {
        val data = JSONArray(prefs.getString(key(project), "[]"))
        return (0 until data.length()).map { data.getString(it) }
    }
    fun append(project: StandProject, expected: List<String>, narrative: String): Boolean = synchronized(lock) {
        require(narrative.isNotBlank())
        if (history(project) != expected) return@synchronized false
        prefs.edit().putString(key(project), JSONArray(expected + narrative).toString()).commit()
    }
    companion object { private val lock = Any() }
}

object StandAiNarrative {
    fun prompt(story: StandProducedStory, history: List<String>): String = buildString {
        appendLine(StandWritingContext.build(story.ownership, StandWritingMode.AI_KOTLIN))
        appendLine("Write a Thai screenplay for EP " + (history.size + 1) +
            ". Preserve prior events and all owners. Do not alter calculated combat outcomes.")
        history.forEachIndexed { i, text -> appendLine("Prior EP " + (i+1) + ": " + text) }
        story.beats.forEach { appendLine("Scene " + it.scene.number + ": " + it.narrative) }
        appendLine("Return JSON with a single nonempty text field. Do not invent new stand owners.")
    }
    fun schema() = JSONObject().put("type","object")
        .put("properties", JSONObject().put("text", JSONObject().put("type","string")))
        .put("required", JSONArray().put("text"))
}
