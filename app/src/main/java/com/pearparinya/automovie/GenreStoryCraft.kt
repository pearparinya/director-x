package com.pearparinya.automovie

/** Genre guidance supplements the selected category; it is not a fixed plot. */
internal object GenreStoryCraft {
    fun direction(id: String): String {
        val shared = "STORY CRAFT: Plan the complete arc before scenes. Give each principal character a goal, obstacle, motive and changing relationship. Each scene advances cause and effect with a playable emotional turn; carry its consequences into later episodes. Avoid invented continuity and repetitive cliffhangers."
        val genre = when (id) {
            "cinema_horror", "cinema_curse" -> "HORROR: Establish the fear, supernatural motive and limits. Build dread through environment, sound and selective revelation; vary tension and release. Connect the haunting to character choices and consequences."
            "cinema_fantasy", "cinema_legend", "cinema_time", "cinema_scifi" -> "SPECULATIVE WORLD: Establish the world's rules, power limits, costs and consequences before they matter. Reveal rules through action. Keep the human or nonhuman character's goal and relationships central; do not change rules to solve a crisis."
            "cinema_mystery", "mystery_twist" -> "MYSTERY: Privately plan the true chronology, culprit and motive first. Place traceable clues and plausible alternative interpretations before the reveal. Track who discovers each clue and when; do not let a character know information before learning it."
            "cinema_creature" -> "CREATURE: Define species, distinctive anatomy, markings, habitat, behavior, motive, abilities and limitations. Preserve the registered MASTER. Use traces, sound and reactions to establish its presence; reveal it in a way justified by the story."
            else -> "DRAMA: Give each side a credible motive and distinct voice. Reveal emotions through behavior, choices and consequences; avoid automatic villains and unearned reversals."
        }
        return "$shared\n$genre"
    }
}
