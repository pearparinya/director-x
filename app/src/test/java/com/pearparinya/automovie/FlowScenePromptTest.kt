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
        assertTrue(prompt.contains("<dialogue>"))
        assertTrue(prompt.contains("<speaker>[เมย์]</speaker>"))
        assertTrue(prompt.contains("[เมย์] speaks naturally"))
        assertTrue(prompt.contains("พี่ริน หนูเป็นน้องสาวของพี่"))
        assertFalse(prompt.contains("<voiceover>"))
    }
    @Test fun narratorMatchesReferenceLayoutAndKeepsOriginalSpeech() {
        val line="การตรวจสอบพื้นที่เริ่มต้นขึ้น ท่ามกลางความระแวง"
        val prompt=FlowScenePrompt.build(scene(speaker="",dialogue="",narration=line),"scene_investigation_01")
        val tags=listOf("<aspect_ratio>","<duration>","<reference_image>","<characters>","<prompt>","<lighting>","<typography_safe_zone>","<voiceover>","<sound_effects>")
        assertEquals(tags.map { prompt.indexOf(it) }.sorted(),tags.map { prompt.indexOf(it) })
        assertTrue(tags.all { prompt.contains(it) })
        assertTrue(prompt.contains("Masterpiece, 8k resolution, cinematic shot. One continuous 8-second vertical shot."))
        assertTrue(prompt.contains("5500K daylight white balance, pure bright studio lighting, no yellow light"))
        assertEquals(1,Regex(Regex.escape(line)).findAll(prompt).count())
        assertTrue(prompt.contains("<language>th-TH</language>"))
        assertTrue(prompt.contains("<subtitles>false</subtitles>"))
    }
    @Test fun characterReferencesMatchActionsAndSpeakerWithoutChangingDialogue() {
        val prompt=FlowScenePrompt.build(scene())
        assertTrue(prompt.contains("<character>[เมย์]</character>"))
        assertTrue(prompt.contains("<character>[รินรดา]</character>"))
        assertTrue(prompt.contains("[เมย์] slides the envelope"))
        assertTrue(prompt.contains("[เมย์]: short-haired woman, left in the first frame"))
        assertTrue(prompt.contains("<speaker>[เมย์]</speaker>"))
        assertTrue(prompt.contains("พี่ริน หนูเป็นน้องสาวของพี่"))
        assertFalse(prompt.contains("<character>Off-screen"))
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
        assertTrue(narrator.contains("<speaker>Off-screen Male Narrator</speaker>"))
        assertTrue(narrator.contains("Visible characters remain silent with mouths closed"))
        val silent=FlowScenePrompt.build(scene(speaker="",dialogue="",narration="ไม่มี"))
        assertFalse(silent.contains("<voiceover>"))
        assertTrue(FlowScenePrompt.issues(scene(narration="เสียงบรรยายซ้อน")).isNotEmpty())
    }
    @Test fun emptyEstablishingShotIsAllowedWithoutInventingPeople() {
        val establishing=scene(speaker="",dialogue="").copy(cast=emptyList())
        assertTrue(FlowScenePrompt.issues(establishing).isEmpty())
        assertFalse(FlowScenePrompt.build(establishing).contains("<dialogue>"))
    }
    @Test fun duplicateAliasesAndIndistinguishableMappingsAreRejected() {
        assertTrue(FlowScenePrompt.issues(scene().copy(cast=listOf(may,may.copy(name="มายด์")))).isNotEmpty())
        assertTrue(FlowScenePrompt.issues(scene().copy(cast=listOf(may,rin.copy(appearance=may.appearance,position=may.position)))).isNotEmpty())
    }
}
