package com.pearparinya.automovie

/** Only reuse a user-approved, single-character frame for the identical person, day and outfit. */
internal object OutfitReferencePolicy {
    data class Candidate(val sceneId: String, val master: String, val day: String, val outfit: String,
                         val approved: Boolean, val people: Int)
    fun choose(candidates: List<Candidate>, targetScene: String, master: String, day: String, outfit: String): Candidate? {
        if (master.isBlank() || day.isBlank() || outfit.isBlank()) return null
        return candidates.firstOrNull {
            it.approved && it.people == 1 && it.sceneId != targetScene &&
                it.master.equals(master, true) && it.day == day && it.outfit == outfit
        }
    }
}
