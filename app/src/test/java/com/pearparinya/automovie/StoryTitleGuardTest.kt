package com.pearparinya.automovie

import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTitleGuardTest {
    @Test fun unrelatedChannelPlaceNameMustNotLeakIntoTitle() {
        val result=runCatching { StoryTitleGuard.validate("น้ำบุญ น้ำใจ ชาวคลองขุด","นายเปีย บ้านคลองขุด") }
        assertTrue(result.isFailure)
    }
    @Test fun placeNameIsReservedEvenForLocalPlots() {
        assertTrue(runCatching { StoryTitleGuard.validate("น้ำใจชาวคลองขุด","นายเปีย บ้านคลองขุด") }.isFailure)
    }
    @Test fun blankGeneratedTitleIsRejected() {
        assertTrue(runCatching { StoryTitleGuard.validate("  ","นายเปีย บ้านคลองขุด") }.isFailure)
    }
    @Test fun channelBrandingIsRejectedEvenWhenSeparatedBySpaces() {
        assertTrue(runCatching { StoryTitleGuard.validate("นายเปีย   บ้านคลองขุด","นายเปีย บ้านคลองขุด") }.isFailure)
    }
    @Test fun unrelatedPersonalChannelNameIsRejected() {
        assertTrue(runCatching { StoryTitleGuard.validate("นายเปียผู้กล้าหาญ","นายเปีย บ้านคลองขุด") }.isFailure)
    }
    @Test fun independentTitleIsAllowed() {
        StoryTitleGuard.validate("น้ำใจที่ไม่เคยหมด","นายเปีย บ้านคลองขุด")
    }
    @Test fun cleansSavedEpisodeTitleWithoutChangingCastOrScenes() {
        val scene=org.json.JSONObject().put("speaker","นายเปีย บ้านคลองขุด")
        val ep=org.json.JSONObject().put("title","เสียงจากบ้านคลองขุด").put("scenes",org.json.JSONArray().put(scene))
        val story=org.json.JSONObject().put("channel","นายเปีย บ้านคลองขุด").put("title","เสียงสะท้อนของชุมชน")
            .put("category","นายเปียพาช้อป").put("episodes",org.json.JSONArray().put(ep))
        ChannelCatalog.updateLegacyChannel(story)
        org.junit.Assert.assertEquals("เสียงสะท้อนของชุมชน",ep.getString("title"))
        org.junit.Assert.assertEquals("รีวิวและแนะนำสินค้า",story.getString("category"))
        org.junit.Assert.assertEquals("นายเปีย บ้านคลองขุด",scene.getString("speaker"))
    }
}
