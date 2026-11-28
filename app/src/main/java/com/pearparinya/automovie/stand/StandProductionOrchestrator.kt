package com.pearparinya.automovie.stand

interface StandRemoteAdapter {
    val option: StandProviderOption
    fun verifyCredential()
    fun submit(jobId: String, kind: StandMediaKind): String
    fun completed(remoteId: String): Boolean
    fun recover(jobId: String): String? = null
    fun download(remoteId: String, destination: java.io.OutputStream)
}
class StandProductionOrchestrator(private val masters: StandMasterStore,
    private val jobs: StandProductionJobStore, private val adapters: List<StandRemoteAdapter>, private val outputRoot: java.io.File) {
    fun advance(project: StandProject, jobId: String, kind: StandMediaKind,
                preferred: String? = null): StandMediaCheckpoint {
        var current = jobs.load(project,jobId)
        if (current?.state == StandSubmissionState.COMPLETED) return requireNotNull(current)
        if (current?.state == StandSubmissionState.SUBMITTING) {
            val uncertain = requireNotNull(current)
            val adapter = adapters.single { it.option.id == uncertain.providerId }
            val remote = adapter.recover(jobId) ?: return uncertain
            val pending = uncertain.copy(state = StandSubmissionState.REMOTE_PENDING, remoteId = remote)
            jobs.transition(project,jobId,uncertain,pending)
            return pending
        }
        if (current?.canPoll() == true) {
            val pending = requireNotNull(current)
            val adapter = adapters.single { it.option.id == pending.providerId }
            if (!adapter.completed(requireNotNull(pending.remoteId))) return pending
            val identity = org.json.JSONArray(listOf(project.channel.id,project.projectId,jobId)).toString()
            val name = StandMasterIntegrity.digest(identity.byteInputStream())
            check(outputRoot.isDirectory || outputRoot.mkdirs())
            val target = java.io.File(outputRoot,name + "." + kind.name.lowercase())
            val temp = java.io.File.createTempFile("production-", ".tmp",outputRoot)
            try {
                java.io.FileOutputStream(temp).use { stream ->
                    adapter.download(requireNotNull(pending.remoteId),stream)
                    stream.fd.sync()
                }
                require(temp.length() > 0) { "Empty production output" }
                when (kind) {
                    StandMediaKind.IMAGE -> {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(temp.absolutePath,bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image output" }
                    }
                    StandMediaKind.VIDEO, StandMediaKind.VOICE -> {
                        val extractor = android.media.MediaExtractor()
                        try {
                            extractor.setDataSource(temp.absolutePath)
                            val prefix = if (kind == StandMediaKind.VIDEO) "video/" else "audio/"
                            require((0 until extractor.trackCount).any {
                                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith(prefix) == true
                            }) { "Missing expected media track" }
                        } finally { extractor.release() }
                    }
                }
                java.nio.file.Files.move(temp.toPath(),target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { temp.delete() }
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
