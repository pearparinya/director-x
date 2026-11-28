package com.pearparinya.automovie.stand

import android.content.SharedPreferences
import org.json.JSONObject

class StandOwnershipStore(private val preferences: SharedPreferences) {
    private fun key(project: StandProject) = "stand.ownership.v1:" + project.channel.id + ":" + project.projectId

    fun load(project: StandProject): StandOwnership {
        val raw = preferences.getString(key(project), null) ?: return StandOwnership.restore(project)
        return StandOwnershipCodec.decode(project, raw)
    }

    fun save(state: StandOwnership): Boolean =
        preferences.edit().putString(key(state.project), StandOwnershipCodec.encode(state)).commit()
}

object StandOwnershipCodec {
    fun encode(state: StandOwnership): String = JSONObject().apply {
        put("channel", state.project.channel.id)
        put("project", state.project.projectId)
        put("assignments", JSONObject(state.snapshot()))
    }.toString()

    fun decode(project: StandProject, raw: String): StandOwnership {
        val json = JSONObject(raw)
        require(json.getString("channel") == project.channel.id)
        require(json.getString("project") == project.projectId)
        val entries = json.getJSONObject("assignments")
        val assignments = entries.keys().asSequence().associateWith { entries.getString(it) }
        return StandOwnership.restore(project, assignments)
    }
}
