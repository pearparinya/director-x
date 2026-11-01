package com.pearparinya.automovie

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Freeze AI casting before scene generation; retries reuse the same names and original bytes. */
internal object AutoCast {
    fun schema() = JSONObject("""{"type":"object","properties":{"story_concept":{"type":"string"},"cast":{"type":"array","items":{"type":"object","properties":{"library_id":{"type":"string"},"name":{"type":"string"},"role":{"type":"string"},"romance_role":{"type":"string"}},"required":["library_id","name","role"]}}},"required":["story_concept","cast"]}""")
    fun validate(value: JSONObject, catalog: JSONArray) {
        require(value.optString("story_concept").isNotBlank()) { "ขาดแนวคิดเรื่อง" }
        val cast=value.getJSONArray("cast"); require(cast.length() in 1..8) { "เลือกนักแสดง 1–8 คนตามบท" }
        val ids=(0 until catalog.length()).map { catalog.getJSONObject(it).getString("id") }.toSet()
        val names=mutableSetOf<String>(); val used=mutableSetOf<String>()
        for(i in 0 until cast.length()) {
            val p=cast.getJSONObject(i)
            require(p.getString("library_id") in ids && used.add(p.getString("library_id"))) { "MASTER ไม่อยู่ในคลังหรือเลือกซ้ำ" }
            val name=p.getString("name").trim()
            require(name.isNotBlank() && names.add(name) && p.getString("role").isNotBlank()) { "ตั้งชื่อใหม่ไม่ซ้ำ พร้อมระบุบทบาท" }
        }
    }
    fun validateGenre(value: JSONObject, catalog: JSONArray, category: String) {
        if(!category.startsWith("รักต่างวัย | หนุ่มรุ่นน้องกับสาวรุ่นพี่")) return
        val cast=value.getJSONArray("cast")
        val rows=(0 until catalog.length()).map { catalog.getJSONObject(it) }.associateBy { it.getString("id") }
        fun lead(kind: String, gender: String, ages: IntRange): JSONObject {
            val matches=(0 until cast.length()).map { cast.getJSONObject(it) }.filter { it.optString("romance_role")==kind }
            require(matches.size==1) { "ระบุ romance_role=$kind ให้คู่หลักคนเดียว" }
            val row=rows[matches.single().getString("library_id")]!!
            require(row.optString("character_gender")==gender && row.optInt("character_age") in ages) { "คู่หลัก $kind ต้องเป็น $gender อายุ $ages ตาม MASTER" }
            return row
        }
        val man=lead("younger_man","man",25..30); val woman=lead("older_woman","woman",45..55)
        require(woman.getInt("character_age")-man.getInt("character_age")>=15) { "คู่หลักต้องอายุห่างอย่างน้อย 15 ปี" }
    }
    fun recentCasts(base: File, current: File): JSONArray {
        val result=JSONArray()
        base.listFiles()?.filter { it.isDirectory && it!=current }?.sortedByDescending { it.lastModified() }?.forEach { project ->
            if(result.length()<5) {
                val file=listOf(File(project,"gemini_job/casting.json"),File(project,"story.json")).firstOrNull { it.isFile }
                val value=file?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                val cast=value?.optJSONArray("cast") ?: value?.optJSONArray("supporting_characters")
                val ids=cast?.let { rows -> (0 until rows.length()).map { rows.getJSONObject(it).optString("library_id") }.filter { it.isNotBlank() }.distinct() }.orEmpty()
                if(ids.isNotEmpty()) result.put(JSONArray(ids))
            }
        }
        return result
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
                .put("auto_cast",true).put("library_id",id).put("role",p.getString("role"))
                .put("romance_role",p.optString("romance_role")).put("character_age",row.optInt("character_age",0)).put("age_basis",row.optString("age_basis"))
                .put("visual_description",MasterLibrary.description(id)+(if(row.optInt("character_age")>0) " Assigned fictional character age: ${row.getInt("character_age")} years. Keep this age consistent throughout the story." else "")+" Reference appearance: "+row.optString("casting_description")))
        }
        return result
    }
}
