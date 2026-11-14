package com.pearparinya.automovie

import org.json.JSONObject

/** Context guides planning; warnings do not silently rewrite an approved wardrobe. */
internal object WardrobeContext {
    const val planning = """CONTEXTUAL WARDROBE: Plan complete Top, Bottom, Shoes and Accessories for EVERY visible character based on that character's role, activity, setting, weather and story period, not the clothes in their identity MASTER. Distinguish active helpers from bystanders and patients from staff; do not assign an occupation or imply everyone is participating. For flood rescue, dress each active helper in practical work clothes and footwear suitable for the depicted water and activity; boots or protective gear only when appropriate and available. For hospital scenes use role-appropriate medical clothing for staff and suitable patient clothing for admitted patients, while visitors wear ordinary clothes. Respect cultural and story context. Choose detailed consistent outfits before the first continuous scene, copy the exact outfit across the existing same-day continuity contract, and never silently replace a user's selected outfit. If a planned outfit conflicts with the situation, flag it for user review rather than inventing a change. Nonhuman characters retain their own natural anatomy and markings."""
    const val image = "CONTEXTUAL WARDROBE: Respect the approved outfit, role and activity; flag unclear or unsuitable clothes or footwear for review. Do not silently change clothing. Preserve the outfit across continuous scenes."
    private val flood=Regex("(?i)flood|floodwater|น้ำท่วม|อุทกภัย")
    private val helping=Regex("(?i)rescu|relief|deliver|carrying|help(?:ing)?|ช่วย|กู้ภัย|แจก|ลำเลียง|ขนของ")
    private val questionable=Regex("(?i)leather jacket|formal (?:gown|dress)|high heels?|ชุดราตรี|เดรส|เสื้อหนัง|แจ็คเก็ตหนัง|แจ็กเก็ตหนัง|รองเท้าส้นสูง")
    fun warnings(scene: JSONObject): List<String> {
        val text=listOf("location","action","flow_action","start_state").joinToString(" ") { scene.optString(it) }
        if(!flood.containsMatchIn(text) || !helping.containsMatchIn(text)) return emptyList()
        val people=scene.optJSONArray("characters") ?: return emptyList()
        return (0 until people.length()).mapNotNull { i ->
            val p=people.optJSONObject(i) ?: return@mapNotNull null
            if(questionable.containsMatchIn(p.optString("outfit"))) "${p.optString("name").ifBlank { "ตัวละคร" }}: ตรวจว่าชุดและรองเท้าเหมาะกับบทบาทในฉากน้ำท่วม หากลงช่วยให้แก้ชุดตามบทก่อนสร้างภาพ" else null
        }
    }
}
