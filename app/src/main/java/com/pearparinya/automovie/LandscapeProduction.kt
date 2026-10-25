package com.pearparinya.automovie

import org.json.JSONObject

/** Production format only; original character reference photos are never resized or rewritten. */
internal object LandscapeProduction {
    const val ASPECT="9:16"
    fun reframeText(raw: String): String = raw
        .replace(Regex("(?i)16[:x]9\\s+(horizontal|landscape|widescreen)"),"9:16 vertical")
        .replace(Regex("(?i)(horizontal|landscape|widescreen)(?=\\s*(16[:x]9|image|video|frame|canvas|orientation|composition|shot))"),"vertical")
        .replace("16:9","9:16").replace("16x9","9x16")
        .replace(Regex("1920\\s*[x×]\\s*1080"),"1080 x 1920")
        .replace("ภาพแนวนอน","ภาพแนวตั้ง").replace("วิดีโอแนวนอน","วิดีโอแนวตั้ง").replace("แนวนอน 9:16","แนวตั้ง 9:16")
    fun requirePlan(story: JSONObject) { require(story.optString("aspect_ratio",ASPECT)==ASPECT) { "เรื่องนี้ยังเป็นแนวนอน กรุณาสร้างสำเนาแนวตั้ง 9:16 ก่อน" } }
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
