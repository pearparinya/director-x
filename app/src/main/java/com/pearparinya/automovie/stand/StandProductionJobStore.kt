package com.pearparinya.automovie.stand

import android.content.SharedPreferences
import org.json.JSONObject

class StandProductionJobStore(private val prefs: SharedPreferences) {
    private fun key(project: StandProject, jobId: String): String {
        require(jobId.isNotBlank())
        return "stand.media.job.v1:" + org.json.JSONArray(listOf(project.channel.id,project.projectId,jobId)).toString()
    }
    fun load(project: StandProject, jobId: String): StandMediaCheckpoint? =
        prefs.getString(key(project,jobId),null)?.let {
            val json = JSONObject(it)
            StandMediaCheckpoint(json.getString("provider"),StandSubmissionState.valueOf(json.getString("state")),
                json.optString("remote").takeIf { value -> value.isNotBlank() })
        }
    fun transition(project: StandProject, jobId: String, expected: StandMediaCheckpoint?,
                   next: StandMediaCheckpoint) = synchronized(lock) {
        check(load(project,jobId) == expected) { "Job checkpoint changed" }
        require(if (expected == null) next.state == StandSubmissionState.NOT_SUBMITTED
            else when (expected.state) {
                StandSubmissionState.NOT_SUBMITTED, StandSubmissionState.FAILED_BEFORE_SUBMISSION ->
                    next.state == StandSubmissionState.SUBMITTING
                StandSubmissionState.SUBMITTING -> expected.providerId == next.providerId &&
                    next.state in setOf(StandSubmissionState.REMOTE_PENDING,StandSubmissionState.COMPLETED,
                        StandSubmissionState.FAILED_BEFORE_SUBMISSION)
                StandSubmissionState.REMOTE_PENDING -> expected.providerId == next.providerId &&
                    expected.remoteId == next.remoteId && next.state == StandSubmissionState.COMPLETED
                StandSubmissionState.COMPLETED -> false
            })
        val json = JSONObject().put("provider",next.providerId).put("state",next.state.name)
            .put("remote",next.remoteId.orEmpty())
        check(prefs.edit().putString(key(project,jobId),json.toString()).commit())
    }
    companion object { private val lock = Any() }
}
