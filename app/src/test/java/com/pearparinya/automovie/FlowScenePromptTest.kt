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
        assertTrue(prompt.contains("ONLY [เมย์] — left, short-haired woman speaks"))
        assertTrue(prompt.contains("[รินรดา] — right, long-haired woman must NOT speak"))
        assertTrue(prompt.contains("[เมย์] outfit in scene: cream top."))
        assertTrue(prompt.contains("<lighting>5500K daylight white balance"))
        assertTrue(prompt.contains("Keep their lips closed and still throughout"))
        assertTrue(prompt.contains("พี่ริน หนูเป็นน้องสาวของพี่"))
        assertFalse(prompt.contains("<voiceover>"))
    }
    @Test fun narratorMatchesReferenceLayoutAndKeepsOriginalSpeech() {
        val line="การตรวจสอบพื้นที่เริ่มต้นขึ้น ท่ามกลางความระแวง"
        val prompt=FlowScenePrompt.build(scene(speaker="",dialogue="",narration=line),"scene_investigation_01")
        val tags=listOf("<aspect_ratio>","<duration>","<reference_image>","<characters>","<prompt>","<voiceover>","<sound_effects>")
        assertEquals(tags.map { prompt.indexOf(it) }.sorted(),tags.map { prompt.indexOf(it) })
        assertTrue(tags.all { prompt.contains(it) })
        assertTrue(prompt.contains("Full vertical continuous 8-second shot with no cuts."))
        assertEquals(1,Regex(Regex.escape(line)).findAll(prompt).count())
        assertTrue(prompt.contains("<language>th-TH</language>"))
        assertTrue(prompt.contains("<subtitles>false</subtitles>"))
    }
    @Test fun thaiSpeechPreservesPhraseSpacesPunctuationAndXmlCharacters() {
        val dialogue="พี่ริน ครับ... ฟังก่อนนะ & เข้าใจไหม"
        val narration="การตรวจสอบพื้นที่เริ่มต้นขึ้น ท่ามกลางความระแวง"
        fun readText(prompt: String): String = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(prompt.toByteArray(Charsets.UTF_8)))
            .getElementsByTagName("text").item(0).textContent
        assertEquals(dialogue,readText(FlowScenePrompt.build(scene(dialogue=dialogue))))
        assertEquals(narration,readText(FlowScenePrompt.build(scene(speaker="",dialogue="",narration=narration))))
    }
    @Test fun characterReferencesMatchActionsAndSpeakerWithoutChangingDialogue() {
        val prompt=FlowScenePrompt.build(scene())
        assertTrue(prompt.contains("<character>[เมย์]</character>"))
        assertTrue(prompt.contains("<character>[รินรดา]</character>"))
        assertTrue(prompt.contains("[เมย์] slides the envelope"))
        assertTrue(prompt.contains("[รินรดา] — right, long-haired woman must NOT speak"))
        assertTrue(prompt.contains("<speaker>[เมย์]</speaker>"))
        assertTrue(prompt.contains("พี่ริน หนูเป็นน้องสาวของพี่"))
        assertFalse(prompt.contains("<character>Off-screen"))
    }
    @Test fun exampleNamesAreStableAndMappedOnlyInAction() {
        val woman=FlowPerson("พัชรินทร์","long brown hair","center","black dress")
        val man=FlowPerson("นที","short dark hair","right background","dark trousers")
        val spec=scene(speaker="",dialogue="",narration="พัชรินทร์มองนที").copy(cast=listOf(woman,man),action="patcharin inspects the blueprints while natee observes")
        val result=FlowScenePrompt.build(spec)
        assertTrue(result.contains("<character>[พัชรินทร์]</character>"))
        assertTrue(result.contains("<character>[นที]</character>"))
        assertTrue(result.contains("[พัชรินทร์] inspects the blueprints while [นที] observes"))
        assertTrue(result.contains("<text>พัชรินทร์มองนที</text>"))
        assertEquals(FlowScenePrompt.build(spec).substringAfter("<character>" ).substringBefore("</character>"),FlowScenePrompt.build(spec.copy(cast=listOf(man,woman))).substringAfterLast("<character>").substringBefore("</character>"))
    }
    @Test fun customNamesUseStableDistinctEnglishIdentifiers() {
        val a=FlowPerson("อรุณี","dark hair","left","coat")
        val b=FlowPerson("มานี","fair hair","right","shirt")
        val spec=scene(speaker="อรุณี",dialogue="สวัสดี").copy(cast=listOf(a,b),action="อรุณี waves at มานี")
        val prompt=FlowScenePrompt.build(spec)
        val first=prompt.substringAfter("<character>").substringBefore("</character>")
        val second=prompt.substringAfterLast("<character>").substringBefore("</character>")
        assertTrue(first == "[อรุณี]"); assertNotEquals(first,second)
        assertTrue(prompt.contains("${first.substringBefore(" —")} waves at ${second.substringBefore(" —")}"))
        assertTrue(prompt.contains("<speaker>$first</speaker>"))
        assertTrue(prompt.contains("<text>สวัสดี</text>"))
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
    @Test fun piyawanAliasIsBoundAndUnknownRomanizedNamesAreNotGuessed() {
        val person=FlowPerson("ปิยวรรณ","woman with dark hair","center","white shirt")
        val spec=scene(speaker="ปิยวรรณ",dialogue="สวัสดี").copy(cast=listOf(person),action="piyawan holds her document folder firmly")
        val result=FlowScenePrompt.build(spec)
        assertTrue(result.contains("[ปิยวรรณ] holds her document folder firmly"))
        assertTrue(result.contains("<speaker>[ปิยวรรณ]</speaker>"))
        assertFalse(result.contains("piyawan holds"))
        val unknown=spec.copy(action="unmappedname holds a folder")
        assertTrue(FlowScenePrompt.issues(unknown).any { it.contains("unmappedname") })
        assertFalse(FlowScenePrompt.build(unknown).contains("<scene"))
    }
    @Test fun duplicateAliasesAndIndistinguishableMappingsAreRejected() {
        assertTrue(FlowScenePrompt.issues(scene().copy(cast=listOf(may,may.copy(name="มายด์")))).isNotEmpty())
        assertTrue(FlowScenePrompt.issues(scene().copy(cast=listOf(may,rin.copy(appearance=may.appearance,position=may.position)))).isNotEmpty())
    }
}
