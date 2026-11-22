package com.pearparinya.automovie

import org.json.JSONObject
import java.util.Locale

/** Suggestions only. Never confirms an identity or changes a screenplay. */
internal object CharacterIdentitySuggestions {
    private val honorific=Regex("^(?:(?:mr|mrs|ms|miss|dr|prof)\\.?\\s+)+",RegexOption.IGNORE_CASE)
    fun normalized(value: String): String =
        honorific.replace(value.trim(),"").trim().lowercase(Locale.ROOT)

    fun suggested(alias: String, people: List<JSONObject>, definitions: List<JSONObject>): List<JSONObject> {
        val target=normalized(alias)
        if(target.isBlank()) return emptyList()
        return people.filter { person ->
            val name=person.optString("name")
            val master=person.optString("master")
            val matching=definitions.filter {
                it.optString("name")==name && it.optString("master").equals(master,true)
            }
            matching.size==1 &&
                definitions.count { it.optString("name")==name }==1 &&
                listOf(matching.single().optString("english_name"),name)
                    .any { normalized(it)==target }
        }.distinctBy { it.optString("name") to it.optString("master").lowercase(Locale.ROOT) }
    }
}
