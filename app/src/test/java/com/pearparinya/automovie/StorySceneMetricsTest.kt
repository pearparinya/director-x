package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StorySceneMetricsTest {
    @Test fun legacyTotalAndNewSceneCountersStaySeparateAcrossRetries() {
        var now = 100L
        val legacy = JSONObject().put("request_number", 57).put("elapsed_ms", 120000)
        val job = StoryJobStatus(legacy, 62, { now }, { 200000L })
        val scene = "เขียน EP 1/1 • ซีน 6–6"
        job.setTask(scene)
        for (attempt in 1..2) {
            job.describeRequest("สร้างเนื้อหา", 4321)
            job.beginRequest(StoryProvider.GROQ, 45000)
            now += 4000
            job.finishAttempt("HTTP 413")
            job.endRequest()
        }
        job.setTask("เขียน EP 1/1 • ซีน 7–7")
        now += 500
        val status = job.heartbeat()
        val metrics = status.getJSONObject("task_metrics")
        val sixth = metrics.getJSONObject(scene)
        assertEquals(59, status.getInt("request_number"))
        assertEquals(2, sixth.getInt("requests"))
        assertEquals(8000L, sixth.getLong("elapsed_ms"))
        assertEquals(8000L, sixth.getJSONObject("stages").getLong("สร้างเนื้อหา"))
        val latest = sixth.getJSONArray("recent").getJSONObject(1)
        assertEquals("HTTP 413", latest.getString("result"))
        assertEquals(4321, latest.getInt("bytes"))
        assertFalse(latest.has("prompt"))
        assertEquals(500L, metrics.getJSONObject("เขียน EP 1/1 • ซีน 7–7").getLong("elapsed_ms"))
    }
}
