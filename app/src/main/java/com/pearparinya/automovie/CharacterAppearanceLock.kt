package com.pearparinya.automovie

internal data class CharacterAppearanceLock(val body: Boolean = true, val hair: Boolean = true, val wardrobe: Boolean = true) {
    fun instruction(name: String): String = buildString {
        append("Character: ").append(name).append(". Original MASTER controls facial identity and natural skin tone; never substitute another identity.")
        if (body) append(" Preserve established body proportions and build; do not copy MASTER clothing.")
        if (hair) append(" Preserve established hairstyle, facial hair and hair color unless the story explicitly requires a change.")
        if (wardrobe) append(" Preserve the approved outfit for this scene when attached; otherwise follow the written scene outfit. Never reuse a different outfit merely because it belongs to the same actor.")
    }
}
