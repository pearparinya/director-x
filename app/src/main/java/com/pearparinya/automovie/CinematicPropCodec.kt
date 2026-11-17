package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, optional scene inventory serialization. An empty inventory is
 * represented by an empty JSON array, preserving legacy scenes.
 */
internal object CinematicPropCodec {
    fun encode(props: List<CinematicProp>): String {
        require(CinematicScenePreflight.issues(props).isEmpty()) { "Invalid cinematic prop inventory" }
        val array = JSONArray()
        props.forEach { prop ->
            array.put(JSONObject().apply {
                put("id", prop.id)
                put("presentInFirstFrame", prop.presentInFirstFrame)
                put("requiredForAction", prop.requiredForAction)
                put("screenSide", prop.screenSide)
                put("expectedScreenSide", prop.expectedScreenSide)
                put("expectedCount", prop.expectedCount)
                if (prop.firstFrameCount != null) put("firstFrameCount", prop.firstFrameCount)
            })
        }
        return array.toString()
    }

    fun decode(raw: String?): List<CinematicProp> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        require(array.length() <= 50) { "Inventory exceeds 50 props" }
        val props = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CinematicProp(
                id = item.getString("id"),
                presentInFirstFrame = item.getBoolean("presentInFirstFrame"),
                requiredForAction = item.getBoolean("requiredForAction"),
                screenSide = item.optString("screenSide", ""),
                expectedScreenSide = item.optString("expectedScreenSide", ""),
                expectedCount = if (item.has("expectedCount")) item.getInt("expectedCount") else 1,
                firstFrameCount = if (item.has("firstFrameCount") && !item.isNull("firstFrameCount")) item.getInt("firstFrameCount") else null
            )
        }
        require(CinematicScenePreflight.issues(props).isEmpty()) { "Invalid cinematic prop inventory" }
        return props
    }
}
