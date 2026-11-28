package com.pearparinya.automovie.stand

data class StandMediaAsset(val standId: String, val ownerId: String, val sha256: String, val bytes: ByteArray)
data class StandVoiceRequest(val speakerId: String, val providerVoiceId: String, val text: String,
    val telepathic: Boolean, val lipMovement: Boolean)
interface StandMediaProvider {
    fun image(prompt: String, references: List<StandMediaAsset>): ByteArray
    fun video(prompt: String, references: List<StandMediaAsset>, startFrame: ByteArray): ByteArray
    fun voice(request: StandVoiceRequest): ByteArray
}
data class StandMediaResult(val image: ByteArray, val video: ByteArray, val voices: List<ByteArray>)
object StandMediaPipeline {
    fun missingMasters(store: StandMasterStore): List<String> =
        StandCatalog.all.map { it.id }.filter { store.verifiedFile(it) == null }

    fun requireReady(store: StandMasterStore) {
        val failures = store.audit().filter { it.status != StandMasterStatus.VERIFIED }
        require(failures.isEmpty()) { failures.joinToString("; ") { it.standId + ": " + it.status.name } }
    }

    fun produceVerified(store: StandMasterStore, request: StandCinematicRequest,
                        voices: List<StandVoiceRequest>, provider: StandMediaProvider): StandMediaResult {
        requireReady(store)
        return produce(request, voices, provider)
    }

    fun assets(request: StandCinematicRequest): List<StandMediaAsset> =
        request.references.map { ref ->
            val bytes = ref.file.readBytes()
            require(StandMasterIntegrity.digest(bytes.inputStream()) == ref.binding.masterSha256) {
                "MASTER changed: " + ref.binding.standId
            }
            StandMediaAsset(ref.binding.standId,ref.binding.ownerId,ref.binding.masterSha256,bytes)
        }

    fun produce(request: StandCinematicRequest, voices: List<StandVoiceRequest>,
                provider: StandMediaProvider): StandMediaResult {
        require(voices.all { it.text.isNotBlank() && it.providerVoiceId.isNotBlank() &&
            (!it.telepathic || !it.lipMovement) })
        val references = assets(request)
        val image = provider.image(request.imagePrompt,references)
        require(image.isNotEmpty())
        val video = provider.video(request.videoPrompt,assets(request),image.copyOf())
        require(video.isNotEmpty())
        val audio = voices.map { provider.voice(it).also { bytes -> require(bytes.isNotEmpty()) } }
        return StandMediaResult(image,video,audio)
    }
}
