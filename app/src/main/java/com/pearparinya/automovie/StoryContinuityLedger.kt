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
    /** Only actual on-screen appearances in earlier episodes of the current job count. */
    fun returningCast(episodes: JSONArray, support: JSONArray, catalog: JSONArray,
                      synopsis: String, category: String): JSONArray {
        val seen = mutableSetOf<Pair<String,String>>()
        for (e in 0 until episodes.length()) {
            val scenes = episodes.optJSONObject(e)?.optJSONArray("scenes") ?: continue
            for (s in 0 until scenes.length()) {
                val people = scenes.optJSONObject(s)?.optJSONArray("characters") ?: continue
                for (n in 0 until people.length()) {
                    val actor = people.optJSONObject(n) ?: continue
                    seen.add(actor.optString("name") to actor.optString("master"))
                }
            }
        }
        val rows = (0 until catalog.length()).map { catalog.getJSONObject(it) }.associateBy { it.optString("id") }
        val result = JSONArray()
        for (i in 0 until support.length()) {
            val actor = support.getJSONObject(i)
            val name = actor.optString("name")
            val master = actor.optString("master")
            val id = actor.optString("library_id")
            if (name.isBlank() || master.isBlank() || id.isBlank() || (name to master) !in seen) continue
            val row = rows[id] ?: continue
            val score = AutoCast.matchingEvidence(row, synopsis, category, previous = setOf(id))
            result.put(JSONObject().put("library_id",id).put("name",name).put("master",master)
                .put("score",score.sumOf { it.points }).put("continuity_points",score.last().points))
        }
        return result
    }

    /** Record explicit named relationships in episode narration and actions. Dialogue may be hearsay. */
    fun relationships(synopsis: String, scenes: JSONArray, support: JSONArray): JSONArray {
        val byName = (0 until support.length()).map { support.getJSONObject(it) }
            .filter { it.optString("name").isNotBlank() && it.optString("library_id").isNotBlank() }
            .groupBy { it.getString("name") }.filterValues { it.size == 1 }
        val result = JSONArray()
        if (byName.size < 2) return result
        val names = byName.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
        val kin = "พ่อ|แม่|ลูก|สามี|ภรรยา|พี่|น้อง|ญาติ|เพื่อน|คนรัก|คู่ขัดแย้ง|ผู้ช่วยเหลือ|ผู้พิทักษ์|เจ้าของ"
        val statement = Regex("""^(?:($names))\s*เป็น\s*($kin)\s*(?:ของ|กับ)\s*($names)$""")
        val recorded = mutableSetOf<Triple<String,String,String>>()
        fun inspect(text: String, source: String) {
            for (clause in text.split(Regex("""[.!。;\n]+"""))) {
                val match = statement.matchEntire(clause.trim()) ?: continue
                val (subject, role, target) = match.destructured
                val from = byName.getValue(subject).single().getString("library_id")
                val to = byName.getValue(target).single().getString("library_id")
                if (from == to || !recorded.add(Triple(from,role,to))) continue
                result.put(JSONObject().put("subject_id",from).put("relation",role).put("object_id",to)
                    .put("evidence",match.value).put("source",source))
            }
        }
        inspect(synopsis,"synopsis")
        for (i in 0 until scenes.length()) {
            val scene = scenes.getJSONObject(i)
            for (key in listOf("action","narration")) inspect(scene.optString(key),"scene ${scene.optInt("scene",i+1)} $key")
        }
        return result
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
