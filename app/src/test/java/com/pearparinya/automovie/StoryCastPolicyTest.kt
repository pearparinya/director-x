package com.pearparinya.automovie
import org.junit.Test
import org.junit.Assert.assertTrue
class StoryCastPolicyTest {
    @Test fun acceptsDistinctActors() { StoryCastPolicy.requireCast(listOf("lead","support")) }
    @Test fun rejectsSingleDuplicateOrBlankActors() {
        for(ids in listOf(emptyList(),listOf("lead"),listOf("lead","lead"),listOf("lead"," "))) {
            assertTrue(runCatching { StoryCastPolicy.requireCast(ids) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
