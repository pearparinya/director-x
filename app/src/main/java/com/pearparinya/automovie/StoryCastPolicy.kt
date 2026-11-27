package com.pearparinya.automovie

internal object StoryCastPolicy {
    const val minimum = 2
    const val direction = "Plan the complete dramatic arc before scenes: setup, conflict, escalation, turning point, climax and resolution. Give at least two distinct story-relevant characters meaningful goals, relationships and actions that affect the outcome. Keep the selected protagonist central without making them the sole actor. Never invent an unregistered MASTER or add a filler character just for the poster."
    fun requireCast(ids: Collection<String>) {
        require(ids.filter { it.isNotBlank() }.distinct().size >= minimum) {
            "ละครต้องมีตัวละครสำคัญอย่างน้อย 2 ตัว กรุณาเลือก MASTER เพิ่มตามบทบาทก่อนเขียนบท"
        }
    }
}
