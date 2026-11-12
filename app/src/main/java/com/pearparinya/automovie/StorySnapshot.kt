package com.pearparinya.automovie

import org.json.JSONArray
import org.json.JSONObject

/** Compare JSON values, not in-memory object identity or object key order. */
internal object StorySnapshot {
    fun same(snapshot: String, current: JSONObject?): Boolean {
        if(current==null) return false
        return runCatching { canonical(JSONObject(snapshot))==canonical(current) }.getOrDefault(false)
    }
    private fun canonical(value: Any?): String = when(value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it)+":"+canonical(value.opt(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
