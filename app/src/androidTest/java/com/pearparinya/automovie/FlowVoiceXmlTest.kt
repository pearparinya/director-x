package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlowVoiceXmlTest {
    private fun spec(dialogue: String="", narration: String="", speaker: String="", offscreen: List<String> = emptyList()) =
        FlowSceneSpec(listOf(FlowPerson("ภูวดล","adult man","center","shirt")),speaker,dialogue,narration,
            "ภูวดล checks A & B","office","standing","reading",8,"9:16","",offscreen)
    private fun xml(text: String)=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(text)))
    @Test fun thaiSpeechGuidanceIsAddedWithoutChangingTheLineOrSilentScenes() {
        val line="ผลงานของคุณน่าสนใจมากค่ะ"
        val visible=xml(FlowVoiceXml.build(spec(dialogue=line,speaker="ภูวดล"),"Slow pan."))
        val prompt=visible.getElementsByTagName("prompt").item(0).textContent
        assertTrue(prompt.contains("Only [ภูวดล] at center speaks"))
        assertTrue(prompt.contains("clear, natural Central Thai"))
        assertTrue(prompt.contains("Begin directly with the first written word"))
        assertTrue(prompt.contains("no throat clearing, humming, fillers"))
        assertEquals(line,visible.getElementsByTagName("text").item(0).textContent)
        val narr=xml(FlowVoiceXml.build(spec(narration=line),"Slow pan."))
        assertTrue(narr.getElementsByTagName("prompt").item(0).textContent.contains("standard Thai pronunciation and tones"))
        assertTrue(narr.getElementsByTagName("prompt").item(0).textContent.contains("Begin directly with the first written word"))
        assertEquals(line,narr.getElementsByTagName("voice").item(0).textContent)
        val silent=xml(FlowVoiceXml.build(spec(),"Slow pan."))
        assertFalse(silent.getElementsByTagName("prompt").item(0).textContent.contains("Central Thai"))
        val primary=FlowScenePrompt.build(spec(dialogue=line,speaker="ภูวดล"))
        assertTrue(primary.contains("clear, natural Central Thai"))
        assertTrue(primary.contains("no throat clearing, humming, fillers"))
        assertTrue(primary.contains(line))
        assertFalse(FlowScenePrompt.build(spec()).contains("Central Thai"))
    }
    @Test fun narrationUsesOffscreenAndEscapesText() {
        val doc=xml(FlowVoiceXml.build(spec(narration="ตัวเลข A & B < C"),"Slow pan."))
        val d=doc.getElementsByTagName("dialogue").item(0) as org.w3c.dom.Element
        assertEquals("true",d.getAttribute("off_screen"))
        assertEquals("ตัวเลข A & B < C",doc.getElementsByTagName("voice").item(0).textContent)
        assertTrue(doc.getElementsByTagName("prompt").item(0).textContent.contains("A & B"))
    }
    @Test fun visibleAndOffscreenDialogueKeepSpeakerAndLines() {
        val visible=xml(FlowVoiceXml.build(spec(dialogue="ตรวจสอบด้วยครับ",speaker="ภูวดล"),"Slow pan."))
        assertEquals("[ภูวดล]",visible.getElementsByTagName("speaker").item(0).textContent)
        assertEquals("ตรวจสอบด้วยครับ",visible.getElementsByTagName("text").item(0).textContent)
        val silent=xml(FlowVoiceXml.build(spec(),"Locked camera."))
        assertEquals(0,silent.getElementsByTagName("dialogue").length)
    }
}
