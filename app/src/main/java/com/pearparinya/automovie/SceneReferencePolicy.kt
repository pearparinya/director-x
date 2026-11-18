package com.pearparinya.automovie

import org.json.JSONObject

/** Explicit reference contracts for scene prompts. Image bytes must be attached separately. */
internal object SceneReferencePolicy {
    private val masterId = Regex("^[A-Za-z][A-Za-z0-9_-]{1,63}$")

    fun propInstructions(scene: JSONObject): String {
        val links = scene.optJSONArray("prop_links") ?: return ""
        val rows = mutableListOf<String>()
        for (i in 0 until links.length()) {
            val row = links.optJSONObject(i) ?: continue
            val id = row.optString("prop_master_id").trim().uppercase()
            if (id.isEmpty()) continue
            require(masterId.matches(id)) { "Invalid prop MASTER ID at prop_links[$i]" }
            val item = row.optString("item").trim()
            require(item.isNotEmpty()) { "Missing prop name for MASTER $id" }
            rows.add("PROP MASTER $id: $item — require a verified PROP_MASTER image attachment before generation; do not substitute a character face reference or invent missing object details. Preserve its distinctive silhouette, material, colors and details.")
        }
        return rows.distinct().joinToString("\n")
    }

    fun wardrobeInstructions(scene: JSONObject): String {
        val characters = scene.optJSONArray("characters") ?: return ""
        val rows = mutableListOf<String>()
        for (i in 0 until characters.length()) {
            val person = characters.optJSONObject(i) ?: continue
            val name = person.optString("name").trim()
            val source = person.optString("wardrobe_source").trim().uppercase()
            when (source) {
                "" -> Unit // Legacy scenes retain existing wardrobe behavior.
                "MASTER_OUTFIT" -> rows.add("$name: MASTER_OUTFIT — reproduce clothing and accessories from this character's own attached MASTER.")
                "SCENE_OUTFIT" -> {
                    val outfit = person.optString("outfit").trim()
                    require(outfit.isNotEmpty()) { "Missing scene outfit for $name" }
                    rows.add("$name: SCENE_OUTFIT — use exactly: $outfit. Use character MASTER for identity only, NOT clothing.")
                }
                else -> error("Unsupported wardrobe_source for $name: $source")
            }
        }
        return if (rows.isEmpty()) "" else "PER-CHARACTER WARDROBE SOURCE (overrides generic wardrobe text):\n" + rows.joinToString("\n")
    }
}
