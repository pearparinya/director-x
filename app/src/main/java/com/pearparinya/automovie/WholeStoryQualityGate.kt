package com.pearparinya.automovie

import org.json.JSONObject

/** Structural checks only; narrative coherence requires separate review. */
internal object WholeStoryQualityGate {
    fun issues(story: JSONObject, expectedEpisodes: Int, scenesPerEpisode: Int): List<String> = buildList {
        val episodes = story.optJSONArray("episodes")
        if (episodes == null || episodes.length() != expectedEpisodes) add("จำนวนตอนไม่ตรงตามแผน")
        if (story.optInt("episode_count", -1) != expectedEpisodes) add("ข้อมูลจำนวนตอนไม่ตรง")
        if (story.optInt("scene_count", -1) != expectedEpisodes * scenesPerEpisode) add("ข้อมูลจำนวนซีนไม่ตรง")
        for (i in 0 until (episodes?.length() ?: 0)) {
            val rows = episodes?.optJSONObject(i)?.optJSONArray("scenes")
            if (rows == null || rows.length() != scenesPerEpisode) add("EP ${i+1}: จำนวนซีนไม่ตรงตามแผน")
            for (j in 0 until (rows?.length() ?: 0)) {
                if (rows?.optJSONObject(j)?.optInt("scene", -1) != j+1) add("EP ${i+1} ซีน ${j+1}: หมายเลขซีนผิดลำดับหรือซ้ำ")
            }
        }
    }
}
