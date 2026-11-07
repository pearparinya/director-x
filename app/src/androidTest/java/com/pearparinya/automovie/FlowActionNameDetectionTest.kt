package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowActionNameDetectionTest {
    private fun scene(action: String, englishName: String = "") = FlowSceneSpec(
        cast = listOf(FlowPerson("ปิ่นมณี", "adult woman", "center", "navy suit", englishName = englishName)),
        speaker = "", dialogue = "", narration = "", action = action,
        location = "office", startState = "", endState = "", duration = 8, aspect = LandscapeProduction.ASPECT
    )

    @Test fun possessivePronounsBeforeNounsThatCanBeVerbsAreNotNames() {
        listOf("her hands", "his hands", "their hands", "your hands", "our hands", "its hands").forEach {
            assertTrue(it, FlowScenePrompt.unknownActionNames(scene("She places $it on the desk")).isEmpty())
        }
    }

    @Test fun unidentifiedNamesStillRequireReview() {
        assertEquals(listOf("Pinmanee"), FlowScenePrompt.unknownActionNames(scene("Pinmanee stands by the desk")))
        assertTrue(FlowScenePrompt.unknownActionNames(scene("Pinmanee stands by the desk", "Pinmanee")).isEmpty())
    }

    @Test fun registeredMayAndModalMayRemainDistinct() {
        assertTrue(FlowScenePrompt.unknownActionNames(scene("May stands by the desk", "May")).isEmpty())
        assertTrue(FlowScenePrompt.unknownActionNames(scene("She may turn toward the door")).isEmpty())
    }
}
