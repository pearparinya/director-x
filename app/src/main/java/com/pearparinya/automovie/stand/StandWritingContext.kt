package com.pearparinya.automovie.stand

object StandWritingContext {
    fun build(ownership: StandOwnership, mode: StandWritingMode): String = buildString {
        appendLine("Channel: " + ownership.project.channel.title)
        appendLine("Writing mode: " + mode.name)
        appendLine("Keep stand and owner identities separate. Never transfer ownership during a scene.")
        appendLine("Only stand users see and hear summoned stands. Private telepathy has no lip movement.")
        appendLine(StandContinuityEngine.VISUAL_LOCK)
        ownership.snapshot().toSortedMap().forEach { (id, owner) ->
            val stand = requireNotNull(StandCatalog.find(id))
            val persona = StandPersonas.get(id)
            appendLine(id + " " + stand.name + " | owner: " + owner +
                " | personality: " + persona.personality + " | voice: " + persona.voiceId)
        }
        if (ownership.project.channel == StandChannel.PIA)
            appendLine("PIEROS exclusively owns ST-013. Preserve PIEROS identity across episodes.")
    }
}
