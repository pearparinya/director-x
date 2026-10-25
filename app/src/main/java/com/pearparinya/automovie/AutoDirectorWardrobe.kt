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
        if(day.isNotBlank()) return listOf((scope+"daily_wardrobe").joinToString("|"))
        val explicit=listOf("wardrobe_event","continuity_group").mapNotNull { field ->
            scene.optString(field).trim().takeIf { it.isNotBlank() }?.let { (scope+listOf(field,normalized(it))).joinToString("|") }
        }
        return explicit.ifEmpty { listOf((scope+listOf("location",normalized(scene.optString("location")),normalized(scene.optString("story_time")),if(day.isBlank()) "EP${shot.ep}" else "")).joinToString("|")) }
    }
    fun issues(shots: List<Shot>): List<String> = buildList {
        val ledger=linkedMapOf<String,Pair<Map<String,String>,String>>()
        val days=linkedMapOf<String,MutableMap<String,Pair<Map<String,String>,String>>>()
        shots.forEach { shot ->
            if(shot.scene.optJSONArray("characters")==null) return@forEach // General story validation reports missing cast.
            FlowReferencePrompt.people(shot.scene).forEach personLoop@ { person ->
            val where="EP${shot.ep} SC${shot.scene.optInt("scene")} ${person.optString("name")}"
            val parts=runCatching { parts(person.optString("outfit")) }.getOrElse { add("$where: ${it.message}"); return@personLoop }
            val exact=parts.mapValues { normalized(it.value) }
            val day=normalized(shot.scene.optString("story_day"))
            if(day.isBlank()) { add("$where: ขาด story_day สำหรับล็อกชุดรายวัน กรุณาระบุ DAY01 / DAY02 ให้ชัดเจน"); return@personLoop }
            val character=FlowCast.id(person) ?: person.optString("name")
            val previousDays=days.getOrPut(character) { linkedMapOf() }
            if(day !in previousDays) {
                previousDays.values.firstOrNull { old -> old.first["Top"]==exact["Top"] ||
                    (!absentBottom(old.first.getValue("Bottom")) && !absentBottom(exact.getValue("Bottom")) && old.first["Bottom"]==exact["Bottom"]) }?.let { old ->
                    val repeated=listOf("Top","Bottom").filter { old.first[it]==exact[it] && (it!="Bottom" || !absentBottom(exact.getValue(it))) }
                    add("$where: คนละวันต้องวางชุดใหม่ (${repeated.joinToString()} ซ้ำกับ ${old.second}) เปลี่ยนเสื้อและกางเกง/กระโปรงตามวัน ห้ามคัดลอกชุดจากภาพแนบ")
                }
                previousDays[day]=exact to where
            }
            val keys=keys(shot,person)
            val existing=keys.mapNotNull { ledger[it] }
            val previous=existing.firstOrNull()
            val conflict=existing.firstOrNull { it.first!=exact }
            if(conflict!=null) {
                val changed=labels.filter { conflict.first[it]!=exact[it] }
                add("$where: ชุด/เครื่องประดับไม่ต่อเนื่อง (${changed.joinToString()}) ต้องคัดลอกค่าจาก ${conflict.second} ทุกส่วน วันเดียวกันห้ามเปลี่ยนชุด แม้เปลี่ยนสถานที่ กิจกรรม หรือ EP")
            }
            // Preserve the original authority even after a conflicting row, also across group/event aliases.
            val entry=previous ?: (exact to where)
            keys.forEach { ledger.putIfAbsent(it,entry) }
        } }
    }
    private fun absentBottom(value: String): Boolean = Regex("^(none|n/a|not applicable|ไม่มี)(?:[ (—:;-].*)?$").matches(value)
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
DAILY LOCK: Keep the exact stated Top, Bottom, Shoes and Accessories throughout this entire story_day, even if location, activity, time of day, wardrobe_event, continuity_group, framing or EP changes. Copy the complete descriptions verbatim; never replace them with 'same as before'. A different story_day needs a new planned top and trousers/skirt or a new one-piece dress; shoes and accessories may be reused only when explicitly listed. Preserve garment color, fabric, neckline, sleeve and hem length, fit, fastenings and patterns. Preserve every accessory's type, count, metal/color, shape and exact wearing position/side. Accessories: none means no earrings, necklace, pendant, rings, watch, bracelet or added hair ornament. Reference-image jewelry is not wardrobe authority; use only the stated current-scene accessory list. Reference images supply identity, hair and body only: never copy their shirt, blouse, trousers, skirt, dress or outfit into the story. Do not invent missing jewelry or garment details. A frame used for continuity must first be checked against these exact clothes and accessories and the original identity references.
During next, retry or image edits, keep clothes and jewelry unchanged. A request to repair wardrobe means restore this day's exact list, not design a new outfit. Only a clearly identified new story_day permits another planned outfit. A new event or activity within the same day does not unlock clothing.
    """.trimIndent()
}
