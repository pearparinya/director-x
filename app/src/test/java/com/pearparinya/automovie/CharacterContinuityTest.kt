package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class CharacterContinuityTest {
    private val first = CharacterContinuity.Beat("SC1","A","a","DAY01","19:00","home","blue","dry","wet","g",false,false)
    @Test fun detectsUnexplainedChanges() {
        val second = first.copy(scene="SC2",time="16.00",outfit="red",state="dry")
        assertEquals(3,CharacterContinuity.issues(listOf(first,second)).size)
    }
    @Test fun permitsExplicitChangeAndMatchingState() {
        val second = first.copy(scene="SC2",time="20:00",outfit="red",state="wet",change=true)
        assertTrue(CharacterContinuity.issues(listOf(first,second)).isEmpty())
    }
    @Test fun flashbackDoesNotOverwriteForwardHistory() {
        val flashback = first.copy(scene="SC2",time="07:00",flashback=true)
        val next = first.copy(scene="SC3",time="20:00",state="wet")
        assertTrue(CharacterContinuity.issues(listOf(first,flashback,next)).isEmpty())
    }
}
