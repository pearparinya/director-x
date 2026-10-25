package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Exact garment/accessory continuity across batches, cuts, absences and episode boundaries. */
internal object AutoDirectorWardrobe {
    data class Shot(val ep: Int, val scene: JSONObject)
    private val labels=listOf("Top","Bottom","Shoes","Accessories")
    private fun normalized(value: String)=value.trim().lowercase().replace(Regex("\\s+")," ")
    fun parts(outfit: String): Map<String,String> {
        val matches=Regex("(?i)\\b(Top|Bottom|Shoes|Accessories)\\s*:").findAll(outfit).toList()
        require(matches.size==4 && matches.map { it.groupValues[1].lowercase() }.distinct().size==4) { "ต้องระบุ Top / Bottom / Shoes / Accessories อย่างละหนึ่งส่วน" }
        return matches.mapIndexed { i,m ->
            val value=outfit.substring(m.range.last+1,matches.getOrNull(i+1)?.range?.first ?: outfit.length).trim().trimEnd(';').trim()
            require(value.isNotBlank() && !Regex("(?i)^(same( as (before|previous))?|เหมือนเดิม|ตามเดิม|เช่นเดิม|[-.]+)$").matches(value)) { "ต้องเขียนรายละเอียดชุดและเครื่องประดับเต็ม ห้ามอ้างเหมือนฉากก่อน" }
            labels.first { it.equals(m.groupValues[1],true) } to value
        }.toMap()
    }
    fun shots(story: JSONObject): List<Shot> {
        val eps=story.getJSONArray("episodes")
        return (0 until eps.length()).flatMap { i -> val ep=eps.getJSONObject(i); val scenes=ep.optJSONArray("scenes") ?: return@flatMap emptyList()
            (0 until scenes.length()).map { Shot(ep.getInt("ep"),scenes.getJSONObject(it)) }
        }
    }
    fun batch(ep: Int, scenes: JSONArray): List<Shot> = (0 until scenes.length()).map { Shot(ep,scenes.getJSONObject(it)) }
    private fun keys(shot: Shot, person: JSONObject): List<String> {
        val scene=shot.scene; val id=FlowCast.id(person) ?: person.optString("name")
        val day=normalized(scene.optString("story_day"))
        val scope=listOf(id,day)
        val explicit=listOf("wardrobe_event","continuity_group").mapNotNull { field ->
            scene.optString(field).trim().takeIf { it.isNotBlank() }?.let { (scope+listOf(field,normalized(it))).joinToString("|") }
        }
        return explicit.ifEmpty { listOf((scope+listOf("location",normalized(scene.optString("location")),normalized(scene.optString("story_time")),if(day.isBlank()) "EP${shot.ep}" else "")).joinToString("|")) }
    }
    fun issues(shots: List<Shot>): List<String> = buildList {
        val ledger=linkedMapOf<String,Pair<Map<String,String>,String>>()
        shots.forEach { shot ->
            if(shot.scene.optJSONArray("characters")==null) return@forEach // General story validation reports missing cast.
            FlowReferencePrompt.people(shot.scene).forEach personLoop@ { person ->
            val where="EP${shot.ep} SC${shot.scene.optInt("scene")} ${person.optString("name")}"
            val parts=runCatching { parts(person.optString("outfit")) }.getOrElse { add("$where: ${it.message}"); return@personLoop }
            val exact=parts.mapValues { normalized(it.value) }
            val keys=keys(shot,person)
            val existing=keys.mapNotNull { ledger[it] }
            val previous=existing.firstOrNull()
            val conflict=existing.firstOrNull { it.first!=exact }
            if(conflict!=null) {
                val changed=labels.filter { conflict.first[it]!=exact[it] }
                add("$where: ชุด/เครื่องประดับไม่ต่อเนื่อง (${changed.joinToString()}) ต้องคัดลอกค่าจาก ${conflict.second} ทุกส่วน ห้ามเปลี่ยนชุดในเหตุการณ์เดิม")
            }
            // Preserve the original authority even after a conflicting row, also across group/event aliases.
            val entry=previous ?: (exact to where)
            keys.forEach { ledger.putIfAbsent(it,entry) }
        } }
    }
    fun catalog(shots: List<Shot>): String {
        val seen=linkedSetOf<String>()
        return shots.flatMap { shot -> FlowReferencePrompt.people(shot.scene).mapNotNull { person ->
            val keys=keys(shot,person)
            val known=!seen.add(keys.joinToString(" / "))
            if(known) null else "${person.optString("name")} | day=${shot.scene.optString("story_day")} | wardrobe_event=${shot.scene.optString("wardrobe_event")} | continuity_group=${shot.scene.optString("continuity_group")} | exact outfit=${person.optString("outfit")}"
        } }.joinToString("\n")
    }
    fun contract(scene: JSONObject): String = """
WARDROBE / ACCESSORY CONTINUITY LOCK
Story day: ${scene.optString("story_day")} | wardrobe_event: ${scene.optString("wardrobe_event")} | continuity_group: ${scene.optString("continuity_group")}
Keep the exact stated Top, Bottom, Shoes and Accessories through all shots in this continuous event, even after a camera-angle change, a character's temporary absence or an episode boundary. Copy the complete descriptions verbatim; never replace them with 'same as before'. Preserve garment color, fabric, neckline, sleeve and hem length, fit, fastenings and patterns. Preserve every accessory's type, count, metal/color, shape and exact wearing position/side. Accessories: none means no earrings, necklace, pendant, rings, watch, bracelet or added hair ornament. Reference-image jewelry is not wardrobe authority; use only the stated current-scene accessory list. Do not invent missing jewelry or garment details. A frame used for continuity must first be checked against these exact clothes and accessories and the original identity references.
During next, retry or image edits, keep clothes and jewelry unchanged. A request to repair wardrobe means restore this exact list, not design a new outfit. Only a scripted, explicit wardrobe transition into a new event may introduce another outfit; never improvise a change while generating a continuous scene.
    """.trimIndent()
}
