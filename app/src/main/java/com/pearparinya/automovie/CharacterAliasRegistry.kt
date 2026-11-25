package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** User-confirmed names scoped to one story; an alias cannot point to two identities. */
internal object CharacterAliasRegistry {
    private const val piaName = "นายเปีย บ้านคลองขุด"
    private const val presenterPattern = "(?:Nai +)?Pia +Ban +Khlong +Khud"
    const val token = "(?:(?i:$presenterPattern)|[A-Za-z][A-Za-z0-9_-]*)"
    private val presenterPhrase = Regex("(?<![A-Za-z0-9_-])$presenterPattern(?![A-Za-z0-9_-])", RegexOption.IGNORE_CASE)
    private val placePhrase = Regex("(?<![A-Za-z0-9_-])(in|at|from|near|to) +(Ban +Khlong +Khud)(?![A-Za-z0-9_-])", RegexOption.IGNORE_CASE)
    private fun validAlias(alias: String) = acceptedAlias.matches(alias) || presenterPhrase.matches(alias)

    private val acceptedAlias = Regex("[A-Za-z][A-Za-z0-9_-]{0,80}")
    private fun key(alias: String)=alias.trim().lowercase(Locale.ROOT)
    fun identityId(name: String, master: String): String =
        java.util.UUID.nameUUIDFromBytes((name+"\n"+master.lowercase(Locale.ROOT)).toByteArray(Charsets.UTF_8)).toString()
    fun add(registry: JSONObject, alias: String, name: String, master: String) {
        require(validAlias(alias))
        require(name.isNotBlank() && master.isNotBlank())
        val entries=registry.optJSONArray("entries") ?: JSONArray().also { registry.put("entries",it) }
        for(i in 0 until entries.length()) {
            val item=entries.getJSONObject(i)
            if(key(item.getString("alias"))==key(alias)) {
                require(item.getString("name")==name && item.getString("master").equals(master,true)) { "ชื่อนี้เคยจับคู่กับตัวละครอื่นแล้ว" }
                return
            }
        }
        entries.put(JSONObject().put("alias",alias).put("name",name).put("master",master).put("character_id",identityId(name,master)))
    }
    fun read(folder: File): JSONObject = File(folder,"character_aliases.json").let {
        if(it.isFile) GeminiStoryFiles.read(it) else JSONObject().put("entries",JSONArray())
    }
    fun save(folder: File, registry: JSONObject) = GeminiStoryFiles.write(File(folder,"character_aliases.json"),registry)
    /**
     * Normalize all registered cast aliases, not a hard-coded list of names.
     * A name must uniquely identify one (Thai name, MASTER) pair across the
     * story and must be present exactly once in the visible scene.
     * Conflicting confirmed aliases are never silently reassigned.
     */
    fun applyRegistered(scene: JSONObject, definitions: List<JSONObject>, confirmed: JSONObject) {
        val people=scene.optJSONArray("characters") ?: return
        val entries=confirmed.optJSONArray("entries") ?: JSONArray()
        val all=mutableListOf<Triple<String,String,String>>()
        for(definition in definitions) {
            val alias=definition.optString("english_name").trim()
            val name=definition.optString("name")
            val master=definition.optString("master")
            if(validAlias(alias) && name.isNotBlank() && master.isNotBlank())
                all.add(Triple(alias,name,master))
        }
        for(i in 0 until entries.length()) {
            val entry=entries.getJSONObject(i)
            val alias=entry.optString("alias").trim()
            if(validAlias(alias))
                if(!entry.has("character_id") || entry.optString("character_id")==identityId(entry.optString("name"),entry.optString("master")))
                    all.add(Triple(alias,entry.optString("name"),entry.optString("master")))
        }
        val grouped=all.groupBy { key(it.first) }
        val original=scene.optString("flow_action")
        val lead=definitions.filter { it.optString("name")==piaName }.singleOrNull()
        val visibleLead=lead!=null && lead.optString("master").isNotBlank() &&
            (0 until people.length()).count { i ->
                val actor=people.getJSONObject(i)
                actor.optString("name")==piaName && actor.optString("master").equals(lead.optString("master"),true)
            }==1
        val actionText=placePhrase.replace(original) { it.groupValues[1]+" บ้านคลองขุด" }.let { text ->
            if(visibleLead) presenterPhrase.replace(text,piaName) else text
        }
        for((alias,rows) in grouped) {
            val identities=rows.map { it.second to it.third.lowercase(Locale.ROOT) }.distinct()
            if(identities.size>1 && Regex("(?<![A-Za-z0-9_-])"+Regex.escape(alias)+"(?![A-Za-z0-9_-])",RegexOption.IGNORE_CASE).containsMatchIn(actionText))
                throw IllegalArgumentException("ชื่ออังกฤษ '$alias' ซ้ำกันระหว่างตัวละคร กรุณาตรวจทะเบียนชื่อและ MASTER ก่อนสร้างซีน")
        }
        val valid=grouped.values.mapNotNull { rows ->
            val identities=rows.map { it.second to it.third.lowercase(Locale.ROOT) }.distinct()
            if(identities.size!=1) null else rows.first()
        }
        var action=actionText
        for((alias,name,master) in valid.sortedByDescending { it.first.length }) {
            val matching=(0 until people.length()).map { people.getJSONObject(it) }.filter {
                it.optString("name")==name && it.optString("master").equals(master,true)
            }
            if(matching.size!=1) continue
            if(definitions.count { it.optString("name")==name && it.optString("master").equals(master,true) }!=1) continue
            val pattern=Regex("(?<![A-Za-z0-9_-])"+Regex.escape(alias)+"(?![A-Za-z0-9_-])",RegexOption.IGNORE_CASE)
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
            if(!validAlias(alias)) continue
            val matching=(0 until people.length()).map { people.getJSONObject(it) }.filter {
                it.optString("name")==item.optString("name") &&
                    it.optString("master").equals(item.optString("master"),true)
            }
            if(matching.size!=1) continue
            val pattern=Regex("(?<![A-Za-z0-9_-])"+Regex.escape(alias)+"(?![A-Za-z0-9_-])",RegexOption.IGNORE_CASE)
            action=pattern.replace(action) { matching.single().getString("name") }
        }
        if(scene.has("flow_action")) scene.put("flow_action",action)
    }
}
