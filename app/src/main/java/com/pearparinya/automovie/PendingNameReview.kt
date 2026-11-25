package com.pearparinya.automovie

import org.json.JSONObject

/** Collect the same unknown action names the scene validator detects, without changing the saved batch. */
internal object PendingNameReview {
    data class Item(val scene: Int, val alias: String, val action: String, val people: List<JSONObject>, val ep: Int = 0)
    data class Group(val alias: String, val items: List<Item>, val people: List<JSONObject>) {
        val scenes get() = items.map { it.scene }.distinct().sorted()
        val locations get() = items.map { "EP ${it.ep} / ซีน ${it.scene}" }.distinct()
    }
    fun groups(items: List<Item>, definitions: List<JSONObject>): List<Group> =
        items.groupBy { it.alias.lowercase(java.util.Locale.ROOT) }.values.map { rows ->
            val candidates=definitions.filter { d ->
                val name=d.optString("name")
                val master=d.optString("master")
                name.isNotBlank() && master.isNotBlank() &&
                    definitions.count { it.optString("name")==name && it.optString("master").equals(master,true) }==1 &&
                    rows.all { row -> row.people.count { it.optString("name")==name && it.optString("master").equals(master,true) }==1 }
            }
            Group(rows.first().alias,rows,candidates)
        }
    fun collectPending(folder: java.io.File, spec: JSONObject, registry: JSONObject): List<Item> =
        folder.listFiles().orEmpty().sortedBy { it.name }.flatMap { file ->
            val match=Regex("EP([0-9]+)_[0-9]+[.]json[.]pending[.]json").matchEntire(file.name)
            if(match==null) emptyList() else collect(GeminiStoryFiles.read(file),spec,registry).map { it.copy(ep=match.groupValues[1].toInt()) }
        }
    fun collect(batch: JSONObject, spec: JSONObject, registry: JSONObject): List<Item> {
        val scenes=batch.getJSONArray("scenes")
        val definitions=FlowCast.definitions(spec)
        val result=mutableListOf<Item>()
        for(i in 0 until scenes.length()) {
            val scene=JSONObject(scenes.getJSONObject(i).toString())
            CharacterAliasRegistry.applyRegistered(scene,definitions,registry)
            val cast=scene.optJSONArray("characters") ?: continue
            val people=(0 until cast.length()).map { cast.getJSONObject(it) }
            for(person in people) {
                val match=definitions.filter { it.optString("name")==person.optString("name") && it.optString("master").equals(person.optString("master"),true) }.singleOrNull()
                val english=match?.optString("english_name").orEmpty()
                if(english.isNotBlank()) person.put("english_name",english) else person.remove("english_name")
            }
            val flow=ProductionCommandCheck.spec(scene)
            for(alias in FlowScenePrompt.unknownActionNames(flow))
                result.add(Item(scene.optInt("scene"),alias,flow.action,people))
        }
        return result
    }
}
