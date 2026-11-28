package com.pearparinya.automovie.stand

data class StandVoiceCue(val request: StandVoiceRequest, val startMs: Long, val durationMs: Long) {
    init { require(startMs >= 0 && durationMs > 0 && startMs <= 8000 && durationMs <= 8000-startMs) }
}
class StandVoiceMapping(private val voices: Map<String,String>) {
    init {
        require(voices.keys == StandCatalog.all.map { it.id }.toSet())
        require(voices.values.all { it.isNotBlank() } && voices.values.toSet().size == 16)
    }
    fun request(line: StandDialogue): StandVoiceRequest {
        require(line.text.isNotBlank())
        require(line.voiceId == StandPersonas.get(line.speakerId).voiceId)
        return StandVoiceRequest(line.speakerId,voices.getValue(line.speakerId),line.text,
            line.speech == StandSpeech.TELEPATHIC,line.lipMovement && line.speech != StandSpeech.TELEPATHIC)
    }
}
object StandVoiceTimeline {
    fun validate(cues: List<StandVoiceCue>): List<StandVoiceCue> {
        val ordered = cues.sortedBy { it.startMs }
        require(ordered.zipWithNext().all { (a,b) -> a.startMs+a.durationMs <= b.startMs })
        require(ordered.all { !it.request.telepathic || !it.request.lipMovement })
        return ordered
    }
}
