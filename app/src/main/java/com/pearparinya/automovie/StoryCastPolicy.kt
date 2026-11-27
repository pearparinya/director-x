package com.pearparinya.automovie

internal object StoryCastPolicy {
    const val minimum = 2
    const val direction = "Plan the complete dramatic arc before scenes: setup, conflict, escalation, turning point, climax and resolution. Give at least two distinct story-relevant characters meaningful goals, relationships and actions that affect the outcome. Keep the selected protagonist central without making them the sole actor. Ground each turning point in a character goal, an established relationship and an observable consequence. Carry the previous END STATE into the next START STATE across immediate cuts, including props, injuries, posture and emotion. Describe one achievable eight-second action; the still shows its start and the Flow prompt carries that action to its end. Never invent an unregistered MASTER or add a filler character just for the poster."
    fun recommended(scenes: Int): IntRange = when {
        scenes <= 10 -> 2..4
        scenes <= 20 -> 3..6
        scenes <= 30 -> 4..8
        scenes <= 50 -> 5..12
        else -> 6..15
    }
    fun sizingGuide(scenes: Int): String {
        if (scenes <= 0) return "Choose cast size after estimating story length; explain the role of every actor."
        val range = recommended(scenes)
        return "For $scenes scenes aim for ${range.first}–${range.last} meaningful characters when supported by the premise and available MASTERs. This is guidance, not a quota; never pad the cast."
    }
    fun requireCast(ids: Collection<String>) {
        require(ids.filter { it.isNotBlank() }.distinct().size >= minimum) {
            "ละครต้องมีตัวละครสำคัญอย่างน้อย 2 ตัว กรุณาเลือก MASTER เพิ่มตามบทบาทก่อนเขียนบท"
        }
    }
}
