package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorySnapshotTest {
    @Test fun reloadedEquivalentStoryIsAcceptedButChangesAreRejected() {
        val snapshot="""{"title":"เรื่อง","episodes":[{"ep":1,"scenes":[{"scene":1,"action":"เดิม"}]}]}"""
        val reloaded=JSONObject("""{"episodes":[{"scenes":[{"action":"เดิม","scene":1}],"ep":1}],"title":"เรื่อง"}""")
        assertTrue(StorySnapshot.same(snapshot,reloaded))
        reloaded.getJSONArray("episodes").getJSONObject(0).getJSONArray("scenes").getJSONObject(0).put("action","เปลี่ยน")
        assertFalse(StorySnapshot.same(snapshot,reloaded))
        assertFalse(StorySnapshot.same(snapshot,null))
    }
    @Test fun onlyLiteralCompleteEvidenceCanBeSelectedAutomatically() {
        val row=JSONObject().put("item","เก้าอี้ไม้เก่า").put("holder","ลุงสมบัติ")
            .put("evidence","ลุงสมบัตินั่งบนเก้าอี้ไม้เก่า").put("details","").put("continuity_id","")
        assertTrue(PropSuggestionReview.directlySupported(row))
        row.put("continuity_id","chair1")
        assertFalse(PropSuggestionReview.directlySupported(row))
        row.put("continuity_id","").put("details","สีน้ำตาล")
        assertFalse(PropSuggestionReview.directlySupported(row))
        row.put("details","").put("holder","เพ็ญศรี")
        assertFalse(PropSuggestionReview.directlySupported(row))
    }
}
