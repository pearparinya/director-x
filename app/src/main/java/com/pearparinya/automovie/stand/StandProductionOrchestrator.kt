package com.pearparinya.automovie.stand

interface StandRemoteAdapter {
    val option: StandProviderOption
    fun verifyCredential()
    fun submit(jobId: String, kind: StandMediaKind): String
    fun completed(remoteId: String): Boolean
}
class StandProductionOrchestrator(private val masters: StandMasterStore,
    private val jobs: StandProductionJobStore, private val adapters: List<StandRemoteAdapter>) {
    fun advance(project: StandProject, jobId: String, kind: StandMediaKind,
                preferred: String? = null): StandMediaCheckpoint {
        var current = jobs.load(project,jobId)
        if (current?.state == StandSubmissionState.COMPLETED ||
            current?.state == StandSubmissionState.SUBMITTING) return requireNotNull(current)
        if (current?.canPoll() == true) {
            val pending = requireNotNull(current)
            val adapter = adapters.single { it.option.id == pending.providerId }
            if (!adapter.completed(requireNotNull(pending.remoteId))) return pending
            val done = pending.copy(state = StandSubmissionState.COMPLETED)
            jobs.transition(project,jobId,pending,done)
            return done
        }
        StandMediaPipeline.requireReady(masters)
        val selected = StandProviderSelection.select(kind,adapters.map { it.option },preferred)
        val adapter = adapters.single { it.option.id == selected.id }
        adapter.verifyCredential()
        if (current == null) {
            current = StandMediaCheckpoint(selected.id,StandSubmissionState.NOT_SUBMITTED)
            jobs.transition(project,jobId,null,current)
        }
        val submitting = StandMediaCheckpoint(selected.id,StandSubmissionState.SUBMITTING)
        jobs.transition(project,jobId,current,submitting)
        // An ambiguous network failure leaves SUBMITTING durable; never retry blindly.
        val remote = adapter.submit(jobId,kind)
        val pending = StandMediaCheckpoint(selected.id,StandSubmissionState.REMOTE_PENDING,remote)
        jobs.transition(project,jobId,submitting,pending)
        return pending
    }
}
