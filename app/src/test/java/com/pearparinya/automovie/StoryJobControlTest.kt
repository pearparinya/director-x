package com.pearparinya.automovie
import org.junit.Assert.*
import org.junit.Test
class StoryJobControlTest {
    @Test fun cancellationRequiresActiveMatchingProject() {
        assertTrue(StoryJobControl.canCancel("a","a",true))
        assertFalse(StoryJobControl.canCancel("a","b",true))
        assertFalse(StoryJobControl.canCancel("a","a",false))
        assertFalse(StoryJobControl.canCancel(null,"a",true))
        assertFalse(StoryJobControl.canCancel(null,null,true))
        assertFalse(StoryJobControl.canCancel("","",true))
    }
}
