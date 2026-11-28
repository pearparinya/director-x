package com.pearparinya.automovie.stand

import android.content.SharedPreferences
import org.json.JSONObject
import org.json.JSONArray

data class StandVitals(val health: Int, val energy: Int) {
    init { require(health in 0..100 && energy in 0..100) }
}
data class StandPersistentEpisode(val number: Int, val narrative: String,
    val owners: Map<String,String>, val vitals: Map<String,StandVitals>,
    val relationships: List<StandCharacterRelationship> = emptyList(), val arc: StandLongTermArc? = null,
    val goals: List<StandCharacterGoal> = emptyList(), val plots: List<StandPlotThread> = emptyList()) {
    init { require(number > 0 && narrative.isNotBlank()); require(vitals.keys.all { it in owners }) }
}
class StandUniverseArchive(private val prefs: SharedPreferences) {
    private fun key(project: StandProject) = "universe.v1:" + project.channel.id + ":" + project.projectId
    fun history(project: StandProject): List<StandPersistentEpisode> {
        val array = JSONArray(prefs.getString(key(project), "[]"))
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val owners = item.getJSONObject("owners")
            val vitals = item.getJSONObject("vitals")
            StandPersistentEpisode(item.getInt("number"), item.getString("narrative"),
                owners.keys().asSequence().associateWith { owners.getString(it) },
                vitals.keys().asSequence().associateWith {
                    val v = vitals.getJSONObject(it)
                    StandVitals(v.getInt("health"), v.getInt("energy"))
                }, (item.optJSONArray("relationships") ?: JSONArray()).let { arr ->
                    (0 until arr.length()).map { i -> val rel = arr.getJSONObject(i)
                        StandCharacterRelationship(rel.getString("first"),rel.getString("second"),StandRelationship.valueOf(rel.getString("relation"))) }
                }, item.optJSONObject("arc")?.let { StandLongTermArc(it.getString("goal"),it.getInt("finalEpisode")) },
                StandPlotJson.goals(item.optJSONArray("goals") ?: JSONArray()),
                StandPlotJson.plots(item.optJSONArray("plots") ?: JSONArray())).also {
                    require(it.number == index + 1)
                    StandOwnership.restore(project, it.owners)
                }
        }
    }
    fun migrateLegacy(project: StandProject, owners: StandOwnership): List<StandPersistentEpisode> = synchronized(lock) {
        require(owners.project == project)
        val existing = history(project)
        if (existing.isNotEmpty()) return@synchronized existing
        val legacy = StandEpisodeArchive(prefs).history(project)
        if (legacy.isEmpty()) return@synchronized emptyList()
        val array = JSONArray()
        legacy.forEachIndexed { index, text ->
            array.put(JSONObject().put("number",index+1).put("narrative",text)
                .put("owners",JSONObject(owners.snapshot())).put("vitals",JSONObject()))
        }
        check(prefs.edit().putString(key(project),array.toString()).commit())
        history(project)
    }
    fun append(project: StandProject, expected: List<StandPersistentEpisode>,
               episode: StandPersistentEpisode): Boolean = synchronized(lock) {
        if (history(project) != expected) return@synchronized false
        require(episode.number == expected.size + 1)
        require(StandOwnership.restore(project, episode.owners).snapshot() == episode.owners)
        require(expected.lastOrNull()?.owners.orEmpty().all { (id, owner) -> episode.owners[id] == owner })
        val prior = expected.lastOrNull()
        require(prior?.arc == null || prior.arc == episode.arc)
        episode.arc?.let { require(StandPlotContinuity.validate(episode.number, episode.owners, it,
            prior?.goals.orEmpty(), episode.goals, prior?.plots.orEmpty(), episode.plots)) }
        val array = JSONArray()
        (expected + episode).forEach { e ->
            val vitals = JSONObject()
            e.vitals.forEach { (id,v) -> vitals.put(id, JSONObject().put("health",v.health).put("energy",v.energy)) }
            val relationships = JSONArray()
            e.relationships.forEach { rel -> relationships.put(JSONObject().put("first",rel.first)
                .put("second",rel.second).put("relation",rel.relation.name)) }
            array.put(JSONObject().put("goals",StandPlotJson.encodeGoals(e.goals))
                .put("plots",StandPlotJson.encodePlots(e.plots)).put("relationships",relationships)
                .put("arc",e.arc?.let { JSONObject().put("goal",it.goal).put("finalEpisode",it.finalEpisode) } ?: JSONObject.NULL)
                .put("number",e.number).put("narrative",e.narrative)
                .put("owners",JSONObject(e.owners)).put("vitals",vitals))
        }
        prefs.edit().putString(key(project),array.toString())
            .putString("stand.ownership.v1:" + project.channel.id + ":" + project.projectId,
                StandOwnershipCodec.encode(StandOwnership.restore(project, episode.owners))).commit()
    }
    companion object { private val lock = Any() }
}
object StandAiStoryValidation {
    fun validate(ownership: StandOwnership, expected: StandPersistentEpisode,
                 response: JSONObject): Boolean = runCatching {
        val owners = response.getJSONObject("owners")
        val vitals = response.getJSONObject("vitals")
        response.getInt("episode") == expected.number &&
            response.getString("text").isNotBlank() &&
            owners.keys().asSequence().associateWith { owners.getString(it) } == expected.owners &&
            expected.owners == ownership.snapshot() &&
            vitals.keys().asSequence().associateWith {
                val v = vitals.getJSONObject(it)
                StandVitals(v.getInt("health"),v.getInt("energy"))
            } == expected.vitals
    }.getOrDefault(false)
}
