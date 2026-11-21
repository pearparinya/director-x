package com.pearparinya.automovie

/**
 * Derives production progress from verified artifacts, never from saved UI flags.
 * Caller must verify each image can be decoded before including its scene ID.
 */
internal object ProductionStateEngine {
    enum class Status { COMPLETED, IN_PROGRESS, PENDING, LOCKED, ERROR }
    data class Snapshot(
        val story: Status,
        val images: Status,
        val flow: Status,
        val verifiedImages: Int,
        val totalImages: Int,
        val missingSceneIds: List<Int>
    )

    fun evaluate(
        sceneIds: List<Int>,
        storyValid: Boolean,
        verifiedImageIds: Set<Int>,
        flowFilesValid: Boolean
    ): Snapshot {
        val validIds = sceneIds.isNotEmpty() && sceneIds.all { it > 0 } &&
            sceneIds.distinct().size == sceneIds.size
        val story = when {
            !validIds -> Status.ERROR
            !storyValid -> Status.IN_PROGRESS
            else -> Status.COMPLETED
        }
        val verified = sceneIds.filter { it in verifiedImageIds }.distinct()
        val missing = sceneIds.filter { it !in verifiedImageIds }
        val images = when {
            story != Status.COMPLETED -> Status.LOCKED
            missing.isEmpty() -> Status.COMPLETED
            verified.isNotEmpty() -> Status.IN_PROGRESS
            else -> Status.PENDING
        }
        val flow = when {
            story != Status.COMPLETED -> Status.LOCKED
            flowFilesValid -> Status.COMPLETED
            else -> Status.PENDING
        }
        return Snapshot(story, images, flow, verified.size, sceneIds.size, missing)
    }
}
