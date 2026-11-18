package com.pearparinya.automovie

import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTitleGuardTest {
    @Test fun unrelatedChannelPlaceNameMustNotLeakIntoTitle() {
        val result=runCatching { StoryTitleGuard.validate("น้ำบุญ น้ำใจ ชาวคลองขุด","นายเปีย บ้านคลองขุด","เรื่องคนช่วยเหลือผู้สูงอายุ") }
        assertTrue(result.isFailure)
    }
    @Test fun plotRelevantPlaceNameIsAllowed() {
        StoryTitleGuard.validate("น้ำใจชาวคลองขุด","นายเปีย บ้านคลองขุด","ชาวคลองขุดช่วยกันซ่อมบ้าน")
    }
    @Test fun blankGeneratedTitleIsRejected() {
        assertTrue(runCatching { StoryTitleGuard.validate("  ","นายเปีย บ้านคลองขุด","เรื่องคนช่วยเหลือผู้สูงอายุ") }.isFailure)
    }
    @Test fun channelBrandingIsRejectedEvenWhenSeparatedBySpaces() {
        assertTrue(runCatching { StoryTitleGuard.validate("นายเปีย   บ้านคลองขุด","นายเปีย บ้านคลองขุด","เรื่องคนช่วยเหลือผู้สูงอายุ") }.isFailure)
    }
    @Test fun unrelatedPersonalChannelNameIsRejected() {
        assertTrue(runCatching { StoryTitleGuard.validate("นายเปียผู้กล้าหาญ","นายเปีย บ้านคลองขุด","เรื่องคนช่วยเหลือผู้สูงอายุ") }.isFailure)
    }
    @Test fun independentTitleIsAllowed() {
        StoryTitleGuard.validate("น้ำใจที่ไม่เคยหมด","นายเปีย บ้านคลองขุด","เรื่องคนช่วยเหลือผู้สูงอายุ")
    }
}
