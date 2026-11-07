package com.pearparinya.automovie

import org.json.JSONObject

/** Collect the same unknown action names the scene validator detects, without changing the saved batch. */
internal object PendingNameReview {
    data class Item(val scene: Int, val alias: String, val action: String, val people: List<JSONObject>)
    fun collect(batch: JSONObject, spec: JSONObject, registry: JSONObject): List<Item> {
        val scenes=batch.getJSONArray("scenes")
        val definitions=FlowCast.definitions(spec)
        val result=mutableListOf<Item>()
        for(i in 0 until scenes.length()) {
            val scene=JSONObject(scenes.getJSONObject(i).toString())
            CharacterAliasRegistry.apply(scene,registry)
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
