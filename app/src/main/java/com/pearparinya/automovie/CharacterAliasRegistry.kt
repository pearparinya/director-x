package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** User-confirmed names scoped to one story; an alias cannot point to two identities. */
internal object CharacterAliasRegistry {
    private fun key(alias: String)=alias.trim().lowercase(Locale.ROOT)
    fun add(registry: JSONObject, alias: String, name: String, master: String) {
        require(Regex("[A-Za-z][A-Za-z0-9_]{1,80}").matches(alias))
        require(name.isNotBlank() && master.isNotBlank())
        val entries=registry.optJSONArray("entries") ?: JSONArray().also { registry.put("entries",it) }
        for(i in 0 until entries.length()) {
            val item=entries.getJSONObject(i)
            if(key(item.getString("alias"))==key(alias)) {
                require(item.getString("name")==name && item.getString("master").equals(master,true)) { "ชื่อนี้เคยจับคู่กับตัวละครอื่นแล้ว" }
                return
            }
        }
        entries.put(JSONObject().put("alias",alias).put("name",name).put("master",master))
    }
    fun read(folder: File): JSONObject = File(folder,"character_aliases.json").let {
        if(it.isFile) GeminiStoryFiles.read(it) else JSONObject().put("entries",JSONArray())
    }
    fun save(folder: File, registry: JSONObject) = GeminiStoryFiles.write(File(folder,"character_aliases.json"),registry)
    /** Trust only an exact English name recorded once in the story cast and its matching visible MASTER. */
    fun applyRegistered(scene: JSONObject, definitions: List<JSONObject>, confirmed: JSONObject) {
        val people=scene.optJSONArray("characters") ?: return
        val confirmedEntries=confirmed.optJSONArray("entries") ?: JSONArray()
        var action=scene.optString("flow_action")
        for(personIndex in 0 until people.length()) {
            val person=people.getJSONObject(personIndex)
            val name=person.optString("name")
            val master=person.optString("master")
            if(name.isBlank() || master.isBlank()) continue
            val exact=definitions.filter { it.optString("name")==name && it.optString("master").equals(master,true) }
            if(exact.size!=1) continue
            val alias=exact.single().optString("english_name").trim()
            if(!Regex("[A-Za-z][A-Za-z0-9_]{1,80}").matches(alias)) continue
            if(definitions.count { it.optString("english_name").trim().equals(alias,true) }!=1) continue
            if((0 until people.length()).count { people.getJSONObject(it).optString("name")==name && people.getJSONObject(it).optString("master").equals(master,true) }!=1) continue
            if((0 until confirmedEntries.length()).map { confirmedEntries.getJSONObject(it) }.any {
                it.optString("alias").equals(alias,true) && (it.optString("name")!=name || !it.optString("master").equals(master,true))
            }) continue
            val pattern=Regex("(?<![A-Za-z0-9_])"+Regex.escape(alias)+"(?![A-Za-z0-9_])",RegexOption.IGNORE_CASE)
            action=pattern.replace(action) { name }
        }
        if(scene.has("flow_action")) scene.put("flow_action",action)
    }
    /** Only normalize when exactly one visible actor matches both the confirmed name and MASTER. */
    fun apply(scene: JSONObject, registry: JSONObject) {
        val people=scene.optJSONArray("characters") ?: return
        val entries=registry.optJSONArray("entries") ?: return
        var action=scene.optString("flow_action")
        for(i in 0 until entries.length()) {
            val item=entries.getJSONObject(i)
            val alias=item.optString("alias")
            if(!Regex("[A-Za-z][A-Za-z0-9_]{1,80}").matches(alias)) continue
            val matching=(0 until people.length()).map { people.getJSONObject(it) }.filter {
                it.optString("name")==item.optString("name") &&
                    it.optString("master").equals(item.optString("master"),true)
            }
            if(matching.size!=1) continue
            val pattern=Regex("(?<![A-Za-z0-9_])"+Regex.escape(alias)+"(?![A-Za-z0-9_])",RegexOption.IGNORE_CASE)
            action=pattern.replace(action) { matching.single().getString("name") }
        }
        if(scene.has("flow_action")) scene.put("flow_action",action)
    }
}
