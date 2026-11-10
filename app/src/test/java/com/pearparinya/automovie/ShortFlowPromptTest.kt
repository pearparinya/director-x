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
    @Test fun rejectsUntranslatedActionInsteadOfSilentlyDroppingIt() {
        assertTrue(runCatching { ShortFlowPrompt.build(scene().copy(action="สมชาย เดินไปหา มานี"),"Slow pan") }.isFailure)
    }
    @Test fun effectsOnlyFollowRelevantSceneAndSilentHasNoSpeech() {
        assertEquals("",ShortFlowPrompt.motion("a quiet room"))
        assertTrue(ShortFlowPrompt.motion("smoke and wind").contains("smoke drifts"))
        assertFalse(ShortFlowPrompt.build(scene(),"Locked",true).contains("<dialogue>"))
    }
}
