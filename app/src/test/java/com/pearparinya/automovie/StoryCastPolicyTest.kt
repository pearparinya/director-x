package com.pearparinya.automovie
import org.junit.Test
import org.junit.Assert.assertTrue
class StoryCastPolicyTest {
    @Test fun sizingForRequestedStoryLengths() {
        for ((scenes, expected) in mapOf(8 to 2..4, 20 to 3..6, 50 to 5..12, 100 to 6..15)) {
            org.junit.Assert.assertEquals(expected, StoryCastPolicy.recommended(scenes))
            assertTrue(StoryCastPolicy.sizingGuide(scenes).contains("${expected.first}–${expected.last}"))
        }
    }
    @Test fun acceptsDistinctActors() { StoryCastPolicy.requireCast(listOf("lead","support")) }
    @Test fun rejectsSingleDuplicateOrBlankActors() {
        for(ids in listOf(emptyList(),listOf("lead"),listOf("lead","lead"),listOf("lead"," "))) {
            assertTrue(runCatching { StoryCastPolicy.requireCast(ids) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
