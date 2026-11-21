package com.pearparinya.automovie

/**
 * Deterministic, non-destructive dialogue planning for an eight-second shot.
 * This is a preflight estimate, not a guarantee of a generated voice's duration.
 * The original dialogue and speaker assignments remain the source of truth.
 */
internal object EightSecondDialoguePlanner {
    const val shotSeconds = 8.0
    private const val reservedActionSeconds = 0.8
    private const val speakerHandoffSeconds = 0.35
    private const val thaiCharactersPerSecond = 7.0
    private const val latinWordsPerSecond = 2.5

    data class Turn(val speaker: String, val text: String)
    data class Assessment(
        val estimatedSeconds: Double,
        val availableSpeechSeconds: Double,
        val fits: Boolean,
        val suggestedThaiCharacterBudget: Int,
        val warning: String?
    )

    private fun estimatedSpeechSeconds(text: String): Double {
        val thaiCount = Regex("[\\u0E00-\\u0E7F]").findAll(text).count()
        val other = text.replace(Regex("[\\u0E00-\\u0E7F]"), " ")
        val words = Regex("[\\p{L}\\p{N}]+").findAll(other).count()
        val pauses = Regex("[,，;；.!?。！？…]+").findAll(text).count() * 0.18
        return thaiCount / thaiCharactersPerSecond + words / latinWordsPerSecond + pauses
    }

    fun assess(turns: List<Turn>): Assessment {
        val spoken = turns.filter { it.text.isNotBlank() }
        val handoffs = spoken.zipWithNext().count { (a, b) -> a.speaker.trim() != b.speaker.trim() }
        val available = (shotSeconds - reservedActionSeconds - handoffs * speakerHandoffSeconds).coerceAtLeast(0.0)
        val estimated = spoken.sumOf { estimatedSpeechSeconds(it.text) }
        val fits = estimated <= available
        val budget = (available * thaiCharactersPerSecond).toInt().coerceAtLeast(0)
        return Assessment(
            estimatedSeconds = estimated,
            availableSpeechSeconds = available,
            fits = fits,
            suggestedThaiCharacterBudget = budget,
            warning = if (fits) null else
                "บทพูดยาวเกินเวลาประมาณการ 8 วินาที: ควรย่อโดยรักษาความหมายและลำดับผู้พูด หรือแยกเป็นซีนใหม่ก่อนส่ง Flow"
        )
    }
}
