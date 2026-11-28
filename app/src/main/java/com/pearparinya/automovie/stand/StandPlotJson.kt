package com.pearparinya.automovie.stand
import org.json.JSONArray
import org.json.JSONObject

object StandPlotJson {
    fun goals(a: JSONArray) = (0 until a.length()).map { i ->
        val v = a.getJSONObject(i)
        StandCharacterGoal(v.getString("owner"),v.getString("objective"),v.getBoolean("achieved"))
    }
    fun plots(a: JSONArray) = (0 until a.length()).map { i ->
        val v = a.getJSONObject(i)
        StandPlotThread(v.getString("id"),v.getString("description"),v.getInt("opened"),
            if(v.isNull("resolved")) null else v.getInt("resolved"))
    }
    fun encodeGoals(values: List<StandCharacterGoal>) = JSONArray().apply {
        values.forEach { put(JSONObject().put("owner",it.owner).put("objective",it.objective).put("achieved",it.achieved)) }
    }
    fun encodePlots(values: List<StandPlotThread>) = JSONArray().apply {
        values.forEach { put(JSONObject().put("id",it.id).put("description",it.description)
            .put("opened",it.opened).put("resolved",it.resolved ?: JSONObject.NULL)) }
    }
}
