package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test

class FlowScenePromptTest {
    private val may=FlowPerson("เมย์","short-haired woman","left","cream top")
    private val rin=FlowPerson("รินรดา","long-haired woman","right","pink top")
    private fun scene(speaker: String="เมย์", dialogue: String="พี่ริน หนูเป็นน้องสาวของพี่", narration: String="ไม่มี") =
        FlowSceneSpec(listOf(may,rin),speaker,dialogue,narration,"May slides the envelope","living room","Envelope in the center","Envelope near Rinlada",8,"9:16")
    @Test fun speakerIsBoundToAppearanceAndListenersStaySilent() {
        val prompt=FlowScenePrompt.build(scene())
        assertTrue(prompt.contains("ONLY SPEAKER: เมย์ [short-haired woman; screen position: left"))
        assertTrue(prompt.contains("SILENT LISTENER: รินรดา [long-haired woman; screen position: right"))
        assertTrue(prompt.contains("No narrator."))
        assertFalse(prompt.contains("NARRATOR ONLY"))
        assertTrue(prompt.contains("END STATE: Envelope near Rinlada"))
    }
    @Test fun aliasesResolveButAbsentOrMultipleSpeakersAreRejected() {
        assertTrue(FlowScenePrompt.issues(scene(speaker="มายด์")).isEmpty())
        assertTrue(FlowScenePrompt.issues(scene(speaker="กวิน")).isNotEmpty())
        assertTrue(FlowScenePrompt.issues(scene(speaker="เมย์ + รินรดา")).isNotEmpty())
    }
    @Test fun missingPositionsAreNeverInvented() {
        val s=scene().copy(cast=listOf(may.copy(position=""),rin))
        assertTrue(FlowScenePrompt.issues(s).isNotEmpty())
        assertFalse(FlowScenePrompt.build(s).contains("ONLY SPEAKER"))
    }
    @Test fun narratorAndSilentScenesNeverAnimateTalkingMouths() {
        val narrator=FlowScenePrompt.build(scene(speaker="",dialogue="",narration="ความจริงเริ่มเปิดเผย"))
        assertTrue(narrator.contains("OFF-SCREEN MALE NARRATOR ONLY"))
        assertTrue(narrator.contains("All on-screen mouths stay closed"))
        val silent=FlowScenePrompt.build(scene(speaker="",dialogue="",narration="ไม่มี"))
        assertTrue(silent.contains("Silent scene"))
        assertTrue(FlowScenePrompt.issues(scene(narration="เสียงบรรยายซ้อน")).isNotEmpty())
    }
    @Test fun emptyEstablishingShotIsAllowedWithoutInventingPeople() {
        val establishing=scene(speaker="",dialogue="").copy(cast=emptyList())
        assertTrue(FlowScenePrompt.issues(establishing).isEmpty())
        assertTrue(FlowScenePrompt.build(establishing).contains("Silent scene"))
    }
    @Test fun duplicateAliasesAndIndistinguishableMappingsAreRejected() {
        assertTrue(FlowScenePrompt.issues(scene().copy(cast=listOf(may,may.copy(name="มายด์")))).isNotEmpty())
        assertTrue(FlowScenePrompt.issues(scene().copy(cast=listOf(may,rin.copy(appearance=may.appearance,position=may.position)))).isNotEmpty())
    }
}
