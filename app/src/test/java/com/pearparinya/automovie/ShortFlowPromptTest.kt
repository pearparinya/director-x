package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class ShortFlowPromptTest {
    private fun scene()=FlowSceneSpec(listOf(FlowPerson("สมชาย","adult","left","shirt"),FlowPerson("มานี","adult","right","dress")),"สมชาย","หยุดก่อน... ฟังฉัน", "", "สมชาย walks toward มานี in drifting fog", "street", "fog", "standing",8,"9:16")
    @Test fun englishDirectionsKeepOriginalThaiSpeechAndMotion() {
        val output=ShortFlowPrompt.build(scene(),"Slow pan")
        val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(output)))
        assertFalse(Regex("[\\u0E00-\\u0E7F]").containsMatchIn(doc.getElementsByTagName("prompt").item(0).textContent))
        assertEquals("หยุดก่อน... ฟังฉัน",doc.getElementsByTagName("text").item(0).textContent)
        assertTrue(output.contains("Character 1 walks toward Character 2"))
        assertTrue(output.contains("fog moves gently"))
        assertEquals("8",doc.getElementsByTagName("duration").item(0).textContent)
        assertEquals("Character 1: หยุดก่อน... ฟังฉัน",ShortFlowPrompt.voiceOnly(scene()))
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
        assertFalse(output.contains("green trousers"))
        assertTrue(runCatching { ShortFlowPrompt.build(sample.copy(cast=cast.toMutableList().apply { this[0]=this[0].copy(outfit="Top: เสื้อสีครีม") }),"Slow pan") }.isFailure)
    }
    @Test fun rejectsUntranslatedActionInsteadOfSilentlyDroppingIt() {
        assertTrue(runCatching { ShortFlowPrompt.build(scene().copy(action="สมชาย เดินไปหา มานี"),"Slow pan") }.isFailure)
    }
    @Test fun effectsOnlyFollowRelevantSceneAndSilentHasNoSpeech() {
        assertEquals("",ShortFlowPrompt.motion("a quiet room"))
        assertTrue(ShortFlowPrompt.motion("smoke and wind").contains("smoke drifts"))
        assertFalse(ShortFlowPrompt.build(scene(),"Locked",true).contains("<dialogue>"))
    }
}
