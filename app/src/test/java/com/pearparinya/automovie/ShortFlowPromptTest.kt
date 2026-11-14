package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class ShortFlowPromptTest {
    private fun scene()=FlowSceneSpec(listOf(FlowPerson("สมชาย","adult","left","shirt"),FlowPerson("มานี","adult","right","dress")),"สมชาย","หยุดก่อน, ฟังฉัน", "", "สมชาย walks toward มานี in drifting fog", "street", "fog", "standing",8,"9:16")
    @Test fun englishDirectionsKeepOriginalThaiSpeechAndMotion() {
        val output=ShortFlowPrompt.build(scene(),"Slow pan")
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(output)))
        assertFalse(Regex("[\\u0E00-\\u0E7F]").containsMatchIn(doc.getElementsByTagName("prompt").item(0).textContent))
        assertEquals("หยุดก่อน, ฟังฉัน",doc.getElementsByTagName("text").item(0).textContent)
        assertTrue(output.contains("Character 1 walks toward Character 2"))
        assertTrue(output.contains("fog moves gently"))
        assertEquals("8",doc.getElementsByTagName("duration").item(0).textContent)
        assertEquals("th-TH",doc.getElementsByTagName("language").item(0).textContent)
        assertEquals(0,doc.getElementsByTagName("constraint").length)
        assertEquals("Character 1: หยุดก่อน, ฟังฉัน",ShortFlowPrompt.voiceOnly(scene()))
    }
    @Test fun mapsEachSpokenLineToPositionAndRecordedShirt() {
        val base=scene()
        val cast=listOf(FlowPerson("สมชาย","adult","center front","Top: cream shirt; Bottom: green trousers"),FlowPerson("มานี","adult","left","Top: black shirt; Shoes: boots"),FlowPerson("หญิง","adult","right","Top: blue shirt"))
        val sample=base.copy(cast=cast,dialogue="",speaker="",action="สมชาย points; มานี rows",turns=listOf(FlowDialogueTurn("สมชาย","เร็ว!"),FlowDialogueTurn("มานี","ตามฉัน!")))
        val output=ShortFlowPrompt.build(sample,"Slow pan")
        assertTrue(output.contains("Character 1: center front, wearing cream shirt"))
        assertTrue(output.contains("Character 2: left, wearing black shirt"))
        assertTrue(output.contains("Character 3: right, wearing blue shirt"))
        assertTrue(output.contains("<speaker>Character 1</speaker><text>เร็ว!</text>"))
        assertTrue(output.contains("<speaker>Character 2</speaker><text>ตามฉัน!</text>"))
        assertTrue(output.contains("ONLY Character 1 (center front, wearing cream shirt) speaks; animate the mouth only if visible and anatomically appropriate"))
        assertTrue(output.contains("Character 2, Character 3 remain silent; do not invent mouths"))
        assertTrue(output.contains("ONLY Character 2 (left, wearing black shirt) speaks; animate the mouth only if visible and anatomically appropriate"))
        assertTrue(output.contains("Use only the props and objects already visible in the attached first frame"))
        assertTrue(output.contains("If an action requires an object missing from the first frame"))
        assertFalse(output.contains("green trousers"))
        assertEquals("cream shirt",ShortFlowPrompt.clothing(cast[0].copy(outfit="Top: เสื้อเชิ้ตสีครีม; Bottom: กางเกงสีเขียว")))
        assertEquals("black jacket",ShortFlowPrompt.clothing(cast[1].copy(outfit="Top: แจ็กเก็ตสีดำ")))
        assertEquals("white work outfit",ShortFlowPrompt.clothing(cast[2].copy(outfit="ชุดทำงานสีขาว")))
        assertTrue(runCatching { ShortFlowPrompt.build(sample.copy(cast=cast.toMutableList().apply { this[0]=this[0].copy(outfit="Top: เสื้อไม่มีข้อมูลสี") }),"Slow pan") }.isFailure)
    }
    @Test fun mixedCastGetsStableTypeLabelsAndTurnBindings() {
        val people=listOf(
            FlowPerson("เอก","adult male","left","cream shirt",characterGender="man"),
            FlowPerson("บี","adult male","right","black jacket",characterGender="man"),
            FlowPerson("ซี","adult female","front","blue shirt",performance="emotion: anxious; reaction: relieved; voice: gentle",characterGender="woman"),
            FlowPerson("ดี","black dog","back","",characterType="animal"),
            FlowPerson("อี","red demon","center","",characterType="creature"))
        val sample=scene().copy(cast=people,speaker="",dialogue="",action="เอก points. บี listens. ซี signals. ดี waits. อี watches",turns=listOf(FlowDialogueTurn("ซี","ระวัง!"),FlowDialogueTurn("เอก","ไปกัน!")))
        val output=ShortFlowPrompt.build(sample,"Locked")
        listOf("The Man 1: left", "The Man 2: right", "Woman 1: front", "Animal 1: back", "Demon 1: center").forEach { assertTrue(it,output.contains(it)) }
        assertTrue(output.contains("<speaker>Woman 1</speaker><text>ระวัง!</text>"))
        assertTrue(output.contains("<speaker>The Man 1</speaker><text>ไปกัน!</text>"))
        assertTrue(output.contains("The Man 1 points"))
        assertTrue(output.contains("Woman 1 performance: emotion: anxious; reaction: relieved; voice: gentle"))
        assertFalse(output.contains("<constraint>"))
        assertFalse(output.contains("[Woman 1]"))
        assertTrue(output.contains("<language>th-TH</language>"))
        assertTrue(output.contains("Animal 1, Demon 1 remain silent"))
    }
    @Test fun includesSceneLocationAndRecordedActingInXml() {
        val base=scene()
        val spec=base.copy(cast=base.cast.mapIndexed { i, p -> if(i==0) p.copy(performance="emotion: anxious; objective: reassure; gaze: listener; body_language: still; reaction: relieved") else p })
        val xml=ShortFlowPrompt.build(spec,"Locked camera")
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val direction=doc.getElementsByTagName("prompt").item(0).textContent
        assertTrue(direction.contains("Environment: street"))
        assertTrue(direction.contains("objective: reassure"))
        assertTrue(direction.contains("gaze: listener"))
        assertEquals("หยุดก่อน, ฟังฉัน",doc.getElementsByTagName("text").item(0).textContent)
    }
    @Test fun rejectsUntranslatedActionInsteadOfSilentlyDroppingIt() {
        assertTrue(runCatching { ShortFlowPrompt.build(scene().copy(action="สมชาย เดินไปหา มานี"),"Slow pan") }.isFailure)
    }
    @Test fun preservesExactDialogueAndSeparatesSoundForNonhumanSpeaker() {
        val animal=FlowPerson("หมาป่า", "gray wolf", "center", "", characterType="animal")
        val original="ฉันจะไม่ยอมแพ้... แม้ต้องสู้กับ A & B!"
        val spec=scene().copy(cast=listOf(animal),speaker="หมาป่า",dialogue=original,action="หมาป่า looks forward",sound="Wind in the trees")
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(ShortFlowPrompt.build(spec,"Locked camera"))))
        assertEquals("Animal 1",doc.getElementsByTagName("speaker").item(0).textContent)
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains("anatomically appropriate"))
        assertEquals(original,doc.getElementsByTagName("text").item(0).textContent)
        assertEquals(1,doc.getElementsByTagName("sound_effects").length)
        assertTrue(doc.getElementsByTagName("ambient").item(0).textContent.contains("Wind in the trees"))
        assertTrue(doc.getElementsByTagName("mix").item(0).textContent.contains("below dialogue"))
        val planned=ShortFlowPrompt.build(spec.copy(sound="Ambience: quiet room; SFX: paper rustle; Music: none."),"Locked")
        val plannedDoc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(planned)))
        assertEquals("paper rustle",plannedDoc.getElementsByTagName("sfx").item(0).textContent)
        assertEquals("none",plannedDoc.getElementsByTagName("music").item(0).textContent)
        assertEquals("Deity 1", ShortFlowPrompt.labels(spec.copy(cast=listOf(animal.copy(characterType="deity")))).first())
    }
    @Test fun offscreenSpeechDoesNotMoveVisibleMouths() {
        val spec=scene().copy(speaker="ผู้บรรยาย",offscreen=listOf("ผู้บรรยาย"))
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(ShortFlowPrompt.build(spec,"Locked camera"))))
        assertEquals("true",doc.getElementsByTagName("dialogue").item(0).attributes.getNamedItem("off_screen").nodeValue)
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains("visible characters stay silent"))
    }
    @Test fun effectsOnlyFollowRelevantSceneAndSilentHasNoSpeech() {
        assertEquals("",ShortFlowPrompt.motion("a quiet room"))
        assertTrue(ShortFlowPrompt.motion("smoke and wind").contains("smoke drifts"))
        assertFalse(ShortFlowPrompt.build(scene(),"Locked",true).contains("<dialogue>"))
    }
}
