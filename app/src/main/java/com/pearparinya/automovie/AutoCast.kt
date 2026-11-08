package com.pearparinya.automovie

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Freeze AI casting before scene generation; retries reuse the same names and original bytes. */
internal object AutoCast {
    /** Send only casting information; keep original files, metadata and hashes for local verification. */
    fun promptCatalog(catalog: JSONArray): JSONArray = JSONArray().apply {
        val fields=listOf("id","display_name","character_age","character_gender","casting_description")
        for(i in 0 until catalog.length()) {
            val source=catalog.getJSONObject(i); val compact=JSONObject()
            fields.forEach { field -> if(source.has(field)) compact.put(field,source.get(field)) }
            put(compact)
        }
    }
    fun schema() = JSONObject("""{"type":"object","properties":{"story_concept":{"type":"string"},"cast":{"type":"array","items":{"type":"object","properties":{"library_id":{"type":"string"},"name":{"type":"string"},"english_name":{"type":"string"},"role":{"type":"string"},"romance_role":{"type":"string"}},"required":["library_id","name","role"]}}},"required":["story_concept","cast"]}""")
    fun validate(value: JSONObject, catalog: JSONArray) {
        require(value.optString("story_concept").isNotBlank()) { "ขาดแนวคิดเรื่อง" }
        val cast=value.getJSONArray("cast"); require(cast.length() in 1..8) { "เลือกนักแสดง 1–8 คนตามบท" }
        val ids=(0 until catalog.length()).map { catalog.getJSONObject(it).getString("id") }.toSet()
        val names=mutableSetOf<String>(); val englishNames=mutableSetOf<String>(); val used=mutableSetOf<String>()
        for(i in 0 until cast.length()) {
            val p=cast.getJSONObject(i)
            require(p.getString("library_id") in ids && used.add(p.getString("library_id"))) { "MASTER ไม่อยู่ในคลังหรือเลือกซ้ำ" }
            val name=p.getString("name").trim()
            val english=p.optString("english_name").trim()
            if(english.isNotEmpty()) require(Regex("[A-Za-z][A-Za-z-]{1,40}").matches(english) && englishNames.add(english.lowercase())) { "ชื่ออังกฤษต้องไม่ซ้ำและเป็นอักษรอังกฤษ" }
            require(name.isNotBlank() && names.add(name) && p.getString("role").isNotBlank()) { "ตั้งชื่อใหม่ไม่ซ้ำ พร้อมระบุบทบาท" }
        }
    }
    fun requirePresenter(value: JSONObject, presenterId: String) {
        if(presenterId.isBlank()) return
        val cast=value.getJSONArray("cast")
        require((0 until cast.length()).any { cast.getJSONObject(it).optString("library_id")==presenterId }) { "ต้องมี MASTER ผู้ดำเนินเรื่องที่เลือก" }
    }
    fun validateGenre(value: JSONObject, catalog: JSONArray, category: String) {
        val olderMan=category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง")
        if(!olderMan && !category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) return
        val cast=value.getJSONArray("cast")
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }.associateBy { it.getString("id") }
        fun lead(kind: String, gender: String, ages: IntRange): JSONObject {
            val matches=(0 until cast.length()).map { cast.getJSONObject(it) }.filter { it.optString("romance_role")==kind }
            require(matches.size==1) { "ระบุ romance_role=$kind ให้คู่หลักคนเดียว" }
            val row=rows[matches.single().getString("library_id")]!!
            require(row.optString("character_gender")==gender && row.optInt("character_age") in ages) { "คู่หลัก $kind ต้องเป็น $gender อายุ $ages ตาม MASTER" }
            return row
        }
        val man=if(olderMan) lead("older_man","man",45..60) else lead("younger_man","man",25..30)
        val woman=if(olderMan) lead("younger_woman","woman",25..35) else lead("older_woman","woman",45..55)
        require(if(olderMan) man.getInt("character_age")-woman.getInt("character_age")>=15 else woman.getInt("character_age")-man.getInt("character_age")>=15) { "คู่หลักต้องอายุห่างอย่างน้อย 15 ปี" }
    }
    fun recentCasts(base: File, current: File): JSONArray {
        val casts=base.listFiles().orEmpty().filter { it.isDirectory && it.canonicalFile!=current.canonicalFile }.mapNotNull { project ->
            // The project directory timestamp may not change when an existing casting file is written.
            val files=listOf(File(project,"gemini_job/casting.json"),File(project,"story.json"))
            files.mapNotNull fileLoop@ { file ->
                if(!file.isFile) return@fileLoop null
                val value=runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@fileLoop null
                val cast=value.optJSONArray("cast") ?: value.optJSONArray("supporting_characters") ?: return@fileLoop null
                val ids=(0 until cast.length()).mapNotNull { cast.optJSONObject(it)?.optString("library_id")?.takeIf(String::isNotBlank) }.distinct()
                if(ids.isEmpty()) null else file.lastModified() to ids
            }.maxByOrNull { it.first }
        }.sortedByDescending { it.first }.take(5)
        return JSONArray(casts.map { JSONArray(it.second) })
    }

    /** New jobs prefer a usable pool excluding the last cast. Old jobs keep their saved pool. */
    fun rotationPool(catalog: JSONArray, recent: JSONArray, category: String, seed: String): JSONArray {
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }
        val history=(0 until recent.length()).mapNotNull { recent.optJSONArray(it) }.map { a ->
            (0 until a.length()).map { a.optString(it) }.toSet()
        }
        if(history.isEmpty()) return JSONArray(rows.shuffled(kotlin.random.Random(seed.hashCode())))
        val last=history.first()
        val fresh=rows.filter { it.optString("id") !in last }
        fun preservesEligibility(candidates: List<JSONObject>): Boolean {
            if(candidates.size<2) return false
            for(gender in listOf("man","woman"))
                if(rows.any { it.optString("character_gender")==gender } && candidates.none { it.optString("character_gender")==gender }) return false
            if(category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) {
                val men=candidates.filter { it.optString("character_gender")=="man" && it.optInt("character_age") in 25..30 }
                val women=candidates.filter { it.optString("character_gender")=="woman" && it.optInt("character_age") in 45..55 }
                if(men.none { man -> women.any { woman -> woman.optInt("character_age")-man.optInt("character_age")>=15 } }) return false
            }
            if(category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง")) {
                val men=candidates.filter { it.optString("character_gender")=="man" && it.optInt("character_age") in 45..60 }
                val women=candidates.filter { it.optString("character_gender")=="woman" && it.optInt("character_age") in 25..35 }
                if(men.none { man -> women.any { woman -> man.optInt("character_age")-woman.optInt("character_age")>=15 } }) return false
            }
            return true
        }
        val candidates=if(preservesEligibility(fresh)) fresh else rows
        // Prefer lower frequency and older use across all five known casts; vary ties per story.
        val random=kotlin.random.Random(seed.hashCode())
        val ordered=candidates.shuffled(random).sortedWith(compareBy<JSONObject> { row ->
            val id=row.optString("id"); history.count { id in it }
        }.thenByDescending { row ->
            val id=row.optString("id"); val lastUse=history.indexOfFirst { id in it }; if(lastUse<0) Int.MAX_VALUE else lastUse
        })
        return JSONArray(ordered)
    }
    /** Prefer a fresh pool, but preserve the complete catalog if there are too few choices. */
    fun candidatePool(catalog: JSONArray, recent: JSONArray, category: String): JSONArray {
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }
        val last=recent.optJSONArray(0) ?: return catalog
        val used=(0 until last.length()).map { last.optString(it) }.toSet()
        val fresh=rows.filter { it.optString("id") !in used }
        if(fresh.size<8) return catalog
        if(category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) {
            val men=fresh.filter { it.optString("character_gender")=="man" && it.optInt("character_age") in 25..30 }
            val women=fresh.filter { it.optString("character_gender")=="woman" && it.optInt("character_age") in 45..55 }
            if(men.none { man -> women.any { woman -> woman.optInt("character_age")-man.optInt("character_age")>=15 } }) return catalog
        }
        if(category.startsWith("รักต่างวัย | หนุ่มรุ่นใหญ่กับสาวรุ่นน้อง")) {
            val men=fresh.filter { it.optString("character_gender")=="man" && it.optInt("character_age") in 45..60 }
            val women=fresh.filter { it.optString("character_gender")=="woman" && it.optInt("character_age") in 25..35 }
            if(men.none { man -> women.any { woman -> man.optInt("character_age")-woman.optInt("character_age")>=15 } }) return catalog
        }
        // Preserve gender coverage when a fresh pool would eliminate an existing group.
        for(gender in listOf("man","woman")) if(rows.any { it.optString("character_gender")==gender } && fresh.none { it.optString("character_gender")==gender }) return catalog
        return JSONArray(fresh)
    }
    fun validateVariety(value: JSONObject, catalog: JSONArray, recent: JSONArray) {
        validate(value,catalog)
        // Variety is a preference; a suitable previously used pairing remains valid.
    }
    fun apply(value: JSONObject, catalog: JSONArray, project: File): JSONArray {
        validate(value,catalog)
        val result=JSONArray(); val cast=value.getJSONArray("cast")
        for(i in 0 until cast.length()) {
            val p=cast.getJSONObject(i); val id=p.getString("library_id")
            val row=(0 until catalog.length()).map { catalog.getJSONObject(it) }.first { it.getString("id")==id }
            val source=File(project,"master_candidates/"+row.getString("file"))
            require(source.isFile && MasterLibrary.digest(source.readBytes())==row.getString("sha256")) { "ขาด MASTER ต้นฉบับของ $id" }
            val key="support_%08x".format(id.substringAfter('_').toInt())
            val target=File(project,"original_masters/$key.master"); target.parentFile!!.mkdirs()
            if(!target.isFile || MasterLibrary.digest(target.readBytes())!=row.getString("sha256")) {
                val pending=File(target.parentFile,target.name+".pending"); source.copyTo(pending,overwrite=true); check(pending.renameTo(target))
            }
            result.put(JSONObject().put("name",p.getString("name").trim()).put("master","${key.uppercase()}_MASTER.png")
                .put("english_name",p.optString("english_name").trim()).put("auto_cast",true).put("library_id",id).put("role",p.getString("role"))
                .put("romance_role",p.optString("romance_role")).put("character_age",row.optInt("character_age",0)).put("age_basis",row.optString("age_basis"))
                .put("visual_description",MasterLibrary.description(id)+(if(row.optInt("character_age")>0) " Assigned fictional character age: ${row.getInt("character_age")} years. Keep this age consistent throughout the story." else "")+" Reference appearance: "+row.optString("casting_description")))
        }
        return result
    }
}
