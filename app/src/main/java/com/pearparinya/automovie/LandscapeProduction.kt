package com.pearparinya.automovie

import org.json.JSONObject

/** Production format only; original character reference photos are never resized or rewritten. */
internal object LandscapeProduction {
    const val ASPECT="16:9"
    fun reframeText(raw: String): String = raw
        .replace(Regex("(?i)9[:x]16\\s+(vertical|portrait)"),"16:9 horizontal")
        .replace(Regex("(?i)(vertical|portrait)(?=\\s*(9[:x]16|image|video|frame|canvas|orientation|composition|shot))"),"horizontal")
        .replace("9:16","16:9").replace("9x16","16x9")
        .replace(Regex("1080\\s*[x×]\\s*1920"),"1920 x 1080")
        .replace("ภาพแนวตั้ง","ภาพแนวนอน").replace("วิดีโอแนวตั้ง","วิดีโอแนวนอน").replace("แนวตั้ง 16:9","แนวนอน 16:9")
    fun requirePlan(story: JSONObject) { require(story.optString("aspect_ratio",ASPECT)==ASPECT) { "เรื่องนี้ยังเป็นแนวตั้ง กรุณาสร้างสำเนาแนวนอน 16:9 ก่อน" } }
    fun copyPlan(source: JSONObject): JSONObject {
        val copy=JSONObject(source.toString()).put("aspect_ratio",ASPECT)
        val eps=copy.getJSONArray("episodes")
        for(i in 0 until eps.length()) {
            val ep=eps.getJSONObject(i); val scenes=ep.getJSONArray("scenes")
            for(j in 0 until scenes.length()) {
                val scene=scenes.getJSONObject(j)
                for(field in listOf("image_prompt","flow_prompt","framing")) if(scene.has(field)) scene.put(field,reframeText(scene.optString(field)))
                listOf("approved_frame_sha256","flow_image_revision","image_revision","video_revision").forEach { scene.remove(it) }
                scene.put("user_review","needs_revision")
            }
        }
        return copy
    }
}
