package com.pearparinya.automovie.stand

enum class StandMediaKind { IMAGE, VIDEO, VOICE }
data class StandProviderOption(val id: String, val capabilities: Set<StandMediaKind>,
    val credentialAvailable: Boolean, val enabled: Boolean = true)
object StandProviderSelection {
    fun select(kind: StandMediaKind, options: List<StandProviderOption>, preferred: String? = null): StandProviderOption {
        require(options.map { it.id }.distinct().size == options.size)
        val eligible = options.filter { it.enabled && it.credentialAvailable && kind in it.capabilities }
        return eligible.firstOrNull { it.id == preferred } ?: eligible.firstOrNull()
            ?: error("No configured provider for " + kind.name)
    }
}
enum class StandSubmissionState { NOT_SUBMITTED, SUBMITTING, REMOTE_PENDING, COMPLETED, FAILED_BEFORE_SUBMISSION }
data class StandMediaCheckpoint(val providerId: String, val state: StandSubmissionState, val remoteId: String? = null) {
    init {
        require(providerId.isNotBlank())
        require(state != StandSubmissionState.REMOTE_PENDING || !remoteId.isNullOrBlank())
    }
    fun canSubmit(): Boolean = state == StandSubmissionState.NOT_SUBMITTED ||
        state == StandSubmissionState.FAILED_BEFORE_SUBMISSION
    fun canPoll(): Boolean = state == StandSubmissionState.REMOTE_PENDING
}
