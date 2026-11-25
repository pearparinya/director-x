package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** A bounded record of actual scene fields, not a claim that continuity is correct. */
internal object StoryContinuityLedger {
    private const val omitted = "Earlier entries omitted because the ledger exceeded its size limit;"
    fun promptContext(ledger: String, past: String, previous: String, compact: Boolean): String {
        val header = "ESTABLISHED SCENE LEDGER (source excerpts, not an independent continuity verdict):\n"
        val guidance = "\nContinue established events, exact outfits and end states; do not repeat prior dialogue."
        return if (compact && !ledger.startsWith(omitted)) header + ledger + guidance
        else header + ledger + "\nActual previous episode events/dialogue (do not repeat):\n" + past +
            "\nCurrent episode prior scenes (continue state/outfits):\n" + previous
    }
    fun summarize(episodes: JSONArray, current: JSONArray, limit: Int = 24000): String {
        val entries = mutableListOf<String>()
        fun addRows(label: String, rows: JSONArray) {
            for (i in 0 until rows.length()) {
                val scene = rows.optJSONObject(i) ?: continue
                val people = scene.optJSONArray("characters") ?: JSONArray()
                val cast = (0 until people.length()).mapNotNull { n -> people.optJSONObject(n) }.joinToString(", ") { p ->
                    listOf(p.optString("name"), p.optString("master"), p.optString("outfit"), p.optJSONObject("performance")?.optString("emotional_arc").orEmpty(), p.optJSONObject("performance")?.toString().orEmpty()).joinToString(" / ")
                }
                val fields = listOf("story_day", "story_time", "continuity_group", "location", "start_state", "action", "end_state", "speaker", "dialogue", "dialogue_turns", "narration", "props")
                entries += "$label SC ${scene.optInt("scene", i + 1)}: " + fields.mapNotNull { key -> scene.opt(key)?.toString()?.takeIf { it.isNotBlank() }?.let { "$key=$it" } }.joinToString("; ") + "; cast=$cast"
            }
        }
        for (i in 0 until episodes.length()) {
            val ep = episodes.optJSONObject(i) ?: continue
            addRows("EP ${ep.optInt("ep", i + 1)}", ep.optJSONArray("scenes") ?: JSONArray())
        }
        addRows("CURRENT EP", current)
        val result = entries.joinToString("\n")
        return if (result.length <= limit) result else "Earlier entries omitted because the ledger exceeded its size limit; consult the full outline and source scenes.\n" + result.takeLast(limit)
    }
}
