package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Three fixed leads plus explicitly registered, separately referenced adult supporting characters. */
internal object FlowCast {
    private val supportingMaster=Regex("^SUPPORT_[A-Fa-f0-9]{8}_MASTER[.](png|jpg|jpeg|webp)$",RegexOption.IGNORE_CASE)
    fun id(person: JSONObject): String? = person.optString("master").takeIf { supportingMaster.matches(it) }?.substringBeforeLast('.')?.replace(Regex("_MASTER$",RegexOption.IGNORE_CASE),"")?.lowercase() ?: StoryIdentity.keyFor(person.optString("name"))
    fun supporting(plan: JSONObject?): List<JSONObject> = plan?.optJSONArray("supporting_characters")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    fun definitions(plan: JSONObject?): List<JSONObject> = (if(supporting(plan).isNotEmpty() && supporting(plan).all { it.optBoolean("auto_cast") }) emptyList() else FlowProductionPack.names.map { (id,name) -> JSONObject().put("name",name).put("master","${id.uppercase()}_MASTER.png").put("visual_description",plan?.optJSONObject("lead_references")?.optJSONObject(id)?.optString("visual_description")?.takeIf { it.isNotBlank() } ?: FlowProductionPack.dna.getValue(id)) })+supporting(plan)
    fun storyRegistry(actors: List<JSONObject>, persisted: JSONArray = JSONArray()): JSONArray = JSONArray().apply {
        val previous = (0 until persisted.length()).map { persisted.getJSONObject(it) }
        val used = mutableSetOf<String>()
        for (actor in actors) {
            val name = actor.getString("name")
            val master = actor.getString("master")
            val row = JSONObject().put("name", name).put("master", master)
                .put("character_id", run {
                    val matches = previous.filter { it.optString("name") == name && it.optString("master").equals(master, true) }
                    require(matches.size <= 1) { "ทะเบียนตัวละครเดิมซ้ำ" }
                    val old = matches.singleOrNull()
                    val oldLibrary = old?.optString("library_id").orEmpty()
                    val newLibrary = actor.optString("library_id")
                    require(oldLibrary.isBlank() || newLibrary.isBlank() || oldLibrary.equals(newLibrary, true)) { "Library ID ไม่ตรงกับทะเบียนเดิม" }
                    require(previous.none { it !== old && newLibrary.isNotBlank() && it.optString("library_id").equals(newLibrary, true) }) { "Library ID เปลี่ยนเจ้าของ" }
                    val stored = old?.optString("character_id").orEmpty()
                    val current = actor.optString("character_id")
                    require(stored.isBlank() || current.isBlank() || stored == current) { "Character ID ไม่ตรงกับทะเบียนเดิม" }
                    val identity = stored.ifBlank { current.ifBlank { CharacterAliasRegistry.identityId(name, master) } }
                    require(used.add(identity)) { "Character ID ซ้ำ" }
                    identity
                })
            val old = previous.singleOrNull { it.optString("name") == name && it.optString("master").equals(master, true) }
            for (key in listOf("library_id", "role", "english_name")) {
                val current = actor.optString(key).takeIf { it.isNotBlank() && !actor.isNull(key) }
                val prior = old?.optString(key)?.takeIf { it.isNotBlank() && !old.isNull(key) }
                (current ?: prior)?.let { row.put(key, it) }
            }
            val library = row.optString("library_id")
            require(library.isBlank() || (0 until length()).none {
                getJSONObject(it).optString("library_id").equals(library, true)
            }) { "Library ID ซ้ำ" }
            put(row)
        }
    }

    fun issues(plan: JSONObject): List<String> = buildList {
        val rows=supporting(plan)
        val limit = if (rows.all { it.optBoolean("auto_cast") }) 15 else 8
        if(rows.size>limit) add("รองรับตัวละครเสริมไม่เกิน $limit คนต่อเรื่อง")
        rows.forEach { p ->
            if(p.optString("name").isBlank() || (StoryIdentity.keyFor(p.optString("name"))!=null && !p.optBoolean("auto_cast")) || !supportingMaster.matches(p.optString("master")) || p.optString("visual_description").isBlank()) add("ข้อมูลตัวละครเสริมต้องมีชื่อ ภาพอ้างอิงแยกรายคน และคำบรรยาย")
        }
        if(rows.map { it.optString("name") }.distinct().size!=rows.size || rows.map { id(it) }.distinct().size!=rows.size) add("ตัวละครเสริมมีชื่อหรือภาพอ้างอิงซ้ำ")
    }
    fun matches(plan: JSONObject, person: JSONObject): Boolean {
        val key=id(person) ?: return false
        val cast=supporting(plan)
        // An automatic cast has its own frozen name-to-MASTER registry. Never
        // fall back to an older lead with the same name and a different image.
        if(cast.isNotEmpty() && cast.all { it.optBoolean("auto_cast") })
            return cast.any { it.optString("name")==person.optString("name") && id(it)==key }
        if(StoryIdentity.keyFor(person.optString("name"))!=null) return StoryIdentity.matchesMaster(person.optString("name"),person.optString("master"))
        return cast.any { it.optString("name")==person.optString("name") && id(it)==key }
    }
}
